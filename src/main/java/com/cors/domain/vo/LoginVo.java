package com.cors.domain.vo;

import com.cors.enums.RoleType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginVo {

    private Long userId;

    private RoleType roleType;

    private String accessToken;
}
