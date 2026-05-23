package com.canteen.common.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 显式配置。
 * <p>
 * 解决 redisson-spring-boot-starter 自动配置时，即使 spring.data.redis.password 未设置
 * 或为空字符串，Redisson 仍会向无密码的 Redis 发送 AUTH "" 命令导致连接失败的问题。
 * <p>
 * 当环境变量 REDIS_PASSWORD 未设置时（本地 Docker 开发环境），完全不传 password 参数，
 * 让 Redisson 跳过 AUTH 握手。
 */
@Configuration
@ConditionalOnClass(RedissonClient.class)
public class RedissonConfig {

    /**
     * 仅当未手动配置 redisson.* 属性且未显式定义 RedissonClient bean 时生效。
     * 通过读取 spring.data.redis 的 host/port 构建单节点 Config。
     */
    @Bean
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient redisson(org.springframework.core.env.Environment env) {
        String host = env.getProperty("spring.data.redis.host", "localhost");
        int port = env.getProperty("spring.data.redis.port", Integer.class, 6379);
        String password = env.getProperty("spring.data.redis.password");

        String address = "redis://" + host + ":" + port;

        Config config = new Config();
        config.useSingleServer()
                .setAddress(address);

        // 仅当密码非空时才设置，彻底避免空串 AUTH 问题
        if (password != null && !password.isBlank()) {
            config.useSingleServer().setPassword(password);
        }

        return Redisson.create(config);
    }
}
