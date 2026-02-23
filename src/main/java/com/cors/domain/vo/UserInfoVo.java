package com.cors.domain.vo;

import com.cors.enums.RoleType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserInfoVo {

    private Long id;

    private String name;

    private String email;

    private RoleType roleType;

    private LocalDateTime created;

    private LocalDateTime updated;
}
