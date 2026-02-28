import axios, {
  type AxiosError,
  type AxiosInstance,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
  type AxiosResponse,
} from "axios";
import { useUserStore } from "../stores/user";
import { ElMessage } from "element-plus";
import type { ResType } from "../interface/Tgeneral";

const BASE_URL = "/api/v1";

// Token 刷新状态管理
let isRefreshing = false;
let refreshSubscribers: Array<(token: string) => void> = [];

// 订阅 token 刷新
const subscribeTokenRefresh = (callback: (token: string) => void) => {
  refreshSubscribers.push(callback);
};

// 通知所有订阅者 token 已刷新
const onTokenRefreshed = (token: string) => {
  refreshSubscribers.forEach((callback) => callback(token));
  refreshSubscribers = [];
};

// 添加 token
const addAuthHeader = (
  config: InternalAxiosRequestConfig,
): InternalAxiosRequestConfig => {
  const { token } = useUserStore();
  if (token) config.headers.Authorization = token;
  
  // 如果是 FormData，删除 Content-Type，让浏览器自动设置 multipart/form-data
  if (config.data instanceof FormData) {
    delete config.headers["Content-Type"];
  }
  
  return config;
};

// 统一错误处理
const handleResponseError = (error: AxiosError | Error) => {
  // 主动取消的请求不提示错误
  if (axios.isCancel(error)) return Promise.reject(error);

  const response = (error as AxiosError).response;

  if (response && response.status !== 200)
    ElMessage.warning("响应失败！请联系管理员");

  return Promise.reject(error);
};

// ==================== Axios 实例创建 ====================

/**
 * 主实例：用于所有 API 请求（包括文件上传/下载）
 * - 不携带 cookie（只有 refresh 接口需要）
 * - 自动添加 Authorization header
 * - 自动处理 401 并刷新 token
 * - 对于 blob/arraybuffer 返回完整 AxiosResponse，其他返回 res.data
 */
const mainAxiosInstance: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  headers: {
    "Content-Type": "application/json",
  },
});

/**
 * Refresh 专用实例：用于刷新 token
 * - 携带 cookie（refresh token 在 cookie 中）
 * - 不添加 Authorization header（token 已过期）
 * - 不经过响应拦截器（避免循环调用）
 */
const refreshAxiosInstance: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  withCredentials: true,
  headers: {
    "Content-Type": "application/json",
  },
});

// ==================== 拦截器配置 ====================

// 主实例：请求拦截器
mainAxiosInstance.interceptors.request.use(addAuthHeader, handleResponseError);

// 主实例：响应拦截器（处理 401、token 刷新和 blob 响应）
mainAxiosInstance.interceptors.response.use(async (res: AxiosResponse) => {
  // 对于 blob/arraybuffer，直接返回完整响应（用于文件下载）
  if (
    res.config.responseType === "blob" ||
    res.config.responseType === "arraybuffer"
  ) {
    return res;
  }

  // 检测业务码 401，表示 token 过期
  if (res.data.code === 401) {
    const originalRequest = res.config;

    // 如果正在刷新 token，将请求加入队列
    if (isRefreshing) {
      return new Promise((resolve) => {
        subscribeTokenRefresh((newToken: string) => {
          // 更新请求头中的 token
          originalRequest.headers.Authorization = newToken;
          // 重试原始请求
          resolve(mainAxiosInstance(originalRequest));
        });
      });
    }

    // 开始刷新 token
    isRefreshing = true;

    try {
      // 调用 refresh 接口（使用 refreshAxiosInstance，避免经过响应拦截器造成循环）
      const refreshResponse =
        await refreshAxiosInstance.post<ResType>(`/user/auth/refresh`);

      if (refreshResponse.data.code === 200) {
        const newToken = refreshResponse.data.data;

        // 更新 store 中的 token
        useUserStore().setToken(newToken);

        // 更新原始请求的 token
        originalRequest.headers.Authorization = newToken;

        // 通知所有等待的请求
        onTokenRefreshed(newToken);

        // 重试原始请求
        return mainAxiosInstance(originalRequest);
      } else {
        // refresh 失败，清除 token 并跳转登录
        useUserStore().clearToken();
        ElMessage.error("登录已过期，请重新登录");
        window.location.href = "/login";
        return Promise.reject(new Error("Token refresh failed"));
      }
    } catch (error) {
      // refresh 请求失败
      useUserStore().clearToken();
      ElMessage.error("登录已过期，请重新登录");
      window.location.href = "/login";
      return Promise.reject(error);
    } finally {
      isRefreshing = false;
    }
  }

  // 其他情况返回 res.data
  return res.data;
}, handleResponseError);

// Refresh 实例：不需要拦截器（避免循环调用）

// ==================== 封装客户端 ====================

interface RestClient {
  get(url: string, config?: AxiosRequestConfig): Promise<ResType>;
  post(
    url: string,
    data?: unknown,
    config?: AxiosRequestConfig,
  ): Promise<ResType>;
  put(
    url: string,
    data?: unknown,
    config?: AxiosRequestConfig,
  ): Promise<ResType>;
  patch(
    url: string,
    data?: unknown,
    config?: AxiosRequestConfig,
  ): Promise<ResType>;
  delete(url: string, config?: AxiosRequestConfig): Promise<ResType>;
}

/**
 * 标准 API 客户端
 * 使用场景：所有业务 API 调用（包括文件上传/下载）
 * - 对于 blob 请求，返回完整的 AxiosResponse<Blob>（需要类型断言）
 * - 对于其他请求，返回 Promise<ResType>
 */
export const apiClient: RestClient = {
  get(url, config) {
    return mainAxiosInstance.get(url, config) as Promise<ResType>;
  },
  post(url, data, config) {
    return mainAxiosInstance.post(url, data, config) as Promise<ResType>;
  },
  put(url, data, config) {
    return mainAxiosInstance.put(url, data, config) as Promise<ResType>;
  },
  patch(url, data, config) {
    return mainAxiosInstance.patch(url, data, config) as Promise<ResType>;
  },
  delete(url, config) {
    return mainAxiosInstance.delete(url, config) as Promise<ResType>;
  },
};

// 导出 mainAxiosInstance，用于需要完整 AxiosResponse 的场景（如文件下载）
export { mainAxiosInstance };
