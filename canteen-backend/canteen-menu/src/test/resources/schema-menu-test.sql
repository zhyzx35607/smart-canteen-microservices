CREATE TABLE IF NOT EXISTS dish (
    id BIGINT AUTO_INCREMENT,
    merchant_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    price_cents INT NOT NULL,
    image_url VARCHAR(500) DEFAULT '',
    on_shelf TINYINT NOT NULL DEFAULT 1,
    threshold INT NOT NULL DEFAULT 5,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP DEFAULT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS daily_menu_item (
    id BIGINT AUTO_INCREMENT,
    daily_menu_id BIGINT NOT NULL,
    dish_id BIGINT NOT NULL,
    stock_init INT NOT NULL DEFAULT 0,
    stock_left INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

INSERT INTO dish (id, merchant_id, name, price_cents, on_shelf, threshold) VALUES
(1, 1, '麻婆豆腐', 1200, 1, 5),
(2, 1, '回锅肉', 1800, 1, 5),
(3, 1, '水煮鱼', 2500, 1, 3);

INSERT INTO daily_menu_item (id, daily_menu_id, dish_id, stock_init, stock_left) VALUES
(1, 1, 1, 100, 100),
(2, 1, 2, 50, 50),
(3, 1, 3, 10, 10);
