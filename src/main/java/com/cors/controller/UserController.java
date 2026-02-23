package com.cors.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.cors.annotation.AuthorizeAnnotation;
import com.cors.domain.ResponseResult;
import com.cors.domain.dto.LoginDto;
import com.cors.domain.dto.RegisterDto;
import com.cors.domain.dto.UserUpdateDto;
import com.cors.domain.vo.LoginVo;
import com.cors.domain.vo.UserInfoVo;
import com.cors.domain.vo.UserVo;
import com.cors.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import static com.cors.constant.CommonConstants.REFRESH_TOKEN;

@RestController
@RequiredArgsConstructor
@RequestMapping("/user")
public class UserController {

    private final UserService userService;


    /**
     * 注册
     *
     * @param registerDto
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseResult<String> register(@Valid @RequestBody RegisterDto registerDto) {
        String email = userService.register(registerDto);
        return ResponseResult.success(email);
    }

    /**
     * 登录
     *
     * @param loginDto
     */
    @PostMapping("/login")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseResult<LoginVo> login(@Valid @RequestBody LoginDto loginDto, HttpServletResponse response) {
        LoginVo loginVo = userService.login(loginDto, response);
        return ResponseResult.success(loginVo);
    }

    @PostMapping("/auth/refresh")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseResult<String> getNewToken(@CookieValue(name = REFRESH_TOKEN) String refreshToken, HttpServletResponse response) {
        String accessToken = userService.getNewToken(refreshToken, response);
        return ResponseResult.success(accessToken);
    }


    /**
     * 退出
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseResult<Void> logout() {
        userService.logout();
        return ResponseResult.success();
    }

    /**
     * 查看个人信息
     */
    @GetMapping
    public ResponseResult<UserInfoVo> getUserInfo() {
        UserInfoVo userInfoVo = userService.getUserInfo();
        return ResponseResult.success(userInfoVo);
    }

    /**
     * 注销
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseResult<Void> cancelUser() {
        userService.cancelUser();
        return ResponseResult.success();
    }

    /**
     * 修改个人信息
     */
    @PutMapping
    public ResponseResult<Object> updateUserInfo(@RequestBody UserUpdateDto userUpdateDto) {
        userService.updateUserInfo(userUpdateDto);
        return ResponseResult.success();
    }

    ////// 管理员额外操作 //////

    /**
     * 删除用户
     */
    @DeleteMapping("/admin/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @AuthorizeAnnotation
    public ResponseResult<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseResult.success();
    }

    @GetMapping("/admin/email")
    @AuthorizeAnnotation
    public ResponseResult<UserVo> getUserByEmail(@RequestParam String email) {
        return ResponseResult.success(userService.getUserByEmail(email));
    }

    /**
     * 查看用户列表
     */
    @GetMapping("/admin")
    @AuthorizeAnnotation
    public ResponseResult<PageDTO<UserInfoVo>> getAllUserInfo(@RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                                              @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        //分页查询订单信息
        PageDTO<UserInfoVo> page = userService.getAllUserInfo(pageNum, pageSize);
        return ResponseResult.success(page);
    }
}
