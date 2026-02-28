<template>
  <div class="home" :class="{ dark: isDark }">
    <div class="container">
      <h1 class="title">
        <span :class="{ visible: showGreeting }"
          >{{ time }}好，{{ username }}！</span
        >
      </h1>
      <div class="cards-grid">
        <router-link
          v-for="app in aiApps"
          :key="app.id"
          :to="app.route"
          class="card"
        >
          <div class="card-content">
            <component :is="app.icon" class="icon" />
            <h2>{{ app.title }}</h2>
            <p>{{ app.description }}</p>
          </div>
        </router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: "Home",
});

import { ref, computed, onMounted } from "vue";
import { useDark } from "@vueuse/core";
import {
  ChatBubbleLeftRightIcon,
  DocumentTextIcon,
  TableCellsIcon,
} from "@heroicons/vue/24/outline";
import { UseAPI } from "../../services/user.ts";
import type { HomeCardType } from "@/interface/Thome.ts";
const isDark = useDark();

// 根据当前时间生成问候语
const time = computed(() => {
  const hour = new Date().getHours();
  if (hour >= 5 && hour < 12) return "早上";
  if (hour >= 12 && hour < 18) return "下午";
  return "晚上";
});

const aiApps = ref<HomeCardType[]>([
  {
    id: 1,
    title: "业务咨询",
    description: "咨询卫星应用中心的各类问题",
    route: "/ai-chat",
    icon: ChatBubbleLeftRightIcon,
  },
  {
    id: 2,
    title: "文件系统",
    description: "更新和管理知识库文档",
    route: "/file",
    icon: DocumentTextIcon,
  },
  {
    id: 3,
    title: "向量状态",
    description: "查看知识库文件的向量化状态",
    route: "/vector-status",
    icon: TableCellsIcon,
  },
]);

// 用户相关状态
const username = ref<string>("未知用户"); // 用户名
const showGreeting = ref<boolean>(false); // 是否显示问候语
// 获取当前用户信息
const loadUserInfo = async (): Promise<void> => {
  try {
    const res = await UseAPI.getUserInfo();
    if (res.code === 200) {
      username.value = res.data.name || "未知用户";
      showGreeting.value = true;
    } else {
      showGreeting.value = false;
    }
  } catch (error) {
    console.error("获取用户信息失败:", error);
    showGreeting.value = false;
  }
};

// 挂载时加载用户信息
onMounted(loadUserInfo);
</script>

<style scoped lang="scss">
.home {
  flex: 1;
  padding: 2rem;
  background: var(--bg-color);
  transition: background-color 0.3s;

  .container {
    max-width: 1600px;
    margin: 0 auto;
    padding: 0 2rem;
  }

  .title {
    text-align: center;
    font-size: 2.5rem;
    margin-bottom: 3rem;
    margin-top: 20px;
    background: linear-gradient(45deg, #007cf0, #00dfd8);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    min-height: 1.2em; // 保持占位高度

    span {
      display: inline-block;
      opacity: 0;

      &.visible {
        animation: fadeIn 1s ease-out forwards;
      }
    }
  }

  .cards-grid {
    display: grid;
    grid-template-columns: repeat(1, 1fr);
    gap: 16px;
    padding: 1rem;

    max-width: 75%;
    margin: 0 auto;

    @media (min-width: 768px) {
      grid-template-columns: repeat(3, 1fr);
    }
  }

  .card {
    position: relative;
    width: 100%;
    max-width: 320px;
    background: rgba(255, 255, 255, 0.8);
    backdrop-filter: blur(10px);
    border-radius: 20px;
    padding: 2rem;
    text-decoration: none;
    color: inherit;
    transition: all 0.3s ease;
    border: 1px solid rgba(255, 255, 255, 0.1);
    overflow: hidden;

    .dark & {
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid rgba(255, 255, 255, 0.05);
    }

    &:hover {
      transform: translateY(-5px);
      box-shadow: 0 10px 20px rgba(0, 0, 0, 0.1);

      .dark & {
        box-shadow: 0 10px 20px rgba(0, 0, 0, 0.3);
      }
    }

    .card-content {
      display: flex;
      flex-direction: column;
      align-items: center;
      text-align: center;
    }

    .icon {
      width: 48px;
      height: 48px;
      margin-bottom: 1rem;
      color: #007cf0;

      &.heart-icon {
        color: #ff4d4f;
        animation: pulse 1.5s ease-in-out infinite;
      }
    }

    h2 {
      font-size: 1.5rem;
      margin-bottom: 0.5rem;
    }

    p {
      color: #666;
      font-size: 1rem;

      .dark & {
        color: #999;
      }
    }
  }

  &.dark {
    background: #1a1a1a;

    .card {
      background: rgba(255, 255, 255, 0.05);

      p {
        color: #999;
      }
    }
  }
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes pulse {
  0% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.1);
  }
  100% {
    transform: scale(1);
  }
}

@media (max-width: 768px) {
  .home {
    padding: 1rem;

    .container {
      padding: 0 1rem;
    }

    .title {
      font-size: 2rem;
    }

    .card {
      max-width: 100%;
    }
  }
}
</style>
