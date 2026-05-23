package com.canteen.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.canteen.user.entity.RefreshToken;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RefreshTokenMapper extends BaseMapper<RefreshToken> {
}
