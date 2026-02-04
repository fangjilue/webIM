package com.webim.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.webim.netty.handler.ChatHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Redis 消息订阅监听器
 * 负责接收来自其他服务器转发的消息，并推送到本地连接的用户
 */
@Slf4j
@Service
public class RedisMessageListener implements MessageListener {

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            log.debug("收到 Redis 广播消息: {}", body);

            JSONObject json = JSON.parseObject(body);
            Long toId = json.getLong("toId");
            Integer toType = json.getInteger("toType");
            String content = json.getString("content");
            Long fromId = json.getLong("fromId");
            Integer msgType = json.getInteger("msgType");

            // 尝试推送到本地用户
            // 注意: 这里并没有判断 toType 是否为 null，实际业务中应该健壮处理
            if (toId != null && toType != null) {
                ChatHandler.pushMessageToLocalUser(toId, toType, fromId, content, msgType);
            }

        } catch (Exception e) {
            log.error("处理 Redis 广播消息失败", e);
        }
    }
}
