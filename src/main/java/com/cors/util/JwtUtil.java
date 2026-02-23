package com.cors.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.cors.exception.TokenInvalidException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;


/**
 * <p>
 * jwt工具类
 * </p>
 *
 * @author vlsmb
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtil {

    @Value("${jwt.security}")
    private String security;
    @Value("${jwt.access-expire}")
    private long accessExpire;
    @Value("${jwt.refresh-expire}")
    private long refreshExpire;

    private final TokenRedisUtil tokenRedisUtil;

    /**
     * 生成 Token JWT令牌
     *
     * @param userId 用户信息
     * @return JWT令牌字符串
     */
    public String generateAccessToken(Long userId) {
        return JWT.create()
                .withClaim("userId", userId)
                .withExpiresAt(expireTimeInMinutes(accessExpire))
                .sign(Algorithm.HMAC256(security));
    }

    /**
     * 生成 RefreshToken JWT令牌
     *
     * @param userId 用户信息
     * @return JWT令牌字符串
     */
    public String generateRefreshToken(Long userId) {
        return JWT.create()
                .withClaim("userId", userId)
                .withExpiresAt(expireTimeInDays(refreshExpire))
                .sign(Algorithm.HMAC256(security));
    }

    /**
     * 校验 accessToken JWT 令牌信息
     *
     * @param token JWT令牌
     * @return UserClaims对象
     */
    public Long verifyAccessToken(String token) {
        try {
            return JWT.require(Algorithm.HMAC256(security))
                    .build()
                    .verify(token)
                    .getClaim("userId")
                    .asLong();
        } catch (Exception e) {
            throw new TokenInvalidException("accessToken 无效或者已经过期", e);
        }
    }

    /**
     * 校验 refreshToken JWT 令牌信息
     *
     * @param token JWT令牌
     */
    public Long verifyRefreshToken(String token) {
        try {
            return JWT.require(Algorithm.HMAC256(security))
                    .build()
                    .verify(token)
                    .getClaim("userId")
                    .asLong();
        } catch (Exception e) {
            throw new TokenInvalidException("refreshToken 无效或者已经过期", e);
        }
    }

    /**
     * 无感刷新 Token
     * 由于 Token 自动续期，后端无法区分请求是来自合法用户还是被劫持的会话
     */
//    public Long verifyToken(String token, HttpServletResponse response) {
//        try {
//            // 正常校验（未过期、签名正确）
//            return JWT.require(Algorithm.HMAC256(security))
//                    .build()
//                    .verify(token)
//                    .getClaim("userId")
//                    .asLong();
//        } catch (TokenExpiredException e) {
//            // accessToken过期 获取refreshToken并刷新
//            DecodedJWT decodeToken = JWT.decode(token);
//            Long userId = decodeToken.getClaim("userId").asLong();
//            if (tokenRedisUtil.getToken(userId) == null) {
//                throw new UnauthorizedException("token已过期");
//            }
//            String newToken = generateRefreshToken(userId);
//            if (newToken == null) {
//                throw new DatabaseException("保存Token到Redis失败", e);
//            }
//            tokenRedisUtil.addToken(userId, newToken);
//            // 写入新的 token 到响应头
//            response.setHeader("Authorization", newToken);
//            return userId;
//        } catch (JWTVerificationException e) {
//            // 捕获其他所有验证异常，例如签名无效
//            throw new UnauthorizedException("token无效");
//        }
//    }


    /**
     * 计算令牌过期时间
     *
     * @return Date对象
     */
    // 以天为单位
    private Date expireTimeInDays(long days) {
        return new Date(System.currentTimeMillis() + days * 24 * 60 * 60 * 1000L);  // 计算过期时间
    }

    // 以分钟为单位
    private Date expireTimeInMinutes(long minutes) {
        return new Date(System.currentTimeMillis() + minutes * 60 * 1000L);  // 计算过期时间
    }

    // 以秒为单位
    private Date expireTimeInSeconds(long seconds) {
        return new Date(System.currentTimeMillis() + seconds * 1000L);  // 计算过期时间
    }
}
