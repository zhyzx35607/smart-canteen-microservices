package com.canteen.order.service;

import com.canteen.common.exception.BusinessException;
import com.canteen.common.result.ResultCode;
import com.canteen.order.entity.Order;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class OrderStateMachine {

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "accept", Set.of(Order.STATUS_PLACED),
            "start", Set.of(Order.STATUS_ACCEPTED),
            "ready", Set.of(Order.STATUS_PREPARING),
            "pickup", Set.of(Order.STATUS_WAITING_PICKUP),
            "cancel", Set.of(Order.STATUS_PLACED, Order.STATUS_ACCEPTED)
    );

    private static final Map<String, String> NEXT_STATUS = Map.of(
            "accept", Order.STATUS_ACCEPTED,
            "start", Order.STATUS_PREPARING,
            "ready", Order.STATUS_WAITING_PICKUP,
            "pickup", Order.STATUS_PICKED_UP,
            "cancel", Order.STATUS_CANCELED
    );

    public String transition(String currentStatus, String event) {
        Set<String> allowedFrom = TRANSITIONS.get(event);
        if (allowedFrom == null || !allowedFrom.contains(currentStatus)) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ILLEGAL,
                    String.format("不允许从 %s 执行 %s 操作", currentStatus, event));
        }
        return NEXT_STATUS.get(event);
    }
}
