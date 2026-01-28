# WebIM 高可用客服聊天系统

[![Java](https://img.shields.io/badge/Java-8-blue.svg)](https://www.oracle.com/java/technologies/javase/javase-jdk8-downloads.html)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Netty](https://img.shields.io/badge/Netty-4.1-blueviolet.svg)](https://netty.io/)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

WebIM 是一个基于 Java 8, Spring Boot 2.7, Netty 和 Redis 开发的高性能 1对1 客服聊天系统。它旨在提供毫秒级的实时通讯能力，并支持通过 Redis 实现多实例部署下的高可用客服分配逻辑。

## 🌟 核心特性

- **高性能架构**：后端采用 Netty + WebSocket 协议，确保消息传输的高吞吐与低延迟。
- **高可用调度**：集成 Redis，实现客服在线状态全局管理及“最少连接数”自动分配算法。
- **现代化 UI**：前端采用原生 JavaScript + CSS 打造，应用毛玻璃 (Glassmorphism) 设计风格，视觉体验极佳。
- **富媒体支持**：支持发送文字消息、Emoji 表情，以及图片上传与实时预览。
- **消息持久化**：连接 MySQL 数据库，自动记录并同步完整的历史对话。
- **自动化构建**：支持 Maven 资源过滤，轻松根据不同环境（开发/生产）注入数据库配置。

## 🛠️ 技术栈

- **后端**：Java 8, Spring Boot 2.7, Netty 4.1, Redis, MyBatis, MySQL, Lombok
- **前端**：Vanilla JavaScript, HTML5, CSS3 (针对现代浏览器优化)

## 🚀 快速开始

### 1. 环境准备
确保您的开发环境中已安装：
- JDK 1.8+
- Maven 3.6+
- MySQL 5.7/8.0
- Redis 6.0+

### 2. 数据库初始化
执行项目根目录下 `sql/init.sql` 中的脚本，创建 `webim` 数据库及所需的表结构，并初始化演示客服数据。

### 3. 配置修改
在 `pom.xml` 的 `<properties>` 标签中修改您的环境配置：
```xml
<properties>
    <MYSQL_HOST>您的数据库IP</MYSQL_HOST>
    <MYSQL_PORT>3306</MYSQL_PORT>
    <MYSQL_USER>您的用户名</MYSQL_USER>
    <MYSQL_PASSWORD>您的密码</MYSQL_PASSWORD>
</properties>
```

### 4. 编译安装
使用 Maven 进行资源替换并打包：
```bash
mvn clean package -DskipTests
```

### 5. 运行项目
```bash
java -jar target/webim-server-1.0.0-SNAPSHOT.jar
```
项目默认运行在 `8080` 端口，WebSocket 运行在 `8888` 端口。

## 📖 使用指南

1. **访问地址**：`http://localhost:8080/index.html`
2. **客服端进入**：在 ID 输入框输入 `1`（已在 SQL 中预置），点击 **“客服控制中心”**。
3. **用户端进入**：在另一个窗口输入任意用户 ID（如 `1001`），点击 **“作为用户进入”**。
4. **对话交互**：系统会自动为在线用户分配负载最低的客服。您可以尝试发送文字、点击表情面板或上传图片。

## 📂 目录结构

```text
webIM/
├── pom.xml                 # Maven 依赖与资源过滤配置
├── sql/                    # 数据库初始化脚本
├── src/main/java/          # Java 源码
│   └── com/webim/
│       ├── config/        # Web 与资源映射配置
│       ├── controller/    # 消息记录与文件上传接口
│       ├── entity/        # 数据库实体类
│       ├── mapper/        # MyBatis Mapper
│       ├── netty/         # Netty WebSocket 服务端实现
│       ├── service/       # 基于 Redis 的业务调度逻辑
│       └── WebIMApplication.java # 启动类
└── src/main/resources/
    ├── application.yml     # Spring 核心配置
    └── static/             # 前端静态资源 (HTML/CSS/JS)
```

## ai 生成的项目过程文件
本项目的 walkthrough.md（项目成果展示及运行指南）文件位于以下位置：
file:///Users/fangjilue/.gemini/antigravity/brain/53786300-bb0a-410c-bf7c-86fc9dd997f6/walkthrough.md

项目的 task 任务清单文件位于以下路径：

file:///Users/fangjilue/.gemini/antigravity/brain/53786300-bb0a-410c-bf7c-86fc9dd997f6/task.md

## 📜 许可证

本项目基于 [Apache License 2.0](LICENSE) 协议发布。
