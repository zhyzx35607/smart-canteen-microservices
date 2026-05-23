package com.canteen.menu.service;

import com.canteen.common.exception.BusinessException;
import com.canteen.common.result.ResultCode;
import com.canteen.menu.dto.DishCreateRequest;
import com.canteen.menu.dto.DishVO;
import com.canteen.menu.entity.Dish;
import com.canteen.menu.entity.Merchant;
import com.canteen.menu.mapper.DishMapper;
import com.canteen.menu.mapper.MerchantMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DishServiceTest {

    @InjectMocks
    private DishService dishService;

    @Mock
    private DishMapper dishMapper;

    @Mock
    private MerchantMapper merchantMapper;

    @Test
    @DisplayName("创建菜品成功")
    void testCreateDishSuccess() {
        Merchant merchant = new Merchant();
        merchant.setId(1L);
        merchant.setName("食堂1号");
        when(merchantMapper.selectById(1L)).thenReturn(merchant);
        when(dishMapper.insert(any(Dish.class))).thenReturn(1);

        DishCreateRequest req = new DishCreateRequest();
        req.setMerchantId(1L);
        req.setName("宫保鸡丁");
        req.setPriceCents(1200);
        req.setThreshold(5);

        DishVO vo = dishService.createDish(req);

        assertNotNull(vo);
        verify(dishMapper).insert(any(Dish.class));
    }

    @Test
    @DisplayName("创建菜品失败: 商户不存在")
    void testCreateDishMerchantNotFound() {
        when(merchantMapper.selectById(999L)).thenReturn(null);

        DishCreateRequest req = new DishCreateRequest();
        req.setMerchantId(999L);
        req.setName("宫保鸡丁");
        req.setPriceCents(1200);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> dishService.createDish(req));
        assertEquals(ResultCode.MENU_MERCHANT_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("上下架切换: 上架 -> 下架")
    void testToggleShelfOnToOff() {
        Dish dish = new Dish();
        dish.setId(1L);
        dish.setOnShelf(1);
        when(dishMapper.selectById(1L)).thenReturn(dish);
        when(dishMapper.updateById(any(Dish.class))).thenReturn(1);

        dishService.toggleShelf(1L);

        assertEquals(0, dish.getOnShelf());
        verify(dishMapper).updateById(dish);
    }

    @Test
    @DisplayName("上下架切换: 下架 -> 上架")
    void testToggleShelfOffToOn() {
        Dish dish = new Dish();
        dish.setId(1L);
        dish.setOnShelf(0);
        when(dishMapper.selectById(1L)).thenReturn(dish);
        when(dishMapper.updateById(any(Dish.class))).thenReturn(1);

        dishService.toggleShelf(1L);

        assertEquals(1, dish.getOnShelf());
    }

    @Test
    @DisplayName("查询菜品失败: 菜品不存在")
    void testGetDishNotFound() {
        when(dishMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> dishService.getDish(999L));
        assertEquals(ResultCode.MENU_DISH_NOT_FOUND.getCode(), ex.getCode());
    }
}
