package com.canteen.menu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.canteen.menu.entity.DailyMenuItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DailyMenuItemMapper extends BaseMapper<DailyMenuItem> {

    @Update("UPDATE daily_menu_item SET stock_left = stock_left + #{quantity} WHERE dish_id = #{dishId}")
    int addStockLeft(@Param("dishId") Long dishId, @Param("quantity") Integer quantity);

    @Update("UPDATE daily_menu_item SET stock_left = stock_left - #{quantity} WHERE dish_id = #{dishId} AND stock_left >= #{quantity}")
    int deductStockLeft(@Param("dishId") Long dishId, @Param("quantity") Integer quantity);
}
