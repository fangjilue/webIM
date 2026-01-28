package com.webim.netty.handler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.webim.entity.Message;
import com.webim.mapper.ChatMapper;
import com.webim.service.AgentService;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.GlobalEventExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 业务处理器
 * 负责处理客户端连接、身份认证、消息转发及心跳检测
 */
@Slf4j
@Component
@ChannelHandler.Sharable
public class ChatHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    /**
     * 存储在线会话：Key 格式为 "用户类型:用户ID"，Value 为对应的 Netty Channel
     */
    private static final Map<String, Channel> userChannels = new ConcurrentHashMap<>();

    private final AgentService agentService;
    private final ChatMapper chatMapper;

    /**
     * Channel 属性 Key，用于在 Channel 中绑定用户 ID 和类型
     */
    private static final AttributeKey<String> USER_ID_KEY = AttributeKey.valueOf("userId");
    private static final AttributeKey<Integer> USER_TYPE_KEY = AttributeKey.valueOf("userType");

    public ChatHandler(AgentService agentService, ChatMapper chatMapper) {
        this.agentService = agentService;
        this.chatMapper = chatMapper;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        String text = frame.text();
        log.debug("收到消息: {}", text);

        // 解析 JSON 消息
        JSONObject json = JSON.parseObject(text);
        String type = json.getString("type");

        // 根据消息类型分发逻辑
        switch (type) {
            case "AUTH":
                // 身份认证（建立连接后的第一步）
                handleAuth(ctx, json);
                break;
            case "SEND":
                // 发送即时消息（私聊转发）
                handleSend(ctx, json);
                break;
            case "HEARTBEAT":
                // 心跳检测响应
                ctx.writeAndFlush(new TextWebSocketFrame("{\"type\":\"PONG\"}"));
                break;
            default:
                log.warn("未知消息类型: {}", type);
        }
    }

    /**
     * 处理身份认证逻辑
     */
    private void handleAuth(ChannelHandlerContext ctx, JSONObject json) {
        Long id = json.getLong("id");
        Integer userType = json.getInteger("userType"); // 1-用户 2-客服

        // 将用户信息绑定到当前 Channel 属性中，方便后续获取
        ctx.channel().attr(USER_ID_KEY).set(id.toString());
        ctx.channel().attr(USER_TYPE_KEY).set(userType);

        // 将 Channel 放入全局在线映射表
        userChannels.put(userType + ":" + id, ctx.channel());

        if (userType == 2) {
            // 如果是客服上线，在 Redis 中标记客服在线
            agentService.agentOnline(id);
        } else {
            // 如果是普通用户上线，由 AgentService 自动分配一名空闲客服
            Long agentId = agentService.assignAgent(id);
            if (agentId != null) {
                // 通知用户分配结果
                notifyUser(ctx.channel(), "SYSTEM", "为您分配了客服: " + agentId, agentId);
            } else {
                // 如果没有空闲客服发送提醒
                notifyUser(ctx.channel(), "SYSTEM", "当前无空闲客服，请稍后再试", null);
            }
        }
        log.info("认证成功: 类型={}, ID={}", userType == 1 ? "用户" : "客服", id);
    }

    /**
     * 处理消息发送逻辑
     */
    private void handleSend(ChannelHandlerContext ctx, JSONObject json) {
        // 从 Channel 属性中提取当前登录者的 ID 和类型
        Long fromId = Long.parseLong(ctx.channel().attr(USER_ID_KEY).get());
        Integer fromType = ctx.channel().attr(USER_TYPE_KEY).get();
        Long toId = json.getLong("toId");
        String content = json.getString("content");
        Integer msgType = json.getInteger("msgType"); // 1-文字 2-图片..

        // 1. 将聊天记录持久化到 MySQL 数据库
        Message message = new Message();
        message.setFromId(fromId);
        message.setFromType(fromType);
        message.setToId(toId);
        message.setContent(content);
        message.setMsgType(msgType);
        chatMapper.insertMessage(message);

        // 2. 消息实时转发
        // 如果发送者是用户(1)，则接收方是客服(2)；反之亦然
        Integer targetType = (fromType == 1) ? 2 : 1;
        Channel targetChannel = userChannels.get(targetType + ":" + toId);

        // 构建返回给目标方的 JSON
        JSONObject resp = new JSONObject();
        resp.put("type", "RECEIVE");
        resp.put("fromId", fromId);
        resp.put("content", content);
        resp.put("msgType", msgType);
        resp.put("timestamp", System.currentTimeMillis());

        if (targetChannel != null && targetChannel.isActive()) {
            // 目标用户在线，直接通过 WebSocket 推送
            targetChannel.writeAndFlush(new TextWebSocketFrame(resp.toJSONString()));
        } else {
            log.info("目标用户 {} 目前不在线，消息已落库，待用户上线后拉取历史记录", toId);
        }
    }

    /**
     * 发送系统通知消息
     */
    private void notifyUser(Channel channel, String type, String content, Long agentId) {
        JSONObject resp = new JSONObject();
        resp.put("type", type);
        resp.put("content", content);
        if (agentId != null)
            resp.put("agentId", agentId);
        channel.writeAndFlush(new TextWebSocketFrame(resp.toJSONString()));
    }

    /**
     * 当连接断开时（主动退出或异常断开）执行清理逻辑
     */
    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        String userId = ctx.channel().attr(USER_ID_KEY).get();
        Integer userType = ctx.channel().attr(USER_TYPE_KEY).get();
        if (userId != null) {
            // 从内存映射中移除 Channel
            userChannels.remove(userType + ":" + userId);
            if (userType == 2) {
                // 客服下线清理 Redis 状态
                agentService.agentOffline(Long.parseLong(userId));
            } else {
                // 用户离开释放占用的客服负载
                agentService.releaseAgent(Long.parseLong(userId));
            }
        }
        log.info("连接已断开: 类型={}, ID={}", userType == 1 ? "用户" : "客服", userId);
    }

    /**
     * 处理 Netty 用户自定义事件，如状态超时（IdleStateHandler 触发）
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof io.netty.handler.timeout.IdleStateEvent) {
            io.netty.handler.timeout.IdleStateEvent event = (io.netty.handler.timeout.IdleStateEvent) evt;
            // 监听读超时（即 3 分钟未收到任何心跳或消息）
            if (event.state() == io.netty.handler.timeout.IdleState.READER_IDLE) {
                log.info("连接由于长时间(3分钟)未收到活跃反馈而触发空闲超时，系统强制关闭: {}", ctx.channel().attr(USER_ID_KEY).get());
                ctx.close();
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("WebSocket 链路发生异常，连接将被断开", cause);
        ctx.close();
    }
}
