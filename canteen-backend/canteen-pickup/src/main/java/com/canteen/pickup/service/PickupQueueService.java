package com.canteen.pickup.service;

import com.canteen.common.exception.BusinessException;
import com.canteen.common.result.ResultCode;
import com.canteen.pickup.dto.*;
import com.canteen.pickup.feign.OrderServiceClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PickupQueueService {

    private final StringRedisTemplate stringRedisTemplate;
    private final OrderServiceClient orderServiceClient;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    private static final String QUEUE_PREFIX = "queue:";
    private static final String CURRENT_PREFIX = "queue:current:";
    private static final String HISTORY_PREFIX = "queue:history:";
    private static final String CODE_PREFIX = "code:";
    private static final String CODE_COUNTER_PREFIX = "pickup:counter:"; // pickupCode -> counterId 反向索引

    /**
     * 入队
     */
    public void enqueue(PickupEnqueueRequest req) {
        String queueKey = QUEUE_PREFIX + req.getCounterId();
        String codeKey = CODE_PREFIX + req.getCounterId() + ":" + req.getPickupCode();

        QueueEntry entry = new QueueEntry(req.getOrderId(), req.getPickupCode(), req.getUserId());

        try {
            String json = objectMapper.writeValueAsString(entry);
            stringRedisTemplate.opsForList().rightPush(queueKey, json);
            // 反向索引: pickupCode -> orderId
            stringRedisTemplate.opsForValue().set(codeKey, String.valueOf(req.getOrderId()), 24, TimeUnit.HOURS);
            // 反向索引: pickupCode -> counterId（核销时按取餐码定位窗口）
            stringRedisTemplate.opsForValue()
                    .set(CODE_COUNTER_PREFIX + req.getPickupCode(), req.getCounterId(), 24, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化队列条目失败", e);
        }

        log.info("Enqueued: counterId={}, orderId={}, pickupCode={}", req.getCounterId(), req.getOrderId(), req.getPickupCode());
    }

    /**
     * 叫号
     */
    public QueueEntry call(String counterId) {
        String queueKey = QUEUE_PREFIX + counterId;
        String currentKey = CURRENT_PREFIX + counterId;
        String historyKey = HISTORY_PREFIX + counterId;

        // 从队列头部取一个
        String json = stringRedisTemplate.opsForList().leftPop(queueKey);
        if (json == null) {
            throw new BusinessException(ResultCode.PICKUP_QUEUE_EMPTY);
        }

        QueueEntry entry;
        try {
            entry = objectMapper.readValue(json, QueueEntry.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("反序列化队列条目失败", e);
        }

        // 设置当前叫号
        try {
            stringRedisTemplate.opsForValue().set(currentKey, objectMapper.writeValueAsString(entry), 24, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        // 加入历史
        stringRedisTemplate.opsForList().rightPush(historyKey, json);
        // 截断历史保留50条
        stringRedisTemplate.opsForList().trim(historyKey, -50, -1);

        // 通过 WebSocket 广播叫号事件
        CallEvent event = new CallEvent(counterId, entry, "CALL");
        broadcastEvent(counterId, event);

        log.info("Called: counterId={}, orderId={}, pickupCode={}", counterId, entry.getOrderId(), entry.getPickupCode());
        return entry;
    }

    /**
     * 核销
     */
    public void verify(VerifyRequest req) {
        String counterId = req.getCounterId();
        if (counterId == null || counterId.isBlank()) {
            // 遍历所有可能的 counter 查找
            counterId = findCounterByCode(req.getPickupCode());
        }
        if (counterId == null) {
            throw new BusinessException(ResultCode.PICKUP_CODE_NOT_FOUND);
        }

        String codeKey = CODE_PREFIX + counterId + ":" + req.getPickupCode();
        String orderIdStr = stringRedisTemplate.opsForValue().get(codeKey);

        if (orderIdStr == null) {
            throw new BusinessException(ResultCode.PICKUP_CODE_NOT_FOUND);
        }

        // 检查是否已核销（current 中是否匹配）
        String currentKey = CURRENT_PREFIX + counterId;
        String currentJson = stringRedisTemplate.opsForValue().get(currentKey);

        // 调用订单服务更新状态（InternalTokenInterceptor 自动注入 token）
        orderServiceClient.markPickedUp(Long.valueOf(orderIdStr));

        // 清理
        stringRedisTemplate.delete(codeKey);
        stringRedisTemplate.delete(CODE_COUNTER_PREFIX + req.getPickupCode());
        stringRedisTemplate.delete(currentKey);

        // 广播核销事件
        CallEvent event = new CallEvent(counterId, null, "VERIFY");
        broadcastEvent(counterId, event);

        log.info("Verified: counterId={}, pickupCode={}, orderId={}", counterId, req.getPickupCode(), orderIdStr);
    }

    /**
     * 获取大屏数据
     */
    public QueueScreenVO getScreenData(String counterId) {
        QueueScreenVO vo = new QueueScreenVO();
        vo.setCounterId(counterId);

        // 当前叫号
        String currentKey = CURRENT_PREFIX + counterId;
        String currentJson = stringRedisTemplate.opsForValue().get(currentKey);
        if (currentJson != null) {
            try {
                vo.setCurrentCalling(objectMapper.readValue(currentJson, QueueEntry.class));
            } catch (JsonProcessingException e) {
                log.error("解析当前叫号数据失败", e);
            }
        }

        // 等待队列
        String queueKey = QUEUE_PREFIX + counterId;
        List<String> queueJsonList = stringRedisTemplate.opsForList().range(queueKey, 0, -1);
        vo.setWaitingList(parseEntries(queueJsonList));

        // 历史记录
        String historyKey = HISTORY_PREFIX + counterId;
        List<String> historyJsonList = stringRedisTemplate.opsForList().range(historyKey, -10, -1);
        vo.setHistoryList(parseEntries(historyJsonList));

        return vo;
    }

    private List<QueueEntry> parseEntries(List<String> jsonList) {
        if (jsonList == null) return new ArrayList<>();
        List<QueueEntry> entries = new ArrayList<>();
        for (String json : jsonList) {
            try {
                entries.add(objectMapper.readValue(json, QueueEntry.class));
            } catch (JsonProcessingException e) {
                log.error("解析队列条目失败: {}", json, e);
            }
        }
        return entries;
    }

    private String findCounterByCode(String pickupCode) {
        return stringRedisTemplate.opsForValue().get(CODE_COUNTER_PREFIX + pickupCode);
    }

    private void broadcastEvent(String counterId, CallEvent event) {
        try {
            messagingTemplate.convertAndSend("/topic/screen/" + counterId, event);
        } catch (Exception e) {
            log.error("WebSocket broadcast failed: counterId={}", counterId, e);
        }
    }
}
