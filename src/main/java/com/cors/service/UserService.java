package com.cors.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cors.domain.dto.LoginDto;
import com.cors.domain.dto.RegisterDto;
import com.cors.domain.dto.UserUpdateDto;
import com.cors.domain.entity.User;
import com.cors.domain.vo.LoginVo;
import com.cors.domain.vo.UserInfoVo;
import com.cors.domain.vo.UserVo;
import jakarta.servlet.http.HttpServletResponse;

public interface UserService extends IService<User> {

    LoginVo login(LoginDto loginDto, HttpServletResponse response);

    String getNewToken(String refreshToken, HttpServletResponse response);

    void logout();

    String register(RegisterDto registerDto);

    UserInfoVo getUserInfo();

    void updateUserInfo(UserUpdateDto userUpdateDto);

    void deleteUser(Long id);

    void cancelUser();

    UserVo getUserByEmail(String email);

    PageDTO<UserInfoVo> getAllUserInfo(Integer pageNum, Integer pageSize);
}
