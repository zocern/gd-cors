package com.cors.aspect;

import com.cors.enums.RoleType;
import com.cors.exception.UnauthorizedException;
import com.cors.mapper.UserMapper;
import com.cors.util.UserContextUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthorizeAspect {

    private final UserMapper userMapper;

    @Pointcut("@annotation(com.cors.annotation.AuthorizeAnnotation)")
    public void pointcut() {
    }

    @Around("pointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 输入参数
        // Object[] args = joinPoint.getArgs();
        Long userId = UserContextUtil.getUserId();
        if (!RoleType.ADMIN.equals(userMapper.selectById(userId).getRole())) {
            throw new UnauthorizedException("权限不足");
        }
        return joinPoint.proceed();
    }
}

