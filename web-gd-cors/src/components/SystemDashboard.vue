<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from "vue";
import { Monitor, Close } from "@element-plus/icons-vue";
import { systemAPI } from "@/services/file.ts";
import { filesize } from "filesize";

// 响应式断点阈值（单位：px）
const BREAKPOINT = 1700;

// 存储使用情况数据类型定义
interface StorageUsageData {
  totalBytes: string; // 总字节数（字符串格式）
  quotaBytes: string; // 配额字节数（字符串格式）
  usedPercentage: number; // 使用百分比 (0-100)
  usedBytes: Record<string, string>; // 各存储桶使用情况
  zonedDateTime: string; // 时间戳
}

// 存储指标数据
interface StorageMetrics {
  totalBytes: number; // 总字节数
  quotaBytes: number; // 配额字节数
  usedBytes: number; // 已使用字节数
  usedPercentage: number; // 使用百分比
  availableBytes: number; // 可用字节数
  bucketUsage: Array<{ name: string; bytes: number }>; // 各存储桶使用情况
  lastUpdateTime: string; // 最后更新时间
}

// 窗口宽度
const windowWidth = ref<number>(window.innerWidth);

// 是否应该折叠（窗口宽度 <= 断点）
const shouldCollapse = computed(() => windowWidth.value <= BREAKPOINT);

// 是否手动展开（仅在折叠模式下有效）
const isManuallyExpanded = ref<boolean>(false);

// 存储指标数据
const storageMetrics = ref<StorageMetrics>({
  totalBytes: 0,
  quotaBytes: 0,
  usedBytes: 0,
  usedPercentage: 0,
  availableBytes: 0,
  bucketUsage: [],
  lastUpdateTime: "",
});

// 数据刷新间隔（毫秒）- 数据库每1分钟更新一次
const REFRESH_INTERVAL = 60000; // 1分钟 = 60000毫秒
let refreshTimer: number | null = null;
let nextRefreshTimer: number | null = null; // 下次整点刷新的定时器
let isFirstRequest = true; // 是否是第一次请求

// 监听窗口大小变化
const handleResize = () => {
  windowWidth.value = window.innerWidth;
  // 当窗口宽度恢复到大于断点时，自动收起手动展开状态
  if (!shouldCollapse.value) {
    isManuallyExpanded.value = false;
  }
};

// 切换仪表盘展开/收起状态
const toggleDashboard = () => {
  isManuallyExpanded.value = !isManuallyExpanded.value;
};

// 获取存储使用情况数据
const fetchStorageMetrics = async () => {
  try {
    const res = await systemAPI.getStorageUsage();
    if (res.code === 200) {
      const data: StorageUsageData = res.data;

      // 将字符串格式的字节数转换为数字
      const totalBytes = parseInt(data.totalBytes || "0", 10);
      const quotaBytes = parseInt(data.quotaBytes || "0", 10);
      const usedPercentage = data.usedPercentage || 0;

      // 计算已使用字节数（基于百分比）
      const usedBytes = Math.round((quotaBytes * usedPercentage) / 100);

      // 计算可用字节数
      const availableBytes = quotaBytes - usedBytes;

      // 处理各存储桶使用情况
      const bucketUsage: Array<{ name: string; bytes: number }> = [];
      if (data.usedBytes && typeof data.usedBytes === "object") {
        Object.entries(data.usedBytes).forEach(([name, bytesStr]) => {
          bucketUsage.push({
            name,
            bytes: parseInt(bytesStr || "0", 10),
          });
        });
      }

      // 格式化更新时间
      let lastUpdateTime = "";
      let updateDate: Date | null = null;
      if (data.zonedDateTime) {
        try {
          updateDate = new Date(data.zonedDateTime);
          lastUpdateTime = updateDate.toLocaleString("zh-CN", {
            year: "numeric",
            month: "2-digit",
            day: "2-digit",
            hour: "2-digit",
            minute: "2-digit",
            second: "2-digit",
            hour12: false,
          });
        } catch (e) {
          lastUpdateTime = data.zonedDateTime;
        }
      }

      // 更新存储指标数据
      storageMetrics.value = {
        totalBytes,
        quotaBytes,
        usedBytes,
        usedPercentage,
        availableBytes,
        bucketUsage,
        lastUpdateTime,
      };

      // 如果是第一次请求，根据更新时间计算下次刷新时间
      if (isFirstRequest) {
        isFirstRequest = false;
        if (updateDate) {
          scheduleNextRefresh(updateDate);
        } else {
          // 如果没有更新时间，使用默认的1分钟间隔刷新
          refreshTimer = window.setInterval(() => {
            fetchStorageMetrics();
          }, REFRESH_INTERVAL);
        }
      }
    } else {
      console.error("获取存储使用情况失败:", res.msg);
      // 不显示错误消息，避免频繁弹窗
    }
  } catch (error) {
    console.error("获取存储使用情况失败:", error);
    // 不显示错误消息，避免频繁弹窗
  }
};

// 计算下一次整分钟的时间（基于数据库更新时间）
const calculateNextMinute = (updateDate: Date): Date => {
  const nextMinute = new Date(updateDate);
  // 将秒和毫秒清零，然后加1分钟
  nextMinute.setSeconds(0, 0);
  nextMinute.setMinutes(nextMinute.getMinutes() + 1);
  return nextMinute;
};

// 安排下次刷新（基于数据库更新时间）
const scheduleNextRefresh = (updateDate: Date) => {
  // 清除之前的定时器
  if (nextRefreshTimer !== null) {
    clearTimeout(nextRefreshTimer);
    nextRefreshTimer = null;
  }
  if (refreshTimer !== null) {
    clearInterval(refreshTimer);
    refreshTimer = null;
  }

  const nextMinute = calculateNextMinute(updateDate);
  const now = new Date();
  let delay = nextMinute.getTime() - now.getTime();

  // 限制延迟时间在合理范围内（最多2分钟，最少0）
  if (delay > REFRESH_INTERVAL * 2) {
    // 如果延迟超过2分钟，可能是时间同步问题，使用默认1分钟间隔
    console.warn("计算出的刷新延迟过长，使用默认1分钟间隔");
    refreshTimer = window.setInterval(() => {
      fetchStorageMetrics();
    }, REFRESH_INTERVAL);
    return;
  }

  // 如果已经过了整分钟，立即刷新并设置下一分钟
  if (delay <= 0) {
    // 立即刷新一次
    fetchStorageMetrics();
    // 然后设置下一分钟的刷新
    const nextNextMinute = new Date(nextMinute);
    nextNextMinute.setMinutes(nextNextMinute.getMinutes() + 1);
    delay = nextNextMinute.getTime() - now.getTime();
    // 确保延迟在合理范围内
    if (delay > 0 && delay <= REFRESH_INTERVAL * 2) {
      nextRefreshTimer = window.setTimeout(() => {
        fetchStorageMetrics();
        // 之后每1分钟刷新一次
        refreshTimer = window.setInterval(() => {
          fetchStorageMetrics();
        }, REFRESH_INTERVAL);
      }, delay);
    } else {
      // 如果延迟不合理，使用默认1分钟间隔
      refreshTimer = window.setInterval(() => {
        fetchStorageMetrics();
      }, REFRESH_INTERVAL);
    }
  } else {
    // 等待到下一个整分钟
    nextRefreshTimer = window.setTimeout(() => {
      fetchStorageMetrics();
      // 之后每1分钟刷新一次
      refreshTimer = window.setInterval(() => {
        fetchStorageMetrics();
      }, REFRESH_INTERVAL);
    }, delay);
  }
};

// 启动定时刷新
const startRefresh = () => {
  // 立即执行一次（第一次请求会触发 scheduleNextRefresh）
  fetchStorageMetrics();
};

// 停止定时刷新
const stopRefresh = () => {
  if (refreshTimer !== null) {
    clearInterval(refreshTimer);
    refreshTimer = null;
  }
  if (nextRefreshTimer !== null) {
    clearTimeout(nextRefreshTimer);
    nextRefreshTimer = null;
  }
};

// 根据使用率获取进度条颜色类型
const getProgressType = (usage: number): "success" | "warning" | "danger" => {
  if (usage < 60) return "success";
  if (usage < 80) return "warning";
  return "danger";
};

// 格式化百分比显示
const formatPercentage = (value: number): string => {
  return `${value.toFixed(1)}%`;
};

// 格式化字节数显示
const formatBytes = (bytes: number): string => {
  return filesize(bytes, { standard: "jedec" });
};

// 组件挂载时
onMounted(() => {
  // 监听窗口大小变化
  window.addEventListener("resize", handleResize);
  // 启动数据刷新
  startRefresh();
});

// 组件卸载前
onBeforeUnmount(() => {
  // 移除窗口大小监听
  window.removeEventListener("resize", handleResize);
  // 停止数据刷新
  stopRefresh();
});

// 监听折叠状态变化，当从折叠状态恢复时自动刷新数据
watch(shouldCollapse, (newVal) => {
  if (!newVal) {
    // 从折叠状态恢复，刷新数据
    fetchStorageMetrics();
  }
});
</script>

<template>
  <!-- 系统监控仪表盘 -->
  <div class="system-dashboard-wrapper">
    <!-- 按钮/面板容器（统一容器，通过 transition 切换状态） -->
    <div class="dashboard-container">
      <transition name="dashboard-expand" mode="out-in">
        <!-- 按钮状态：圆形按钮 -->
        <div
          v-if="shouldCollapse && !isManuallyExpanded"
          key="button"
          class="dashboard-button"
          @click="toggleDashboard"
        >
          <el-icon class="button-icon"><Monitor /></el-icon>
        </div>

        <!-- 面板状态：展开的卡片 -->
        <el-card v-else key="panel" class="dashboard-panel" shadow="always">
          <template #header>
            <div class="dashboard-header">
              <el-icon class="dashboard-icon"><Monitor /></el-icon>
              <span class="dashboard-title">系统监控</span>
              <!-- 关闭按钮（仅在折叠模式下显示） -->
              <el-button
                v-if="shouldCollapse"
                class="dashboard-close-btn"
                type="text"
                :icon="Close"
                circle
                size="small"
                @click="toggleDashboard"
              />
            </div>
          </template>

          <div class="dashboard-content">
            <!-- 存储使用率 -->
            <div class="metric-item">
              <div class="metric-label">
                <span class="metric-name">存储使用率</span>
                <span class="metric-value">{{
                  formatPercentage(storageMetrics.usedPercentage)
                }}</span>
              </div>
              <el-progress
                :percentage="storageMetrics.usedPercentage"
                :status="getProgressType(storageMetrics.usedPercentage)"
                :stroke-width="12"
                :show-text="false"
              />
            </div>

            <!-- 已用容量 -->
            <div class="metric-item">
              <div class="metric-label">
                <span class="metric-name">已用容量</span>
                <span class="metric-value">{{
                  formatBytes(storageMetrics.usedBytes)
                }}</span>
              </div>
            </div>

            <!-- 总容量 -->
            <div class="metric-item">
              <div class="metric-label">
                <span class="metric-name">总容量</span>
                <span class="metric-value">{{
                  formatBytes(storageMetrics.quotaBytes)
                }}</span>
              </div>
            </div>

            <!-- 可用容量 -->
            <div class="metric-item">
              <div class="metric-label">
                <span class="metric-name">可用容量</span>
                <span class="metric-value">{{
                  formatBytes(storageMetrics.availableBytes)
                }}</span>
              </div>
            </div>

            <!-- 各存储桶使用情况 -->
            <div
              v-if="storageMetrics.bucketUsage.length > 0"
              class="bucket-usage-section"
            >
              <div class="bucket-usage-title">存储桶使用情况</div>
              <div
                v-for="bucket in storageMetrics.bucketUsage"
                :key="bucket.name"
                class="bucket-item"
              >
                <span class="bucket-name">{{ bucket.name }}</span>
                <span class="bucket-size">{{ formatBytes(bucket.bytes) }}</span>
              </div>
            </div>

            <!-- 最后更新时间 -->
            <div v-if="storageMetrics.lastUpdateTime" class="update-time">
              更新时间：{{ storageMetrics.lastUpdateTime }}
            </div>
          </div>
        </el-card>
      </transition>
    </div>
  </div>
</template>

<style scoped lang="scss">
.system-dashboard-wrapper {
  position: fixed;
  bottom: 24px;
  right: 24px;
  z-index: 2000; // 确保层级足够高，但低于弹窗（通常弹窗是 2000+）
  pointer-events: none; // 允许点击穿透

  .dashboard-container {
    pointer-events: auto; // 恢复点击事件
    position: relative;
    display: inline-flex; // 使用 flex 确保对齐
    align-items: flex-end; // 底部对齐
    justify-content: flex-end; // 右对齐
  }

  // 按钮样式（折叠状态）
  .dashboard-button {
    width: 56px;
    height: 56px;
    border-radius: 50%;
    background: var(--el-color-primary);
    color: #fff;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
    position: relative;
    flex-shrink: 0; // 防止收缩

    .button-icon {
      font-size: 24px;
      transition: transform 0.3s ease;
    }

    &:hover {
      transform: scale(1.1);
      box-shadow: 0 6px 16px rgba(0, 0, 0, 0.2);

      .button-icon {
        transform: rotate(90deg);
      }
    }

    &:active {
      transform: scale(0.95);
    }
  }

  // 仪表盘面板样式（展开状态）
  .dashboard-panel {
    width: 360px;
    max-width: calc(100vw - 48px); // 确保在小屏幕上不会超出视口
    border-radius: 12px;
    overflow: hidden;

    :deep(.el-card__header) {
      padding: 16px;
      background: var(--el-color-primary-light-9);
      border-bottom: 1px solid var(--el-border-color-lighter);
    }

    :deep(.el-card__body) {
      padding: 16px;
    }
  }

  .dashboard-header {
    display: flex;
    align-items: center;
    gap: 8px;

    .dashboard-icon {
      font-size: 20px;
      color: var(--el-color-primary);
    }

    .dashboard-title {
      flex: 1;
      font-size: 16px;
      font-weight: 600;
      color: var(--el-text-color-primary);
    }

    .dashboard-close-btn {
      padding: 4px;
      color: var(--el-text-color-secondary);
      transition: all 0.2s ease;

      &:hover {
        color: var(--el-text-color-primary);
        transform: rotate(90deg);
      }
    }
  }

  .dashboard-content {
    min-height: 120px;

    .metric-item {
      margin-bottom: 16px;

      &:last-child {
        margin-bottom: 0;
      }

      .metric-label {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 8px;

        .metric-name {
          font-size: 14px;
          color: var(--el-text-color-regular);
          font-weight: 500;
        }

        .metric-value {
          font-size: 14px;
          color: var(--el-text-color-primary);
          font-weight: 600;
          font-variant-numeric: tabular-nums; // 等宽数字，避免数值跳动
        }
      }

      :deep(.el-progress) {
        .el-progress-bar__outer {
          border-radius: 6px;
          background-color: var(--el-fill-color-light);
        }

        .el-progress-bar__inner {
          border-radius: 6px;
          transition: width 0.3s ease;
        }
      }
    }

    .bucket-usage-section {
      margin-top: 20px;
      padding-top: 16px;
      border-top: 1px solid var(--el-border-color-lighter);

      .bucket-usage-title {
        font-size: 13px;
        color: var(--el-text-color-secondary);
        font-weight: 600;
        margin-bottom: 12px;
      }

      .bucket-item {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 6px 0;
        font-size: 13px;

        .bucket-name {
          color: var(--el-text-color-regular);
          flex: 1;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
          margin-right: 8px;
        }

        .bucket-size {
          color: var(--el-text-color-primary);
          font-weight: 500;
          font-variant-numeric: tabular-nums;
          flex-shrink: 0;
        }
      }
    }

    .update-time {
      margin-top: 16px;
      padding-top: 12px;
      border-top: 1px solid var(--el-border-color-lighter);
      font-size: 12px;
      color: var(--el-text-color-placeholder);
      text-align: center;
    }
  }
}

// 展开动画：按钮展开成面板
.dashboard-expand-enter-active {
  transition: all 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
  transform-origin: bottom right; // 从右下角展开（按钮位置）
}

.dashboard-expand-leave-active {
  transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1);
  transform-origin: bottom right; // 从右下角收缩（按钮位置）
}

// 按钮展开成面板
.dashboard-expand-enter-from {
  opacity: 0;
  transform: scale(0.2);
  border-radius: 50%;
}

.dashboard-expand-enter-to {
  opacity: 1;
  transform: scale(1);
  border-radius: 12px;
}

// 面板收缩成按钮
.dashboard-expand-leave-from {
  opacity: 1;
  transform: scale(1);
  border-radius: 12px;
}

.dashboard-expand-leave-to {
  opacity: 0;
  transform: scale(0.2);
  border-radius: 50%;
}

// 响应式调整：在小屏幕上调整位置和大小
@media (max-width: 768px) {
  .system-dashboard-wrapper {
    bottom: 16px;
    right: 16px;

    .dashboard-button {
      width: 48px;
      height: 48px;

      .button-icon {
        font-size: 20px;
      }
    }

    .dashboard-panel {
      width: calc(100vw - 32px);
      max-width: 360px;
    }
  }
}
</style>
