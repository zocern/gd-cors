package com.cors.controller;

import com.cors.domain.ResponseResult;
import com.cors.domain.dto.SessionCreateDto;
import com.cors.domain.dto.SessionUpdateDto;
import com.cors.domain.vo.SessionVo;
import com.cors.service.SessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/sessions")
public class SessionController {

    private final SessionService sessionService;

    @GetMapping
    public ResponseResult<List<SessionVo>> listSessions() {
        return ResponseResult.success(sessionService.getSessionList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseResult<Long> createSession(@Valid @RequestBody SessionCreateDto sessionCreateDto) {
        return ResponseResult.success(sessionService.createSession(sessionCreateDto));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseResult<Void> deleteSession(@PathVariable @NotNull(message = "ID不能为空") Long id) {
        sessionService.deleteSession(id);
        return ResponseResult.success();
    }

    @PatchMapping("/{id}")
    public ResponseResult<Void> updateSession(@PathVariable @NotNull(message = "ID不能为空") Long id,
                                              @Valid @RequestBody SessionUpdateDto sessionUpdateDto) {
        sessionService.updateSession(id, sessionUpdateDto);
        return ResponseResult.success();
    }
}
