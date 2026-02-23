package com.cors.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cors.domain.entity.ChatMessage;
import com.cors.domain.vo.MessageVo;

public interface MessageService extends IService<ChatMessage> {

    PageDTO<MessageVo> getMessageByPage(Long sessionId, Integer pageNum, Integer pageSize);
}
