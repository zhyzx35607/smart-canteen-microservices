package com.canteen.user.dto;

import lombok.Data;

@Data
public class UserVO {

    private Long id;
    private String phone;
    private String studentNo;
    private String nickname;
    private String avatar;
    private Integer status;
}
