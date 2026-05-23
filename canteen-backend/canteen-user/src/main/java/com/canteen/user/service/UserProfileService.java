package com.canteen.user.service;

import cn.hutool.core.bean.BeanUtil;
import com.canteen.common.exception.BusinessException;
import com.canteen.common.result.ResultCode;
import com.canteen.user.dto.UserUpdateRequest;
import com.canteen.user.dto.UserVO;
import com.canteen.user.entity.User;
import com.canteen.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserMapper userMapper;

    public UserVO getMe(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return toVO(user);
    }

    public UserVO updateMe(Long userId, UserUpdateRequest req) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        if (req.getNickname() != null) {
            user.setNickname(req.getNickname());
        }
        if (req.getAvatar() != null) {
            user.setAvatar(req.getAvatar());
        }

        userMapper.updateById(user);
        log.info("User profile updated: id={}", userId);
        return toVO(user);
    }

    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        BeanUtil.copyProperties(user, vo);
        return vo;
    }
}
