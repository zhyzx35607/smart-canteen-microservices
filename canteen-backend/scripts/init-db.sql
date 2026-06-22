-- 创建数据�?
CREATE DATABASE IF NOT EXISTS `canteen_user` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `canteen_menu` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `canteen_order` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ========== canteen_user ==========
USE `canteen_user`;

CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `phone` VARCHAR(20) NOT NULL COMMENT '手机�?,
    `student_no` VARCHAR(50) DEFAULT NULL COMMENT '学工�?,
    `password_hash` VARCHAR(255) NOT NULL COMMENT '密码(BCrypt)',
    `nickname` VARCHAR(100) DEFAULT '' COMMENT '昵称',
    `role` VARCHAR(32) DEFAULT 'user' COMMENT '???: user/merchant/admin',
    `avatar` VARCHAR(500) DEFAULT '' COMMENT '头像URL',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状�?1-正常 0-禁用',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted_at` DATETIME DEFAULT NULL COMMENT '软删除时�?,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_phone` (`phone`),
    UNIQUE KEY `uk_student_no` (`student_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户�?;

CREATE TABLE IF NOT EXISTS `refresh_token` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `token_jti` VARCHAR(64) NOT NULL COMMENT 'JWT jti',
    `expires_at` DATETIME NOT NULL COMMENT '过期时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_jti` (`token_jti`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='刷新令牌�?;

-- ========== canteen_menu ==========
USE `canteen_menu`;

CREATE TABLE IF NOT EXISTS `merchant` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(100) NOT NULL COMMENT '商户�?,
    `counter_id` VARCHAR(50) NOT NULL COMMENT '窗口编号',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted_at` DATETIME DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_counter_id` (`counter_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商户�?;

CREATE TABLE IF NOT EXISTS `dish` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `merchant_id` BIGINT NOT NULL COMMENT '商户ID',
    `name` VARCHAR(100) NOT NULL COMMENT '菜品�?,
    `price_cents` INT NOT NULL COMMENT '价格(�?',
    `image_url` VARCHAR(500) DEFAULT '' COMMENT '图片URL',
    `on_shelf` TINYINT NOT NULL DEFAULT 1 COMMENT '上架状�?1-上架 0-下架',
    `threshold` INT NOT NULL DEFAULT 5 COMMENT '库存预警阈�?,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted_at` DATETIME DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_merchant_id` (`merchant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜品�?;

CREATE TABLE IF NOT EXISTS `daily_menu` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `merchant_id` BIGINT NOT NULL COMMENT '商户ID',
    `biz_date` DATE NOT NULL COMMENT '营业日期',
    `sell_start` TIME NOT NULL COMMENT '开售时�?,
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
    `status` VARCHAR(30) NOT NULL DEFAULT 'PLACED' COMMENT '订单状�?,
    `total_cents` INT NOT NULL COMMENT '总金�?�?',
    `pickup_code` VARCHAR(20) DEFAULT NULL COMMENT '取餐�?,
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单�?;

CREATE TABLE IF NOT EXISTS `order_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `order_id` BIGINT NOT NULL COMMENT '订单ID',
    `dish_id` BIGINT NOT NULL COMMENT '菜品ID',
    `dish_name_snapshot` VARCHAR(100) NOT NULL COMMENT '菜品名快�?,
    `unit_price` INT NOT NULL COMMENT '单价(�?',
    `quantity` INT NOT NULL COMMENT '数量',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单明细';

-- ========== 初始化测试数�?==========
USE `canteen_menu`;

INSERT INTO `merchant` (`id`, `name`, `counter_id`) VALUES
(1, '川味窗口', 'C01'),
(2, '粤式窗口', 'C02'),
(3, '面食窗口', 'C03');

INSERT INTO `dish` (`id`, `merchant_id`, `name`, `price_cents`, `on_shelf`, `threshold`) VALUES
(1, 1, '麻婆豆腐', 1200, 1, 5),
(2, 1, '回锅�?, 1800, 1, 5),
(3, 1, '水煮�?, 2500, 1, 3),
(4, 2, '白切�?, 2200, 1, 5),
(5, 2, '煲仔�?, 1500, 1, 5),
(6, 3, '牛肉�?, 1600, 1, 5),
(7, 3, '炸酱�?, 1200, 1, 5);

USE `canteen_user`;
-- 密码: password123 (BCrypt)
INSERT INTO `user` (`id`, `phone`, `student_no`, `password_hash`, `nickname`, `role`, `status`) VALUES
(1, '13800000001', '2024001', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '测试用户', 'user', 1);
