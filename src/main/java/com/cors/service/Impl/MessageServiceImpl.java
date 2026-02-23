package com.cors.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cors.domain.entity.ChatMessage;
import com.cors.domain.entity.Session;
import com.cors.domain.vo.MessageVo;
import com.cors.exception.BadRequestException;
import com.cors.mapper.MessageMapper;
import com.cors.service.MessageService;
import com.cors.service.SessionService;
import com.cors.util.UserContextUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl extends ServiceImpl<MessageMapper, ChatMessage> implements MessageService {

    private final SessionService sessionService;

    @Override
    public PageDTO<MessageVo> getMessageByPage(Long sessionId, Integer pageNum, Integer pageSize) {
        // 获取当前用户ID
        Long userId = UserContextUtil.getUserId();

        Session session = sessionService.getById(sessionId);
        if (session == null) {
            throw new BadRequestException("会话不存在");
        }
        if (!userId.equals(session.getUserId())) {
            throw new BadRequestException("获取该会话记录权限不足");
        }

        // 分页查询指定会话的消息，按创建时间倒序
        Page<ChatMessage> page = this.lambdaQuery()
                .eq(ChatMessage::getSessionId, sessionId) // 查询指定会话的消息
                .orderByDesc(ChatMessage::getCreated) // 按 created 倒序
                .page(new Page<>(pageNum, pageSize));

        // 获取分页记录
        List<ChatMessage> chatMessages = page.getRecords();

        List<MessageVo> messageVos = BeanUtil.copyToList(chatMessages, MessageVo.class);

        // 如果没有记录，返回空的分页结果
        if (chatMessages.isEmpty()) {
            return new PageDTO<>(pageNum, pageSize, page.getTotal());
        }

        // 用 PageDTO 包装
        PageDTO<MessageVo> pageDTO = new PageDTO<>(pageNum, pageSize, page.getTotal());
        pageDTO.setRecords(messageVos);
        return pageDTO;
    }
}
