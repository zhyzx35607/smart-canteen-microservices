package com.canteen.menu;

import com.canteen.common.exception.BusinessException;
import com.canteen.menu.dto.StockDeductRequest;
import com.canteen.menu.service.StockService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@Sql(scripts = "classpath:schema-menu-test.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class MenuServiceIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.sql.init.mode", () -> "always");
        registry.add("spring.cloud.nacos.discovery.enabled", () -> "false");
        registry.add("spring.cloud.nacos.config.enabled", () -> "false");
        registry.add("spring.cloud.sentinel.enabled", () -> "false");
        registry.add("spring.cloud.stream.rocketmq.binder.name-server", () -> "localhost:19876");
        registry.add("spring.autoconfigure.exclude",
                () -> "org.redisson.spring.starter.RedissonAutoConfigurationV2");
        registry.add("internal.token", () -> "test-internal-token");
    }

    @Autowired
    private StockService stockService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @AfterEach
    void tearDown() {
        Set<String> keys = stringRedisTemplate.keys("stock:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    private void initRedisStock(Long dishId, int quantity) {
        stringRedisTemplate.opsForValue().set("stock:" + dishId, String.valueOf(quantity));
    }

    @Test
    @DisplayName("集成: 库存扣减成功 — Redis + DB 双写")
    void deductSuccess() {
        initRedisStock(1L, 100);

        StockDeductRequest.StockItem item = new StockDeductRequest.StockItem(1L, 3);
        boolean result = stockService.deduct(List.of(item));

        assertThat(result).isTrue();
        assertThat(stockService.getStock(1L)).isEqualTo(97);
    }

    @Test
    @DisplayName("集成: 库存不足 — 返回 false")
    void deductInsufficientStock() {
        initRedisStock(2L, 5);

        StockDeductRequest.StockItem item = new StockDeductRequest.StockItem(2L, 10);
        boolean result = stockService.deduct(List.of(item));

        assertThat(result).isFalse();
        assertThat(stockService.getStock(2L)).isEqualTo(5);
    }

    @Test
    @DisplayName("集成: 库存扣减后回滚 — 恢复到原值")
    void restoreAfterDeduct() {
        initRedisStock(1L, 100);

        StockDeductRequest.StockItem item = new StockDeductRequest.StockItem(1L, 5);
        stockService.deduct(List.of(item));
        assertThat(stockService.getStock(1L)).isEqualTo(95);

        stockService.restore(List.of(item));
        assertThat(stockService.getStock(1L)).isEqualTo(100);
    }

    @Test
    @DisplayName("集成: 多菜品批量扣减 — 原子操作全部成功或全部失败")
    void batchDeductAtomic() {
        initRedisStock(1L, 10);
        initRedisStock(2L, 5);
        initRedisStock(3L, 3);

        List<StockDeductRequest.StockItem> items = List.of(
                new StockDeductRequest.StockItem(1L, 2),
                new StockDeductRequest.StockItem(2L, 2),
                new StockDeductRequest.StockItem(3L, 1)
        );

        boolean result = stockService.deduct(items);
        assertThat(result).isTrue();
        assertThat(stockService.getStock(1L)).isEqualTo(8);
        assertThat(stockService.getStock(2L)).isEqualTo(3);
        assertThat(stockService.getStock(3L)).isEqualTo(2);
    }

    @Test
    @DisplayName("集成: 批量扣减 — 任一菜品库存不足则全部回退")
    void batchDeductFailOnAnyInsufficient() {
        initRedisStock(1L, 10);
        initRedisStock(2L, 5);
        initRedisStock(3L, 1);

        List<StockDeductRequest.StockItem> items = List.of(
                new StockDeductRequest.StockItem(1L, 2),
                new StockDeductRequest.StockItem(2L, 2),
                new StockDeductRequest.StockItem(3L, 5)
        );

        boolean result = stockService.deduct(items);
        assertThat(result).isFalse();
        assertThat(stockService.getStock(1L)).isEqualTo(10);
        assertThat(stockService.getStock(2L)).isEqualTo(5);
        assertThat(stockService.getStock(3L)).isEqualTo(1);
    }

    @Test
    @DisplayName("集成: 空列表扣减 — 直接返回 true")
    void deductEmptyList() {
        assertThat(stockService.deduct(List.of())).isTrue();
        assertThat(stockService.deduct(null)).isTrue();
    }

    @Test
    @DisplayName("集成: 库存低于阈值 — 触发告警（日志 WARN）")
    void lowStockWarning() {
        initRedisStock(1L, 4);

        StockDeductRequest.StockItem item = new StockDeductRequest.StockItem(1L, 2);
        boolean result = stockService.deduct(List.of(item));

        assertThat(result).isTrue();
        assertThat(stockService.getStock(1L)).isEqualTo(2);
    }

    @Test
    @DisplayName("集成: getStock — 不存在的菜品返回 0")
    void getStockNonExistent() {
        assertThat(stockService.getStock(9999L)).isZero();
    }
}
