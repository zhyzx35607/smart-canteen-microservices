package com.canteen.menu.controller;

import com.canteen.common.exception.BusinessException;
import com.canteen.common.result.Result;
import com.canteen.common.result.ResultCode;
import com.canteen.menu.entity.Merchant;
import com.canteen.menu.mapper.MerchantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/merchants")
@RequiredArgsConstructor
public class InternalMerchantController {

    private final MerchantMapper merchantMapper;

    @Value("${internal.token}")
    private String internalToken;

    @GetMapping("/{id}")
    public Result<MerchantInfo> getMerchant(@RequestHeader("X-Internal-Token") String token,
                                            @PathVariable Long id) {
        if (!internalToken.equals(token)) {
            throw new BusinessException(ResultCode.GATEWAY_AUTH_FAILED, "内部接口Token无效");
        }
        Merchant m = merchantMapper.selectById(id);
        if (m == null) {
            throw new BusinessException(ResultCode.MENU_MERCHANT_NOT_FOUND);
        }
        MerchantInfo info = new MerchantInfo();
        info.setId(m.getId());
        info.setName(m.getName());
        info.setCounterId(m.getCounterId());
        return Result.success(info);
    }

    @lombok.Data
    public static class MerchantInfo {
        private Long id;
        private String name;
        private String counterId;
    }
}
