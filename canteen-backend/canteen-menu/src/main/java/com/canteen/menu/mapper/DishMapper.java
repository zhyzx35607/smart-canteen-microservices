package com.canteen.menu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.canteen.menu.entity.Dish;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DishMapper extends BaseMapper<Dish> {
}
