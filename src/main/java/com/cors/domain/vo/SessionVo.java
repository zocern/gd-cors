package com.cors.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SessionVo {

    private Long id;

    private Long userId;

    private String title;

    private LocalDateTime created;

    private LocalDateTime updated;

}
