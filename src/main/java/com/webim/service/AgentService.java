package com.webim.service;

import com.webim.mapper.ChatMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 客服调度与负载均衡服务
 * 利用 Redis 实现跨实例的客服状态同步与会话分配
 */
@Slf4j
@Service
public class AgentService {

    private final StringRedisTemplate redisTemplate;
    private final ChatMapper chatMapper;

    // Redis 键名命名空间
    private static final String ONLINE_AGENTS_KEY = "agent:online"; // 存储所有在线客服 ID 的集合 (Set)
    private static final String AGENT_LOAD_KEY_PREFIX = "agent:load:"; // 客服负载计数器 (String/Counter)
    private static final String USER_AGENT_MAP_KEY = "user:agent:"; // 用户与客服的绑定关系 (String)

    public AgentService(StringRedisTemplate redisTemplate, ChatMapper chatMapper) {
        this.redisTemplate = redisTemplate;
        this.chatMapper = chatMapper;
    }

    /**
     * 客服上线
     * 将客服 ID 加入在线集合，初始化负载计数器，并同步数据库状态
     */
    public void agentOnline(Long agentId) {
        redisTemplate.opsForSet().add(ONLINE_AGENTS_KEY, agentId.toString());
        redisTemplate.opsForValue().setIfAbsent(AGENT_LOAD_KEY_PREFIX + agentId, "0");
        chatMapper.updateAgentStatus(agentId, 1);
        log.info("客服 {} 已上线，并已注册到全局调度池", agentId);
    }

    /**
     * 客服下线
     * 从在线集合移除，同步数据库状态
     */
    public void agentOffline(Long agentId) {
        redisTemplate.opsForSet().remove(ONLINE_AGENTS_KEY, agentId.toString());
        chatMapper.updateAgentStatus(agentId, 0);
        log.info("客服 {} 已下线，停止接收新咨询", agentId);
    }

    /**
     * 为用户分配空闲客服（核心调度算法：最少连接数）
     * 1. 优先检查旧有的绑定关系（会话保持）
     * 2. 若无绑定，则在所有在线客服中寻找当前负载（连接数）最小的一个
     */
    public Long assignAgent(Long userId) {
        // 第一步：检查 Redis 中是否存有该用户的活跃会话记录，且对应的客服依然在线
        String existingAgentId = redisTemplate.opsForValue().get(USER_AGENT_MAP_KEY + userId);
        if (existingAgentId != null && redisTemplate.opsForSet().isMember(ONLINE_AGENTS_KEY, existingAgentId)) {
            log.debug("用户 {} 恢复了与客服 {} 的会话保持", userId, existingAgentId);
            return Long.parseLong(existingAgentId);
        }

        // 第二步：获取当前所有在线的客服 ID 集合
        Set<String> onlineAgents = redisTemplate.opsForSet().members(ONLINE_AGENTS_KEY);
        if (onlineAgents == null || onlineAgents.isEmpty()) {
            log.warn("用户 {} 进线失败：当前无任何在线客服", userId);
            return null;
        }

        // 第三步：寻找负载最低（计数器值最小）的客服（简单轮询/最少连接算法）
        String bestAgent = null;
        int minLoad = Integer.MAX_VALUE;

        for (String agentId : onlineAgents) {
            String loadStr = redisTemplate.opsForValue().get(AGENT_LOAD_KEY_PREFIX + agentId);
            int load = loadStr == null ? 0 : Integer.parseInt(loadStr);
            if (load < minLoad) {
                minLoad = load;
                bestAgent = agentId;
            }
        }

        if (bestAgent != null) {
            // 第四步：建立绑定关系，并增加目标客服的负载计数
            redisTemplate.opsForValue().set(USER_AGENT_MAP_KEY + userId, bestAgent);
            redisTemplate.opsForValue().increment(AGENT_LOAD_KEY_PREFIX + bestAgent);
            log.info("用户 {} 成功分配客服 {}，当前客服负载: {}", userId, bestAgent, minLoad + 1);
        }

        return bestAgent != null ? Long.parseLong(bestAgent) : null;
    }

    /**
     * 用户断开连接（退出/超时）时释放负载
     * 减小对应客服的连接计数，并清除绑定关系
     */
    public void releaseAgent(Long userId) {
        String agentId = redisTemplate.opsForValue().get(USER_AGENT_MAP_KEY + userId);
        if (agentId != null) {
            redisTemplate.opsForValue().decrement(AGENT_LOAD_KEY_PREFIX + agentId);
            redisTemplate.delete(USER_AGENT_MAP_KEY + userId);
            log.info("用户 {} 离线，已释放客服 {} 的负载", userId, agentId);
        }
    }
}
