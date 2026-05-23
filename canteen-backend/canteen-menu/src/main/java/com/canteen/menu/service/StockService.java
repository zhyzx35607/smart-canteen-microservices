package com.canteen.menu.service;

import com.canteen.common.exception.BusinessException;
import com.canteen.common.result.ResultCode;
import com.canteen.menu.dto.StockDeductRequest;
import com.canteen.menu.entity.DailyMenuItem;
import com.canteen.menu.entity.Dish;
import com.canteen.menu.mapper.DailyMenuItemMapper;
import com.canteen.menu.mapper.DishMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final StringRedisTemplate stringRedisTemplate;
    private final DishMapper dishMapper;
    private final DailyMenuItemMapper dailyMenuItemMapper;

    private static final String STOCK_KEY_PREFIX = "stock:";

    /**
     * Lua 脚本：原子扣减库存
     * KEYS[i] = stock:{dishId}
     * ARGV[i] = quantity
     * 返回 1=成功, 0=库存不足
     */
    private static final String DEDUCT_LUA =
            "for i = 1, #KEYS do " +
            "  local left = tonumber(redis.call('GET', KEYS[i])) " +
            "  if not left or left < tonumber(ARGV[i]) then return 0 end " +
            "end " +
            "for i = 1, #KEYS do " +
            "  redis.call('DECRBY', KEYS[i], ARGV[i]) " +
            "end " +
            "return 1";

    /**
     * 原子批量扣减库存：先用 Lua 在 Redis 中原子扣减；成功后异步落库 daily_menu_item.stock_left。
     * Redis 是请求时的事实数据源，DB 作为持久化备份；二者通过 deduct/restore 双写保持一致。
     */
    public boolean deduct(List<StockDeductRequest.StockItem> items) {
        if (items == null || items.isEmpty()) return true;

        List<String> keys = items.stream()
                .map(item -> STOCK_KEY_PREFIX + item.getDishId())
                .toList();
        List<String> args = items.stream()
                .map(item -> String.valueOf(item.getQuantity()))
                .toList();

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(DEDUCT_LUA, Long.class);
        Long result = stringRedisTemplate.execute(script, keys, args.toArray(new String[0]));

        if (result == null || result == 0L) {
            log.warn("Stock deduct failed: insufficient stock for items={}", items);
            return false;
        }

        // 同步落库：保持 Redis 与 daily_menu_item.stock_left 一致
        for (StockDeductRequest.StockItem item : items) {
            int updated = dailyMenuItemMapper.deductStockLeft(item.getDishId(), item.getQuantity());
            if (updated == 0) {
                log.warn("DB stock_left deduct affected 0 rows: dishId={}, quantity={}",
                        item.getDishId(), item.getQuantity());
            }
        }

        // 扣减后检查低库存预警
        for (StockDeductRequest.StockItem item : items) {
            String stockStr = stringRedisTemplate.opsForValue().get(STOCK_KEY_PREFIX + item.getDishId());
            if (stockStr != null) {
                int left;
                try {
                    left = Integer.parseInt(stockStr);
                } catch (NumberFormatException e) {
                    log.warn("Invalid stock value in Redis: dishId={}, value={}", item.getDishId(), stockStr);
                    continue;
                }
                Dish dish = dishMapper.selectById(item.getDishId());
                int threshold = (dish != null && dish.getThreshold() != null) ? dish.getThreshold() : 5;
                if (left < threshold) {
                    log.warn("Low stock alert: dishId={}, dishName={}, left={}, threshold={}",
                            item.getDishId(), dish != null ? dish.getName() : "", left, threshold);
                }
            }
        }

        log.info("Stock deducted successfully: items={}", items);
        return true;
    }

    /**
     * 回滚库存：Redis 与 DB 双写。
     */
    public void restore(List<StockDeductRequest.StockItem> items) {
        if (items == null || items.isEmpty()) return;

        for (StockDeductRequest.StockItem item : items) {
            stringRedisTemplate.opsForValue().increment(STOCK_KEY_PREFIX + item.getDishId(), item.getQuantity());
            dailyMenuItemMapper.addStockLeft(item.getDishId(), item.getQuantity());
        }

        log.info("Stock restored: items={}", items);
    }

    /**
     * 获取当前库存
     */
    public int getStock(Long dishId) {
        String stockStr = stringRedisTemplate.opsForValue().get(STOCK_KEY_PREFIX + dishId);
        if (stockStr == null) return 0;
        try {
            return Integer.parseInt(stockStr);
        } catch (NumberFormatException e) {
            log.warn("Invalid stock value in Redis: dishId={}, value={}", dishId, stockStr);
            return 0;
        }
    }
}
