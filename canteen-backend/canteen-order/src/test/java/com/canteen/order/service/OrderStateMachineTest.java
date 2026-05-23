package com.canteen.order.service;

import com.canteen.common.exception.BusinessException;
import com.canteen.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderStateMachineTest {

    private final OrderStateMachine stateMachine = new OrderStateMachine();

    @Test
    @DisplayName("正常流转: PLACED -> ACCEPTED -> PREPARING -> WAITING_PICKUP -> PICKED_UP")
    void testNormalFlow() {
        assertEquals("ACCEPTED", stateMachine.transition("PLACED", "accept"));
        assertEquals("PREPARING", stateMachine.transition("ACCEPTED", "start"));
        assertEquals("WAITING_PICKUP", stateMachine.transition("PREPARING", "ready"));
        assertEquals("PICKED_UP", stateMachine.transition("WAITING_PICKUP", "pickup"));
    }

    @Test
    @DisplayName("取消: PLACED -> CANCELED")
    void testCancelFromPlaced() {
        assertEquals("CANCELED", stateMachine.transition("PLACED", "cancel"));
    }

    @Test
    @DisplayName("取消: ACCEPTED -> CANCELED")
    void testCancelFromAccepted() {
        assertEquals("CANCELED", stateMachine.transition("ACCEPTED", "cancel"));
    }

    @Test
    @DisplayName("非法: PREPARING 不能取消")
    void testCannotCancelFromPreparing() {
        assertThrows(BusinessException.class, () ->
                stateMachine.transition("PREPARING", "cancel"));
    }

    @Test
    @DisplayName("非法: WAITING_PICKUP 不能取消")
    void testCannotCancelFromWaitingPickup() {
        assertThrows(BusinessException.class, () ->
                stateMachine.transition("WAITING_PICKUP", "cancel"));
    }

    @Test
    @DisplayName("非法: PICKED_UP 状态不能任何操作")
    void testCannotTransitionFromPickedUp() {
        assertThrows(BusinessException.class, () ->
                stateMachine.transition("PICKED_UP", "accept"));
        assertThrows(BusinessException.class, () ->
                stateMachine.transition("PICKED_UP", "cancel"));
    }

    @Test
    @DisplayName("非法: PLACED 不能 start")
    void testCannotStartFromPlaced() {
        assertThrows(BusinessException.class, () ->
                stateMachine.transition("PLACED", "start"));
    }

    @Test
    @DisplayName("非法: PLACED 不能 ready")
    void testCannotReadyFromPlaced() {
        assertThrows(BusinessException.class, () ->
                stateMachine.transition("PLACED", "ready"));
    }
}
