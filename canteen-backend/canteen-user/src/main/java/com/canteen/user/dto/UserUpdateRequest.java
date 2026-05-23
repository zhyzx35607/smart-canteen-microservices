package com.canteen.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserUpdateRequest {

    @Size(max = 100, message = "昵称最长100字")
    private String nickname;

    @Size(max = 500, message = "头像URL最长500字")
    private String avatar;
}
