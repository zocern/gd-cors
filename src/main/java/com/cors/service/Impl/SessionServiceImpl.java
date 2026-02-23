package com.cors.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cors.domain.dto.SessionCreateDto;
import com.cors.domain.dto.SessionUpdateDto;
import com.cors.domain.entity.Session;
import com.cors.domain.vo.SessionVo;
import com.cors.exception.BadRequestException;
import com.cors.mapper.SessionMapper;
import com.cors.memory.RedisChatMemoryStore;
import com.cors.service.SessionService;
import com.cors.util.UserContextUtil;
import lombok.RequiredArgsConstructor;
import net.sf.jsqlparser.util.validation.metadata.DatabaseException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SessionServiceImpl extends ServiceImpl<SessionMapper, Session> implements SessionService {

    private final RedisChatMemoryStore redisChatMemoryStore;

    @Override
    public List<SessionVo> getSessionList() {
        Long userId = UserContextUtil.getUserId();

        List<Session> sessions = this.list(new LambdaQueryWrapper<Session>().eq(Session::getUserId, userId));

        List<SessionVo> sessionVos = BeanUtil.copyToList(sessions, SessionVo.class);
        if (sessionVos == null || sessionVos.isEmpty()) {
            throw new DatabaseException("会话列表为空");
        }
        return sessionVos;
    }

    @Override
    public Long createSession(SessionCreateDto sessionCreateDto) {
        Long userId = UserContextUtil.getUserId();

        Session session = Session.builder()
                .userId(userId)
                .title(sessionCreateDto.getTitle())
                .build();
        if (!this.save(session)) {
            throw new DatabaseException("创建会话失败");
        }
        return session.getId();
    }

    @Override
    @Transactional
    public void deleteSession(Long id) {
        Long userId = UserContextUtil.getUserId();
        Session session = this.getById(id);
        if (Objects.isNull(session)) {
            throw new DatabaseException("会话为空, ID: " + id);
        }
        if (!Objects.equals(session.getUserId(), userId)) {
            throw new BadRequestException("删除权限不足");
        }
        if (!this.removeById(id)) {
            throw new DatabaseException("删除会话失败");
        }
        try {
            redisChatMemoryStore.deleteMessages(id);
        } catch (Exception e) {
            throw new RuntimeException("同步清理会话记忆失败", e);
        }
    }

    @Override
    public void updateSession(Long id, SessionUpdateDto sessionUpdateDto) {
        Long userId = UserContextUtil.getUserId();
        if (!Objects.equals(userId, this.getById(id).getUserId())) {
            throw new BadRequestException("修改权限不足");
        }
        boolean update = this.lambdaUpdate()
                .eq(Session::getId, id)
                .set(Session::getTitle, sessionUpdateDto.getTitle())
                .update();
        if (!update) {
            throw new DatabaseException("修改会话失败");
        }
    }
}
