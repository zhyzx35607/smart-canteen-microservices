-- Add role column to user table
ALTER TABLE user ADD COLUMN IF NOT EXISTS role VARCHAR(32) DEFAULT 'user' COMMENT '??бз??бдиибь'иибы2: user/merchant/admin' AFTER nickname;
