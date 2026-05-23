-- 创建数据库
CREATE DATABASE IF NOT EXISTS `canteen_user` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `canteen_menu` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `canteen_order` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ========== canteen_user ==========
USE `canteen_user`;

CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `phone` VARCHAR(20) NOT NULL COMMENT '手机号',
    `student_no` VARCHAR(50) DEFAULT NULL COMMENT '学工号',
    `password_hash` VARCHAR(255) NOT NULL COMMENT '密码(BCrypt)',
    `nickname` VARCHAR(100) DEFAULT '' COMMENT '昵称',
    `avatar` VARCHAR(500) DEFAULT '' COMMENT '头像URL',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1-正常 0-禁用',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted_at` DATETIME DEFAULT NULL COMMENT '软删除时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_phone` (`phone`),
    UNIQUE KEY `uk_student_no` (`student_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

CREATE TABLE IF NOT EXISTS `refresh_token` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `token_jti` VARCHAR(64) NOT NULL COMMENT 'JWT jti',
    `expires_at` DATETIME NOT NULL COMMENT '过期时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_jti` (`token_jti`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='刷新令牌表';

-- ========== canteen_menu ==========
USE `canteen_menu`;

CREATE TABLE IF NOT EXISTS `merchant` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(100) NOT NULL COMMENT '商户名',
    `counter_id` VARCHAR(50) NOT NULL COMMENT '窗口编号',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted_at` DATETIME DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_counter_id` (`counter_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商户表';

CREATE TABLE IF NOT EXISTS `dish` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `merchant_id` BIGINT NOT NULL COMMENT '商户ID',
    `name` VARCHAR(100) NOT NULL COMMENT '菜品名',
    `price_cents` INT NOT NULL COMMENT '价格(分)',
    `image_url` VARCHAR(500) DEFAULT '' COMMENT '图片URL',
    `on_shelf` TINYINT NOT NULL DEFAULT 1 COMMENT '上架状态 1-上架 0-下架',
    `threshold` INT NOT NULL DEFAULT 5 COMMENT '库存预警阈值',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted_at` DATETIME DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_merchant_id` (`merchant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜品表';

CREATE TABLE IF NOT EXISTS `daily_menu` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `merchant_id` BIGINT NOT NULL COMMENT '商户ID',
    `biz_date` DATE NOT NULL COMMENT '营业日期',
    `sell_start` TIME NOT NULL COMMENT '开售时间',
    `sell_end` TIME NOT NULL COMMENT '停售时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted_at` DATETIME DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_merchant_date` (`merchant_id`, `biz_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='每日菜单';

CREATE TABLE IF NOT EXISTS `daily_menu_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `daily_menu_id` BIGINT NOT NULL COMMENT '每日菜单ID',
    `dish_id` BIGINT NOT NULL COMMENT '菜品ID',
    `stock_init` INT NOT NULL DEFAULT 0 COMMENT '初始库存',
    `stock_left` INT NOT NULL DEFAULT 0 COMMENT '剩余库存',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_daily_menu_id` (`daily_menu_id`),
    KEY `idx_dish_id` (`dish_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='每日菜单条目';

-- ========== canteen_order ==========
USE `canteen_order`;

CREATE TABLE IF NOT EXISTS `orders` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `merchant_id` BIGINT NOT NULL COMMENT '商户ID',
    `counter_id` VARCHAR(50) NOT NULL COMMENT '窗口编号',
    `status` VARCHAR(30) NOT NULL DEFAULT 'PLACED' COMMENT '订单状态',
    `total_cents` INT NOT NULL COMMENT '总金额(分)',
    `pickup_code` VARCHAR(20) DEFAULT NULL COMMENT '取餐码',
    `placed_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
    `accepted_at` DATETIME DEFAULT NULL COMMENT '接单时间',
    `ready_at` DATETIME DEFAULT NULL COMMENT '制作完成时间',
    `picked_at` DATETIME DEFAULT NULL COMMENT '取餐时间',
    `canceled_at` DATETIME DEFAULT NULL COMMENT '取消时间',
    `cancel_reason` VARCHAR(200) DEFAULT NULL COMMENT '取消原因',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted_at` DATETIME DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_pickup_code` (`pickup_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';

CREATE TABLE IF NOT EXISTS `order_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `order_id` BIGINT NOT NULL COMMENT '订单ID',
    `dish_id` BIGINT NOT NULL COMMENT '菜品ID',
    `dish_name_snapshot` VARCHAR(100) NOT NULL COMMENT '菜品名快照',
    `unit_price` INT NOT NULL COMMENT '单价(分)',
    `quantity` INT NOT NULL COMMENT '数量',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单明细';

-- ========== 初始化测试数据 ==========
USE `canteen_menu`;

INSERT INTO `merchant` (`id`, `name`, `counter_id`) VALUES
(1, '川味窗口', 'C01'),
(2, '粤式窗口', 'C02'),
(3, '面食窗口', 'C03');

INSERT INTO `dish` (`id`, `merchant_id`, `name`, `price_cents`, `on_shelf`, `threshold`) VALUES
(1, 1, '麻婆豆腐', 1200, 1, 5),
(2, 1, '回锅肉', 1800, 1, 5),
(3, 1, '水煮鱼', 2500, 1, 3),
(4, 2, '白切鸡', 2200, 1, 5),
(5, 2, '煲仔饭', 1500, 1, 5),
(6, 3, '牛肉面', 1600, 1, 5),
(7, 3, '炸酱面', 1200, 1, 5);

USE `canteen_user`;
-- 密码: password123 (BCrypt)
INSERT INTO `user` (`id`, `phone`, `student_no`, `password_hash`, `nickname`, `status`) VALUES
(1, '13800000001', '2024001', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '测试用户', 1);
