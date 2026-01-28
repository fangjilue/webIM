package com.webim.entity;

import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * 用户实体
 */
@Data
public class User implements Serializable {
    private Long id;
    private String nickname;
    private String avatar;
    private Date createTime;
}
