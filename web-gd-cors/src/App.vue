<script setup lang="ts">
// TODO：普通用户显示个人中心按钮
import { RouterLink, RouterView } from "vue-router";
import { useDark, useToggle } from "@vueuse/core";
import { SunIcon, MoonIcon } from "@heroicons/vue/24/outline";
import { useRouter } from "vue-router";
import { ref, computed, onMounted, watch } from "vue";
import { User, SwitchButton, HomeFilled } from "@element-plus/icons-vue";
import { useUserStore } from "./stores/user";
import { UseAPI, SignAPI } from "./services/user";
import { ElMessage } from "element-plus";
import axios from "axios";

const isDark = useDark();
const toggleDark = useToggle(isDark);
const router = useRouter();
const userStore = useUserStore();

// 添加全局状态来跟踪当前路由
const currentRoute = ref(router.currentRoute.value.path);

// 登录状态管理 - 基于 token 存在性
const isLoggedIn = computed(() => {
  return !!userStore.token;
});

// 是否显示导航栏（404 页面不显示）
const showNavbar = computed(() => {
  // 检查路由名称是否为 NotFound
  return router.currentRoute.value.name !== "NotFound";
});

// 是否显示返回首页按钮（首页/登录/注册页不显示）
const showBack = computed(() => {
  const hidden = ["/home", "/login", "/register"];
  return !hidden.includes(currentRoute.value);
});

// 是否显示管理员按钮
const isAdmin = computed(() => {
  return userStore.role === "ADMIN";
});

const handleGoHome = () => {
  router.push("/home");
};

// 初始化用户信息（仅在应用启动时调用一次）
const initUserInfo = async () => {
  const token = userStore.token;
  if (!token) return;

  try {
    const res = await UseAPI.getUserInfo();
    if (res.code === 200 && res.data?.role) {
      userStore.setRole(res.data.role);
    } else {
      // token 无效，清除
      userStore.clearToken();
    }
  } catch (error) {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      userStore.clearToken();
    }
  }
};

// 退出登录
const handleLogout = async () => {
  await SignAPI.postLogout();
  userStore.clearToken();
  ElMessage.success("已退出登录");
  // 重定向到登录页面
  await router.push("/login");
};

// 是否显示退出登录按钮（登录和注册页面不显示）
const showLogOutButton = computed(() => {
  return (
    isLoggedIn.value &&
    currentRoute.value !== "/login" &&
    currentRoute.value !== "/register"
  );
});

// 跳转到管理员页
const handleGoAdmin = () => {
  router.push("/root");
};

// 跳转到个人中心
const handleGoUser = () => {
  router.push("/user");
};

// 非管理员显示个人中心按钮
const showUserButton = computed(() => {
  return showLogOutButton.value && !isAdmin.value;
});

// 组件挂载时初始化用户信息
onMounted(() => {
  initUserInfo();
});

// 监听路由变化更新 currentRoute
watch(
  () => router.currentRoute.value.path,
  (newPath) => {
    currentRoute.value = newPath;
  },
  { immediate: true },
);
</script>

<template>
  <div class="app" :class="{ dark: isDark }">
    <nav class="navbar" v-if="showNavbar">
      <div class="navbar-left">
        <router-link to="/home" class="logo">CORS智能助手</router-link>
        <el-button
          plain
          style="margin-left: 10px"
          type="primary"
          size="default"
          v-if="showBack"
          @click="handleGoHome"
          title="返回首页"
        >
          <el-icon style="vertical-align: middle">
            <HomeFilled />
          </el-icon>
        </el-button>
      </div>
      <div class="navbar-actions">
        <button @click="toggleDark()" class="theme-toggle">
          <SunIcon v-if="isDark" class="icon" />
          <MoonIcon v-else class="icon" />
        </button>
        <el-button
          plain
          type="primary"
          size="large"
          v-if="showLogOutButton && isAdmin"
          @click="handleGoAdmin"
          title="管理员"
        >
          <el-icon style="vertical-align: middle">
            <User />
          </el-icon>
          管理员中心
        </el-button>
        <!--        <el-button-->
        <!--          plain-->
        <!--          type="success"-->
        <!--          size="large"-->
        <!--          v-if="showUserButton"-->
        <!--          @click="handleGoUser"-->
        <!--          title="个人中心"-->
        <!--        >-->
        <!--          <el-icon style="vertical-align: middle">-->
        <!--            <User />-->
        <!--          </el-icon>-->
        <!--          个人中心-->
        <!--        </el-button>-->
        <el-button
          plain
          type="danger"
          size="large"
          v-if="showLogOutButton"
          @click="handleLogout"
          title="退出登录"
        >
          <el-icon style="vertical-align: middle">
            <SwitchButton />
          </el-icon>
          退出登录
        </el-button>
      </div>
    </nav>
    <div class="router-view-wrapper">
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </div>
  </div>
</template>

<style lang="scss">
:root {
  --bg-color: #f5f5f5;
  --text-color: #333;
}

.dark {
  --bg-color: #1a1a1a;
  --text-color: #fff;
}

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html,
body {
  height: 100%;
}

body {
  font-family:
    -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen, Ubuntu,
    Cantarell, "Open Sans", "Helvetica Neue", sans-serif;
  color: var(--text-color);
  background: var(--bg-color);
  min-height: 100vh;
}

.app {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

// 让 router-view 占据剩余空间
.router-view-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0; // 重要：允许 flex 子元素收缩
  overflow: hidden; // 防止内容溢出
}

.navbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 2rem;
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(10px);
  position: sticky;
  top: 0;
  z-index: 100;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);

  .logo {
    font-size: 1.5rem;
    font-weight: bold;
    text-decoration: none;
    color: inherit;
    background: #007cf0;
    background-clip: text;
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
  }

  .navbar-left {
    display: flex;
    align-items: center;
    gap: 0.5rem;
  }

  .navbar-actions {
    display: flex;
    align-items: center;
    gap: 0.1rem;
  }

  .theme-toggle {
    background: none;
    border: none;
    cursor: pointer;
    padding: 0.5rem;
    border-radius: 50%;
    transition: background-color 0.3s;
    margin-right: 0.75rem;

    &:hover {
      background: rgba(255, 255, 255, 0.1);
    }

    .icon {
      width: 24px;
      height: 24px;
      color: var(--text-color);
    }
  }

  .admin-button {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    background: rgba(59, 130, 246, 0.12);
    border: 1px solid rgba(59, 130, 246, 0.25);
    color: #2563eb;
    padding: 0.5rem 1rem;
    border-radius: 0.5rem;
    cursor: pointer;
    font-size: 0.875rem;
    font-weight: 500;
    transition: all 0.3s ease;

    &:hover {
      background: rgba(59, 130, 246, 0.2);
      border-color: rgba(59, 130, 246, 0.35);
      transform: translateY(-1px);
    }

    .admin-text {
      @media (max-width: 768px) {
        display: none;
      }
    }
  }

  .logout-button {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    background: rgba(220, 38, 38, 0.1);
    border: 1px solid rgba(220, 38, 38, 0.2);
    color: #dc2626;
    padding: 0.5rem 1rem;
    border-radius: 0.5rem;
    cursor: pointer;
    font-size: 0.875rem;
    font-weight: 500;
    transition: all 0.3s ease;

    &:hover {
      background: rgba(220, 38, 38, 0.2);
      border-color: rgba(220, 38, 38, 0.3);
      transform: translateY(-1px);
    }

    .icon {
      width: 18px;
      height: 18px;
    }

    .logout-text {
      @media (max-width: 768px) {
        display: none;
      }
    }
  }

  .dark & {
    background: rgba(0, 0, 0, 0.2);
    border-bottom: 1px solid rgba(255, 255, 255, 0.05);

    .logout-button {
      background: rgba(220, 38, 38, 0.15);
      border-color: rgba(220, 38, 38, 0.3);
      color: #f87171;

      &:hover {
        background: rgba(220, 38, 38, 0.25);
        border-color: rgba(220, 38, 38, 0.4);
      }
    }

    .admin-button {
      background: rgba(59, 130, 246, 0.18);
      border-color: rgba(59, 130, 246, 0.35);
      color: #60a5fa;

      &:hover {
        background: rgba(59, 130, 246, 0.26);
        border-color: rgba(59, 130, 246, 0.45);
      }
    }
  }
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

@media (max-width: 768px) {
  .navbar {
    padding: 1rem;

    .navbar-actions {
      gap: 0.25rem;
    }

    .logout-button {
      padding: 0.5rem;

      .logout-text {
        display: none;
      }
    }
    .admin-button {
      padding: 0.5rem;
    }
  }
}
</style>
