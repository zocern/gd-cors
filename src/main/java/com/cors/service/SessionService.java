package com.cors.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cors.domain.dto.SessionCreateDto;
import com.cors.domain.dto.SessionUpdateDto;
import com.cors.domain.entity.Session;
import com.cors.domain.vo.SessionVo;

import java.util.List;

public interface SessionService extends IService<Session> {

    List<SessionVo> getSessionList();

    Long createSession(SessionCreateDto sessionCreateDto);

    void deleteSession(Long id);

    void updateSession(Long id, SessionUpdateDto sessionUpdateDto);
}
