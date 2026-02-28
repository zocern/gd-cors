<template>
  <div class="register-container" :class="{ dark: isDark }">
    <div class="register-card">
      <div class="register-header">
        <h1 class="register-title">创建新账号</h1>
        <p class="register-subtitle">请填写以下信息完成注册</p>
      </div>

      <el-form class="register-form" :model="registerForm" label-position="top">
        <el-form-item required label="用户名称">
          <el-input
            v-model="registerForm.name"
            type="text"
            size="large"
            placeholder="请输入您的名称"
          >
            <template #prefix>
              <el-icon class="input-icon">
                <User />
              </el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item required label="邮箱地址">
          <el-input
            v-model="registerForm.email"
            type="email"
            size="large"
            placeholder="请输入您的邮箱地址"
          >
            <template #prefix>
              <el-icon class="input-icon">
                <InfoFilled />
              </el-icon>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item required label="密码">
          <el-input
            v-model="registerForm.password"
            :type="showPassword ? 'text' : 'password'"
            size="large"
            placeholder="请输入密码（至少6位）"
          >
            <!-- 前置图标 -->
            <template #prefix>
              <el-icon class="input-icon">
                <Lock />
              </el-icon>
            </template>

            <!-- 后置可点击图标按钮 -->
            <template #suffix>
              <el-button
                type="text"
                class="password-toggle"
                @click.stop="showPassword = !showPassword"
                size="default"
              >
                <el-icon class="icon" v-if="showPassword" size="large">
                  <View />
                </el-icon>
                <el-icon class="icon" v-else size="large">
                  <Hide />
                </el-icon>
              </el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item required label="确认密码">
          <el-input
            v-model="registerForm.confirmPassword"
            :type="showConfirmPassword ? 'text' : 'password'"
            size="large"
            placeholder="请再次输入密码"
          >
            <template #prefix>
              <el-icon class="input-icon">
                <Lock />
              </el-icon>
            </template>

            <template #suffix>
              <el-button
                type="text"
                class="password-toggle"
                @click.stop="showConfirmPassword = !showConfirmPassword"
                size="default"
              >
                <el-icon class="icon" v-if="showConfirmPassword" size="large">
                  <View />
                </el-icon>
                <el-icon class="icon" v-else size="large">
                  <Hide />
                </el-icon>
              </el-button>
            </template>
          </el-input>
        </el-form-item>

        <el-button
          class="register-button"
          :disabled="isLoading"
          size="large"
          @click="handleRegister"
        >
          <span v-if="isLoading" class="loading-spinner"></span>
          <span v-else>注册</span>
        </el-button>

        <div class="register-footer">
          <p class="login-text">
            已有账号？
            <a
              href="#"
              class="login-link"
              @click.prevent="router.push('/login')"
              >立即登录</a
            >
          </p>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Hide, InfoFilled, Lock, User, View } from "@element-plus/icons-vue";

defineOptions({
  name: "Register",
});

// TODO: 表单校验正则化
// TODO: 注册时填写用户名
import { type Ref, ref } from "vue";
import { useRouter } from "vue-router";
import { useDark } from "@vueuse/core";
import { SignAPI } from "../../services/user.ts";
import { ElMessage } from "element-plus";
import type { RegisterFormType } from "../../interface/Tregister.ts";

const isDark = useDark();
const router = useRouter();

const registerForm: Ref<RegisterFormType> = ref({
  name: "",
  email: "",
  password: "",
  confirmPassword: "",
}); // 注册表单数据

const showPassword: Ref<boolean> = ref(false); // 显示/隐藏密码
const showConfirmPassword: Ref<boolean> = ref(false); // 显示/隐藏确认密码
const isLoading: Ref<boolean> = ref(false); // 注册按钮加载状态

// 注册处理函数
const handleRegister = async (): Promise<void> => {
  const { name, email, password, confirmPassword } = registerForm.value;

  // 基本表单验证
  if (!name) {
    ElMessage.warning("请填写用户名称");
    return;
  }
  if (!email) {
    ElMessage.warning("请填写邮箱地址");
    return;
  }
  if (!password) {
    ElMessage.warning("请填写密码");
    return;
  }
  if (!confirmPassword) {
    ElMessage.warning("请填写确认密码");
    return;
  }

  isLoading.value = true;
  try {
    const response = await SignAPI.postRegister(registerForm.value);
    if (response.code === 200) {
      await router.push("/login");
      ElMessage.success("注册成功！");
    } else {
      ElMessage.error(response.msg);
    }
  } catch (error) {
    ElMessage.warning("注册失败，请联系管理员。");
  } finally {
    isLoading.value = false;
  }
};
</script>

<style scoped lang="scss">
.register-container {
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

  .register-card {
    width: 100%;
    max-width: 420px;
    background: rgba(255, 255, 255, 0.95);
    backdrop-filter: blur(10px);
    border-radius: 1.5rem;
    box-shadow: 0 20px 40px rgba(0, 0, 0, 0.1);
    padding: 3rem 2.5rem;
    border: 1px solid rgba(255, 255, 255, 0.2);
  }

  .register-header {
    text-align: center;
    margin-bottom: 2.5rem;

    .register-title {
      font-size: 2rem;
      font-weight: 700;
      color: #1a1a1a;
      margin: 0 0 0.5rem 0;
      background: linear-gradient(135deg, #007cf0, #00d4ff);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      background-clip: text;
    }

    .register-subtitle {
      font-size: 1rem;
      color: #666;
      margin: 0;
      font-weight: 400;
    }
  }

  .register-form {
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
      margin-bottom: 2rem;

      .agree-terms {
        display: flex;
        align-items: flex-start;
        cursor: pointer;

        .checkbox {
          width: 1rem;
          height: 1rem;
          margin-right: 0.5rem;
          margin-top: 0.125rem;
          accent-color: #007cf0;
          flex-shrink: 0;
        }

        .checkbox-text {
          font-size: 0.875rem;
          color: #374151;
          line-height: 1.4;

          .terms-link {
            color: #007cf0;
            text-decoration: none;
            transition: color 0.2s ease;

            &:hover {
              color: #0066cc;
            }
          }
        }
      }
    }

    .register-button {
      margin-top: 15px;
      width: 100%;
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

    .register-footer {
      text-align: center;
      margin-top: 2rem;

      .login-text {
        font-size: 0.875rem;
        color: #666;
        margin: 0;

        .login-link {
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
}

.dark {
  .register-card {
    background: rgba(40, 40, 40, 0.95);
    border-color: rgba(255, 255, 255, 0.1);
    box-shadow: 0 20px 40px rgba(0, 0, 0, 0.3);
  }

  .register-header {
    .register-title {
      color: #fff;
    }

    .register-subtitle {
      color: #9ca3af;
    }
  }

  .register-form {
    .form-group {
      .form-label {
        color: #e5e7eb;
      }

      .input-wrapper {
        .form-input {
          background: rgba(255, 255, 255, 0.05);
          border-color: rgba(255, 255, 255, 0.1);
          color: #fff;

          &:focus {
            background: rgba(255, 255, 255, 0.1);
            border-color: #007cf0;
            box-shadow: 0 0 0 3px rgba(0, 124, 240, 0.2);
          }

          &::placeholder {
            color: #6b7280;
          }
        }

        .input-icon {
          color: #6b7280;
        }

        .password-toggle {
          color: #6b7280;

          &:hover {
            color: #007cf0;
          }
        }
      }
    }

    .form-options {
      .agree-terms {
        .checkbox-text {
          color: #e5e7eb;
        }
      }
    }

    .register-footer {
      .login-text {
        color: #9ca3af;
      }
    }
  }
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 480px) {
  .register-container {
    padding: 1rem;

    .register-card {
      padding: 2rem 1.5rem;
    }

    .register-header {
      .register-title {
        font-size: 1.75rem;
      }
    }
  }
}

:deep(.el-form-item__label) {
  font-size: 15px;
}
</style>
