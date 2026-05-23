package com.canteen.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "手机号不能为空")
    private String phone;

    private String password;

    private String verifyCode;

    /** 登录方式: password / sms */
    @NotBlank(message = "登录方式不能为空")
    private String loginType;
}
