import { apiClient } from "./client";
import type { LoginFormType } from "@/interface/Tlogin.ts";
import type { RegisterFormType } from "@/interface/Tregister.ts";

const USER_API_BASE_URL = "/user";

export const SignAPI = {
  // 用户登录
  postLogin(loginFormValue: LoginFormType) {
    return apiClient.post(`${USER_API_BASE_URL}/login`, {
      email: loginFormValue.email,
      password: loginFormValue.password,
    });
  },

  // 用户注册
  postRegister(registerFormValue: RegisterFormType) {
    return apiClient.post(`${USER_API_BASE_URL}/register`, {
      email: registerFormValue.email,
      password: registerFormValue.password,
      confirmPassword: registerFormValue.confirmPassword,
      name: registerFormValue.name,
    });
  },

  // 退出登录
  postLogout() {
    return apiClient.post(`${USER_API_BASE_URL}/logout`);
  },
};

export const RootAPI = {
  // 获取所有用户列表（管理员接口）
  getAllUsers(pageNum: number) {
    return apiClient.get(`${USER_API_BASE_URL}/admin`, {
      params: {
        pageNum: pageNum,
        pageSize: 10,
      },
    });
  },

  // 删除用户（管理员接口）
  deleteUser(userId: string) {
    return apiClient.delete(`${USER_API_BASE_URL}/admin/${userId}`);
  },
};

export const UseAPI = {
  // 获取当前用户信息
  getUserInfo() {
    return apiClient.get(`${USER_API_BASE_URL}`);
  },

  // 请求refresh接口
  postRefreshToken() {
    return apiClient.post(`${USER_API_BASE_URL}/auth/refresh`);
  },
};
