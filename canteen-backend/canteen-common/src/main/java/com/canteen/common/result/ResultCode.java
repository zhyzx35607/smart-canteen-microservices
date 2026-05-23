package com.canteen.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResultCode {

    // ========== 通用 ==========
    SUCCESS(0, "操作成功"),
    UNKNOWN_ERROR(90000, "系统内部错误"),

    // ========== 用户域 10xxx ==========
    USER_ALREADY_EXISTS(10001, "账号已存在"),
    USER_PASSWORD_ERROR(10002, "密码错误"),
    USER_TOKEN_INVALID(10003, "Token 无效或已过期"),
    USER_TOKEN_EXPIRED(10004, "Token 已过期"),
    USER_NOT_FOUND(10005, "用户不存在"),
    USER_PHONE_EXISTS(10006, "手机号已注册"),
    USER_VERIFY_CODE_ERROR(10007, "验证码错误"),
    USER_DISABLED(10008, "账号已被禁用"),

    // ========== 菜品域 20xxx ==========
    MENU_STOCK_INSUFFICIENT(20001, "库存不足"),
    MENU_DISH_OFF_SHELF(20002, "菜品已下架"),
    MENU_DISH_NOT_FOUND(20003, "菜品不存在"),
    MENU_ALREADY_PUBLISHED(20004, "今日菜单已发布"),
    MENU_MERCHANT_NOT_FOUND(20005, "商户不存在"),

    // ========== 订单域 30xxx ==========
    ORDER_STATUS_ILLEGAL(30001, "订单状态非法"),
    ORDER_EXPIRED(30002, "订单已过期"),
    ORDER_NOT_FOUND(30003, "订单不存在"),
    ORDER_CANNOT_CANCEL(30004, "订单当前状态不可取消"),
    ORDER_IDEMPOTENT_DUPLICATE(30005, "重复请求"),

    // ========== 取餐域 40xxx ==========
    PICKUP_CODE_NOT_FOUND(40001, "取餐码不存在"),
    PICKUP_ALREADY_VERIFIED(40002, "重复核销"),
    PICKUP_QUEUE_EMPTY(40003, "队列为空"),

    // ========== 网关/系统 50xxx ==========
    GATEWAY_RATE_LIMITED(50001, "请求过于频繁，请稍后再试"),
    GATEWAY_AUTH_FAILED(50002, "鉴权失败"),
    GATEWAY_SERVICE_UNAVAILABLE(50003, "服务暂不可用");

    private final int code;
    private final String message;
}
