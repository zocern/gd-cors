package com.cors.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.cors.domain.ResponseResult;
import com.cors.domain.vo.MessageVo;
import com.cors.service.MessageService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/messages")
public class MessageController {

    private final MessageService messageService;

    @GetMapping
    public ResponseResult<PageDTO<MessageVo>> listMessages(@RequestParam @NotNull(message = "会话ID不能为空") Long sessionId,
                                                           @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                                           @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        //分页查询对话消息
        PageDTO<MessageVo> page = messageService.getMessageByPage(sessionId, pageNum, pageSize);
        return ResponseResult.success(page);
    }
}
