import { useUserStore } from "../stores/user";
import { fetchEventSource } from "@microsoft/fetch-event-source";
import { apiClient } from "./client.ts";
import axios from "axios";
import { ElMessage } from "element-plus";

const SESSIONS_API_BASE_URL = "/sessions";
const BASE_URL = "/api/v1";
const BASE = window.location.origin;

const userStore = useUserStore();

// SSE Token 刷新状态管理
let isSseRefreshing = false;
let sseRefreshPromise = null;

// SSE Token 刷新函数
async function refreshTokenForSSE() {
  // 如果正在刷新，返回现有的 Promise
  if (isSseRefreshing && sseRefreshPromise) {
    return sseRefreshPromise;
  }

  isSseRefreshing = true;
  sseRefreshPromise = (async () => {
    try {
      // 与 Axios 顶层封装保持一致：
      // - 不携带 Authorization（使用 cookie 中的 refresh token）
      // - 开启 withCredentials 以发送 cookie
      // - 后端返回的数据结构：data.data 为新的 accessToken 字符串
      const refreshResponse = await axios.post(
        `${BASE_URL}/user/auth/refresh`,
        {},
        {
          withCredentials: true,
        },
      );

      if (refreshResponse.data.code === 200 && refreshResponse.data.data) {
        const newToken = refreshResponse.data.data;
        userStore.setToken(newToken);
        return newToken;
      } else {
        throw new Error("Token refresh failed");
      }
    } catch (error) {
      userStore.clearToken();
      ElMessage.error("登录已过期，请重新登录");
      window.location.href = "/login";
      throw error;
    } finally {
      isSseRefreshing = false;
      sseRefreshPromise = null;
    }
  })();

  return sseRefreshPromise;
}

export const chatAPI = {
  // 发送信息
  postMessage({ message, sessionId, mode }) {
    return apiClient.post(`${SESSIONS_API_BASE_URL}/messages`, {
      model: mode,
      message: message,
      sessionId: sessionId,
    });
  },

  // 订阅 SSE 流
  subscribeChatStream({
    sessionId,
    onChunk,
    onFinish,
    onError,
    onId,
    lastChunkId,
  }) {
    const sseUrl = new URL(
      `/api/v1${SESSIONS_API_BASE_URL}/${sessionId}/sse`,
      BASE,
    );

    let controller = new AbortController();
    let hasRetried = false; // 防止无限重试

    const startSSE = (token) => {
      return fetchEventSource(sseUrl, {
        method: "GET",
        headers: {
          Authorization: token,
          "Last-Chunk-ID": lastChunkId,
        },
        openWhenHidden: true,
        signal: controller.signal,

        // 处理接收到的消息
        onmessage(event) {
          if (!event.data) return;
          // 返回 chunk 及其 id
          onId(event.id);
          onChunk && onChunk(event.data);
        },

        // 连接关闭时的回调
        onclose() {
          onFinish && onFinish();
        },

        // 错误处理
        async onerror(err) {
          // 检查是否是 401 错误（token 过期）
          if (err instanceof Response && err.status === 401 && !hasRetried) {
            hasRetried = true;
            console.log("[SSE] Token 过期，尝试刷新...");

            try {
              // 刷新 token
              const newToken = await refreshTokenForSSE();
              
              // 取消旧连接
              controller.abort();
              
              // 创建新的 AbortController
              controller = new AbortController();
              
              // 使用新 token 重新建立连接
              console.log("[SSE] Token 刷新成功，重新连接...");
              return startSSE(newToken);
            } catch (refreshError) {
              console.error("[SSE] Token 刷新失败", refreshError);
              onError && onError(refreshError);
              throw refreshError;
            }
          } else {
            // 其他错误或已经重试过，直接抛出
            onError && onError(err);
            throw err;
          }
        },
      });
    };

    const finished = startSSE(userStore.token);

    return {
      cancel: () => controller.abort(),
      finished,
    };
  },

  // 手动停止对话
  postStopMessage(sessionId) {
    return apiClient.post(`${SESSIONS_API_BASE_URL}/${sessionId}/stop`);
  },

  // 获取聊天历史列表
  getChatHistory() {
    // 添加类型参数
    return apiClient.get(`${SESSIONS_API_BASE_URL}`);
  },

  // 创建新的聊天会话
  postCreateSession(title = "新对话") {
    return apiClient.post(`${SESSIONS_API_BASE_URL}`, {
      title: title,
    });
  },

  // 重命名聊天会话
  patchRenameSession(chatId, name) {
    return apiClient.patch(`${SESSIONS_API_BASE_URL}/${chatId}`, {
      title: name,
    });
  },

  // 删除聊天会话
  deleteDeleteSession(chatId) {
    return apiClient.delete(`${SESSIONS_API_BASE_URL}/${chatId}`);
  },

  // 分页获取对话聊天记录
  getChatMessagesByPage(chatId, pageNum = 1, pageSize = 10, signal = null) {
    return apiClient.get(`/messages`, {
      params: {
        sessionId: chatId,
        pageNum: pageNum,
        pageSize: pageSize,
      },
      signal: signal,
    });
  },
};
