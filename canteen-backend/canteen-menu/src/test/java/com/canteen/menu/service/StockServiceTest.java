package com.canteen.menu.service;

import com.canteen.menu.dto.StockDeductRequest;
import com.canteen.menu.entity.DailyMenuItem;
import com.canteen.menu.entity.Dish;
import com.canteen.menu.mapper.DailyMenuItemMapper;
import com.canteen.menu.mapper.DishMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @InjectMocks
    private StockService stockService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private DishMapper dishMapper;

    @Mock
    private DailyMenuItemMapper dailyMenuItemMapper;

    @Test
    @DisplayName("库存扣减: 空列表直接返回 true")
    void testDeductEmptyList() {
        assertTrue(stockService.deduct(List.of()));
        assertTrue(stockService.deduct(null));
    }

    @Test
    @DisplayName("库存扣减: 成功返回 true")
    void testDeductSuccess() {
        when(stringRedisTemplate.execute(
                any(DefaultRedisScript.class),
                anyList(),
                any(String[].class)
        )).thenReturn(1L);

        Dish dish = new Dish();
        dish.setId(1L);
        dish.setThreshold(5);
        when(dishMapper.selectById(1L)).thenReturn(dish);
        when(stringRedisTemplate.opsForValue()).thenReturn(mock(org.springframework.data.redis.core.ValueOperations.class));
        // Mock getStock 返回足够库存
        when(stringRedisTemplate.opsForValue().get("stock:1")).thenReturn("50");

        StockDeductRequest.StockItem item = new StockDeductRequest.StockItem(1L, 2);
        boolean result = stockService.deduct(List.of(item));

        assertTrue(result);
    }

    @Test
    @DisplayName("库存扣减: 库存不足返回 false")
    void testDeductInsufficientStock() {
        when(stringRedisTemplate.execute(
                any(DefaultRedisScript.class),
                anyList(),
                any(String[].class)
        )).thenReturn(0L);

        StockDeductRequest.StockItem item = new StockDeductRequest.StockItem(1L, 999);
        boolean result = stockService.deduct(List.of(item));

        assertFalse(result);
    }

    @Test
    @DisplayName("库存回滚: 调用 Redis INCRBY + DB 更新")
    void testRestore() {
        var valueOps = mock(org.springframework.data.redis.core.ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);

        StockDeductRequest.StockItem item = new StockDeductRequest.StockItem(1L, 3);
        stockService.restore(List.of(item));

        verify(valueOps).increment("stock:1", 3);
        verify(dailyMenuItemMapper).addStockLeft(1L, 3);
    }

    @Test
    @DisplayName("库存回滚: 空列表不操作")
    void testRestoreEmptyList() {
        stockService.restore(List.of());
        stockService.restore(null);
        verifyNoInteractions(stringRedisTemplate);
        verifyNoInteractions(dailyMenuItemMapper);
    }
}
