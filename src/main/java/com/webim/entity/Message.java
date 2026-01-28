package com.webim.entity;

import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * 消息记录实体
 */
@Data
public class Message implements Serializable {
    private Long id;
    private Long fromId;
    private Integer fromType; // 1-用户 2-客服
    private Long toId;
    private String content;
    private Integer msgType; // 1-文字 2-表情 3-图片
    private Date createTime;
}
