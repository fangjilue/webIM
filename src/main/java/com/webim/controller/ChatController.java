package com.webim.controller;

import com.webim.entity.Message;
import com.webim.mapper.ChatMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * 聊天相关 HTTP 接口
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatMapper chatMapper;

    public ChatController(ChatMapper chatMapper) {
        this.chatMapper = chatMapper;
    }

    /**
     * 获取历史消息
     */
    @GetMapping("/history")
    public List<Message> getHistory(@RequestParam Long userId, @RequestParam Long targetId) {
        return chatMapper.selectHistory(userId, targetId);
    }

    /**
     * 图片上传
     */
    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return "error";
        }

        String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
        String filePath = System.getProperty("user.dir") + "/uploads/";
        File dest = new File(filePath + fileName);

        if (!dest.getParentFile().exists()) {
            dest.getParentFile().mkdirs();
        }

        try {
            file.transferTo(dest);
            // 这里返回相对路径，前端拼接完整 URL
            return "/uploads/" + fileName;
        } catch (IOException e) {
            log.error("上传文件失败", e);
            return "error";
        }
    }
}
