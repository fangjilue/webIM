package com.webim.entity;

import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * 客服实体
 */
@Data
public class Agent implements Serializable {
    private Long id;
    private String agentName;
    private Integer workStatus; // 0-不在线 1-在线 2-忙碌
    private Integer maxLinks;
    private Date createTime;
}
