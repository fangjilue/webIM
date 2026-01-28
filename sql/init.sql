-- 创建数据库
CREATE DATABASE IF NOT EXISTS `webim` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE `webim`;

-- 用户表（模拟在线用户）
CREATE TABLE IF NOT EXISTS `im_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `nickname` VARCHAR(64) NOT NULL COMMENT '昵称',
    `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 客服表
CREATE TABLE IF NOT EXISTS `im_agent` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '客服ID',
    `agent_name` VARCHAR(64) NOT NULL COMMENT '客服名',
    `work_status` TINYINT DEFAULT 0 COMMENT '工作状态 0-不在线 1-在线 2-忙碌',
    `max_links` INT DEFAULT 5 COMMENT '最大接待人数',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客服表';

-- 聊天记录表
CREATE TABLE IF NOT EXISTS `im_message` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '消息ID',
    `from_id` BIGINT NOT NULL COMMENT '发送者ID',
    `from_type` TINYINT NOT NULL COMMENT '发送者类型 1-用户 2-客服',
    `to_id` BIGINT NOT NULL COMMENT '接收者ID',
    `content` TEXT COMMENT '内容',
    `msg_type` TINYINT DEFAULT 1 COMMENT '消息类型 1-文字 2-表情 3-图片',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    PRIMARY KEY (`id`),
    INDEX `idx_from_to` (`from_id`, `to_id`),
    INDEX `idx_to_from` (`to_id`, `from_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天记录表';

-- 初始化一些演示数据
INSERT INTO `im_agent` (agent_name, work_status, max_links) VALUES ('在线客服-小美', 1, 10);
INSERT INTO `im_agent` (agent_name, work_status, max_links) VALUES ('在线客服-阿强', 1, 5);
