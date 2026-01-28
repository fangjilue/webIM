package com.webim.mapper;

import com.webim.entity.Agent;
import com.webim.entity.Message;
import com.webim.entity.User;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 后端数据库 Mapper
 */
@Mapper
public interface ChatMapper {

        // --- 客服相关 ---
        @Select("SELECT id, agent_name as agentName, work_status as workStatus, max_links as maxLinks, create_time as createTime FROM im_agent WHERE work_status = 1")
        List<Agent> selectOnlineAgents();

        @Update("UPDATE im_agent SET work_status = #{status} WHERE id = #{id}")
        int updateAgentStatus(@Param("id") Long id, @Param("status") Integer status);

        // --- 用户相关 ---
        @Insert("INSERT INTO im_user (nickname, avatar) VALUES (#{nickname}, #{avatar})")
        @Options(useGeneratedKeys = true, keyProperty = "id")
        int insertUser(User user);

        @Select("SELECT id, nickname, avatar, create_time as createTime FROM im_user WHERE id = #{id}")
        User selectUserById(Long id);

        // --- 消息记录 ---
        @Insert("INSERT INTO im_message (from_id, from_type, to_id, content, msg_type) " +
                        "VALUES (#{fromId}, #{fromType}, #{toId}, #{content}, #{msgType})")
        int insertMessage(Message message);

        @Select("SELECT id,from_id as fromId, from_type as fromType, to_id as toId, content, msg_type as msgType, create_time as createTime FROM im_message WHERE "
                        +
                        "(from_id = #{uid} AND to_id = #{targetId}) OR (from_id = #{targetId} AND to_id = #{uid}) " +
                        "ORDER BY create_time ASC LIMIT 100")
        List<Message> selectHistory(@Param("uid") Long uid, @Param("targetId") Long targetId);
}
