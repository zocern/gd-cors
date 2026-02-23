package com.cors.service.Impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cors.domain.dto.LoginDto;
import com.cors.domain.dto.RegisterDto;
import com.cors.domain.dto.UserUpdateDto;
import com.cors.domain.entity.User;
import com.cors.domain.vo.LoginVo;
import com.cors.domain.vo.UserInfoVo;
import com.cors.domain.vo.UserVo;
import com.cors.enums.RoleType;
import com.cors.exception.BadRequestException;
import com.cors.exception.UnauthorizedException;
import com.cors.mapper.UserMapper;
import com.cors.service.UserService;
import com.cors.util.BCryptUtil;
import com.cors.util.JwtUtil;
import com.cors.util.TokenRedisUtil;
import com.cors.util.UserContextUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import net.sf.jsqlparser.util.validation.metadata.DatabaseException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import static com.cors.constant.CommonConstants.REFRESH_TOKEN;


@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final BCryptUtil bCryptUtils;
    private final JwtUtil jwtUtil;
    private final TokenRedisUtil tokenRedisUtil;

    @Value("${user.password-length}")
    private Integer passwordLength;

    @Value("${jwt.refresh-expire}")
    private long refreshExpire;

    @Override
    public LoginVo login(LoginDto loginDto, HttpServletResponse response) {
        // 通过邮箱查询用户
        User user = lambdaQuery()
                .eq(User::getEmail, loginDto.getEmail())
                .one();
        // 校验用户是否存在及密码是否正确
        if (user == null || !bCryptUtils.matches(loginDto.getPassword().trim(), user.getPassword())) {
            throw new BadRequestException("用户不存在或者密码错误");
        }
        String accessToken = jwtUtil.generateAccessToken(user.getId());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN, refreshToken)
                .httpOnly(true)                         // 禁止 JS 读取
                // .secure(true)                           // 生产环境必须为 true (仅 HTTPS)
                .path("/api/v1/user/auth/refresh")      // 限制 Cookie 仅在刷新接口发送
                .maxAge(Duration.ofDays(refreshExpire))                  // 设置过期时间，与 Redis/JWT 保持一致
                .sameSite("Strict")                     // 防止 CSRF
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        tokenRedisUtil.addToken(user.getId(), refreshToken);
        return LoginVo.builder()
                .userId(user.getId())
                .roleType(user.getRole())
                .accessToken(accessToken)
                .build();
    }

    @Override
    public String getNewToken(String refreshToken, HttpServletResponse response) {
        Long userId = jwtUtil.verifyRefreshToken(refreshToken);
        if (!refreshToken.equals(tokenRedisUtil.getToken(userId))) {
            throw new UnauthorizedException("refreshToken 错误，请重新登录");
        }
        String accessToken = jwtUtil.generateAccessToken(userId);
        String newRefreshToken = jwtUtil.generateRefreshToken(userId);
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN, newRefreshToken)
                .httpOnly(true)                         // 禁止 JS 读取
                // .secure(true)                           // 生产环境必须为 true (仅 HTTPS)
                .path("/api/v1/user/auth/refresh")      // 限制 Cookie 仅在刷新接口发送
                .maxAge(Duration.ofDays(refreshExpire)) // 设置过期时间，与 Redis/JWT 保持一致
                .sameSite("Strict")                     // 防止 CSRF
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        tokenRedisUtil.addToken(userId, newRefreshToken);
        return accessToken;
    }



    @Override
    public void logout() {
        Long userId = UserContextUtil.getUserId();
        tokenRedisUtil.removeToken(userId);
    }

    @Override
    public String register(RegisterDto registerDto) {
        // 字符串去掉左侧右侧空格
        String password = registerDto.getPassword().trim();
        String confirmPassword = registerDto.getConfirmPassword().trim();

        // 检验密码是否符合规则
        if (!password.equals(confirmPassword)) {
            throw new BadRequestException("两次密码不一致");
        }
        if (password.length() < passwordLength) {
            throw new BadRequestException("密码的长度至少为" + passwordLength + "位！");
        }
        String email = registerDto.getEmail();
        // 查询该邮箱是否注册
        boolean exists = lambdaQuery()
                .eq(User::getEmail, email)
                .exists();
        if (exists) {
            throw new BadRequestException("该邮箱已注册");
        }
        String name = registerDto.getName();
        if (lambdaQuery().eq(User::getName, name).exists()) {
            throw new BadRequestException("该用户名已存在");
        }
        String hashPassword = bCryptUtils.hashPassword(password);
        User user = User.builder().name(name).email(email).password(hashPassword).build();
        if (!this.save(user)) {
            throw new DatabaseException("用户信息保存失败");
        }
        return user.getEmail();
    }

    @Override
    public UserInfoVo getUserInfo() {
        Long userId = UserContextUtil.getUserId();
        User user = this.getById(userId);
        if (user == null) {
            throw new UnauthorizedException("用户信息不存在");
        }
        UserInfoVo userInfoVo = new UserInfoVo();
        BeanUtils.copyProperties(user, userInfoVo);
        return userInfoVo;
    }

    @Override
    public void updateUserInfo(UserUpdateDto userUpdateDto) {
        Long userId = UserContextUtil.getUserId();
        // 先判断是否存在
        if (!this.exists(Wrappers.<User>lambdaQuery()
                .eq(User::getId, userId))) {
            throw new BadRequestException("用户ID不存在或不可用");
        }
        User user = new User();
        user.setId(userId);
        BeanUtils.copyProperties(userUpdateDto, user);
        if (userUpdateDto.getPassword() != null && userUpdateDto.getConfirmPassword() != null) {
            if (!userUpdateDto.getPassword().equals(userUpdateDto.getConfirmPassword())) {
                throw new BadRequestException("两次输入的新密码不一致");
            }
            user.setPassword(bCryptUtils.hashPassword(userUpdateDto.getPassword()));
        }
        if (!this.updateById(user)) {
            throw new DatabaseException("MybatisPlus更新数据库失败");
        }
    }

    @Override
    public void deleteUser(Long id) {
        Long userId = UserContextUtil.getUserId();
        RoleType role = this.getById(userId).getRole();
        if (!role.equals(RoleType.ADMIN)) {
            throw new UnauthorizedException("权限不足");
        }
        if (userId.equals(id)) {
            throw new UnauthorizedException("无法删除已登录账号");
        }
        if (!this.removeById(id)) {
            throw new DatabaseException("删除用户失败");
        }
        // 使Token失效
        tokenRedisUtil.removeToken(id);
    }

    @Override
    public void cancelUser() {
        Long userId = UserContextUtil.getUserId();
        if (!this.removeById(userId)) {
            throw new DatabaseException("注销失败");
        }
        // 使Token失效
        tokenRedisUtil.removeToken(userId);
    }

    @Override
    public UserVo getUserByEmail(String email) {

        User user = lambdaQuery()
                .like(User::getEmail, email)
                .one();
        UserVo userVo = new UserVo();
        BeanUtils.copyProperties(user, userVo);

        return userVo;
    }

    @Override
    public PageDTO<UserInfoVo> getAllUserInfo(Integer pageNum, Integer pageSize) {
        // 分页查询
        Page<User> page = this.lambdaQuery().page(new Page<>(pageNum, pageSize));

        // 获取分页记录
        List<User> records = page.getRecords();

        // 如果没有记录，返回空的 PageDTO
        if (records.isEmpty()) {
            return new PageDTO<>();
        }

        // 转换结果
        List<UserInfoVo> userInfoVo = records.stream()
                .map(this::getUserInfoVo)
                .filter(Objects::nonNull)
                .toList();

        // 用 PageDTO 包装
        PageDTO<UserInfoVo> result = new PageDTO<>(pageNum, pageSize, page.getTotal());
        result.setRecords(userInfoVo);

        return result;
    }

    private UserInfoVo getUserInfoVo(User user) {
        if (user != null) {
            // 封装vo
            UserInfoVo userInfoVo = new UserInfoVo();
            BeanUtils.copyProperties(user, userInfoVo);
            return userInfoVo;
        }
        return null;
    }
}
