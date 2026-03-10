<template>
  <el-container class="login-container">
    <el-container class="login-card">
      <!-- 标题 -->
      <el-header class="login-header">
        <h1 class="login-title">欢迎回来</h1>
        <p class="login-subtitle">请使用您的邮箱和密码登录</p>
      </el-header>

      <!-- 表单体 -->
      <el-form
        :model="loginForm"
        class="login-form"
        @submit.prevent="handleLogin"
      >
        <el-form-item label="邮箱地址" label-position="top" size="large">
          <el-input
            v-model="loginForm.email"
            placeholder="请输入您的邮箱地址"
            prefix-icon="UserFilled"
          />
        </el-form-item>

        <el-form-item label="密码" label-position="top" size="large">
          <el-input
            v-model="loginForm.password"
            placeholder="请输入您的密码"
            prefix-icon="Key"
            :type="showPassword ? 'text' : 'password'"
          >
            <template #suffix>
              <el-button
                @click="showPassword = !showPassword"
                link
                style="border: none; box-shadow: none"
              >
                <el-icon v-if="showPassword"><Hide /></el-icon>
                <el-icon v-else><View /></el-icon>
              </el-button>
            </template>
          </el-input>
        </el-form-item>
      </el-form>

      <!-- 登录按钮 -->
      <el-button
        @click="handleLogin"
        class="login-button"
        :disabled="isLoading"
        size="large"
        style="margin-top: 1.5rem"
      >
        <span v-if="isLoading" class="loading-spinner"></span>
        <span v-else>登录</span>
      </el-button>

      <!-- 注册按钮 -->
      <div class="login-footer">
        <p class="register-text">
          还没有账号？
          <a
            href="#"
            class="register-link"
            @click.prevent="router.push('/register')"
            >立即注册</a
          >
        </p>
      </div>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { Hide, View } from "@element-plus/icons-vue";

defineOptions({
  name: "Login",
});

import { type Ref, ref } from "vue";
import { useRouter } from "vue-router";
import { SignAPI, UseAPI } from "@/services/user.ts";
import { ElMessage } from "element-plus";
import { useUserStore } from "@/stores/user.ts";
import type { LoginFormType } from "@/interface/Tlogin.ts";

const router = useRouter();
const userStore = useUserStore();

const loginForm: Ref<LoginFormType> = ref({
  email: "",
  password: "",
  rememberMe: false,
}); // 登录表单数据

const showPassword: Ref<boolean> = ref(false); // 是否显示密码
const isLoading: Ref<boolean> = ref(false); // 加载状态

// 登录处理函数
const handleLogin = async (): Promise<void> => {
  if (isLoading.value) return;

  isLoading.value = true;

  try {
    const res = await SignAPI.postLogin(loginForm.value);

    if (res.code === 200) {
      // 设置状态
      userStore.setRole(res.data.role);
      userStore.setToken(res.data.accessToken);

      // 跳转到首页
      await router.replace("/home");
      ElMessage.success("登录成功！");
    } else {
      ElMessage.error(res.msg);
    }
  } catch (error) {
    ElMessage.warning("登录失败！请联系管理员");
  } finally {
    isLoading.value = false;
  }
};
</script>

<style scoped lang="scss">
.login-container {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-color);
  padding: 2rem;
  overflow: hidden;

  .login-card {
    width: 100%;
    max-width: 420px;
    backdrop-filter: blur(10px);
    border-radius: 1.5rem;
    box-shadow: 0 20px 40px rgba(0, 0, 0, 0.1);
    padding: 3rem 2.5rem;
    border: 1px solid rgba(255, 255, 255, 0.2);
  }

  .login-header {
    text-align: center;
    margin-bottom: 2.5rem;

    .login-title {
      font-size: 2rem;
      font-weight: 700;
      color: #1a1a1a;
      margin: 0 0 0.5rem 0;
      background: linear-gradient(135deg, #007cf0, #00d4ff);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      background-clip: text;
    }

    .login-subtitle {
      font-size: 1rem;
      color: #666;
      margin: 0;
      font-weight: 400;
    }
  }

  .login-form {
    .form-group {
      margin-bottom: 1.5rem;

      .form-label {
        display: block;
        font-size: 0.875rem;
        font-weight: 600;
        color: #374151;
        margin-bottom: 0.5rem;
      }

      .input-wrapper {
        position: relative;
        display: flex;
        align-items: center;

        .form-input {
          width: 100%;
          padding: 1rem 1rem 1rem 3rem;
          border: 2px solid rgba(0, 0, 0, 0.1);
          border-radius: 0.75rem;
          font-size: 1rem;
          background: rgba(255, 255, 255, 0.8);
          transition: all 0.3s ease;
          outline: none;

          &:focus {
            border-color: #007cf0;
            background: rgba(255, 255, 255, 0.95);
            box-shadow: 0 0 0 3px rgba(0, 124, 240, 0.1);
          }

          &::placeholder {
            color: #9ca3af;
          }
        }

        .input-icon {
          position: absolute;
          left: 1rem;
          color: #9ca3af;
          pointer-events: none;

          .icon {
            width: 1.25rem;
            height: 1.25rem;
          }
        }

        .password-toggle {
          position: absolute;
          right: 1rem;
          background: none;
          border: none;
          color: #9ca3af;
          cursor: pointer;
          padding: 0.25rem;
          border-radius: 0.25rem;
          transition: color 0.2s ease;

          &:hover {
            color: #007cf0;
          }

          .icon {
            width: 1.25rem;
            height: 1.25rem;
          }
        }
      }
    }

    .form-options {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 2rem;

      .remember-me {
        display: flex;
        align-items: center;
        cursor: pointer;

        .checkbox {
          width: 1rem;
          height: 1rem;
          margin-right: 0.5rem;
          accent-color: #007cf0;
        }

        .checkbox-text {
          font-size: 0.875rem;
          color: #374151;
        }
      }

      .forgot-password {
        font-size: 0.875rem;
        color: #007cf0;
        text-decoration: none;
        transition: color 0.2s ease;

        &:hover {
          color: #0066cc;
        }
      }
    }
  }

  .login-button {
    width: 100%;
    padding: 1rem;
    background: linear-gradient(135deg, #007cf0, #00d4ff);
    color: white;
    border: none;
    border-radius: 0.75rem;
    font-size: 1rem;
    font-weight: 600;
    cursor: pointer;
    transition: all 0.3s ease;
    position: relative;
    overflow: hidden;

    &:hover:not(:disabled) {
      transform: translateY(-2px);
      box-shadow: 0 10px 25px rgba(0, 124, 240, 0.3);
    }

    &:active:not(:disabled) {
      transform: translateY(0);
    }

    &:disabled {
      opacity: 0.7;
      cursor: not-allowed;
      transform: none;
    }

    .loading-spinner {
      display: inline-block;
      width: 1.25rem;
      height: 1.25rem;
      border: 2px solid rgba(255, 255, 255, 0.3);
      border-radius: 50%;
      border-top-color: white;
      animation: spin 1s ease-in-out infinite;
    }
  }

  .login-footer {
    text-align: center;
    margin-top: 2rem;

    .register-text {
      font-size: 0.875rem;
      color: #666;
      margin: 0;

      .register-link {
        color: #007cf0;
        text-decoration: none;
        font-weight: 600;
        transition: color 0.2s ease;

        &:hover {
          color: #0066cc;
        }
      }
    }
  }
}

@media (max-width: 480px) {
  .login-container {
    padding: 1rem;

    .login-card {
      padding: 2rem 1.5rem;
    }

    .login-header {
      .login-title {
        font-size: 1.75rem;
      }
    }
  }
}
</style>
