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

// 底层 Axios 实例
const axiosInstance: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  headers: {
    "Content-Type": "application/json",
  },
});

// 原始客户端
const rawAxiosInstance: AxiosInstance = axios.create({
  baseURL: BASE_URL,
});

// 请求拦截器
axiosInstance.interceptors.request.use(addAuthHeader, handleResponseError);
rawAxiosInstance.interceptors.request.use(addAuthHeader, handleResponseError);

// 响应拦截器：返回 res.data
axiosInstance.interceptors.response.use(async (res: AxiosResponse) => {
  console.log(res);

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
          resolve(axiosInstance(originalRequest));
        });
      });
    }

    // 开始刷新 token
    isRefreshing = true;

    try {
      // 调用 refresh 接口
      const refreshResponse = await axios.post<ResType>(
        `${BASE_URL}/user/auth/refresh`,
      );

      if (refreshResponse.data.code === 200) {
        const newToken = refreshResponse.data.data;

        // 更新 store 中的 token
        useUserStore().setToken(newToken);

        // 更新原始请求的 token
        originalRequest.headers.Authorization = newToken;

        // 通知所有等待的请求
        onTokenRefreshed(newToken);

        // 重试原始请求
        return axiosInstance(originalRequest);
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

  return res.data;
}, handleResponseError);
rawAxiosInstance.interceptors.response.use((res: AxiosResponse) => {
  if (
    res.config.responseType === "blob" ||
    res.config.responseType === "arraybuffer"
  )
    return res;

  return res.data;
}, handleResponseError);

// 定义统一返回 ResType 的客户端接口
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

// 封装后的 apiClient，所有方法返回 Promise<ResType>
export const apiClient: RestClient = {
  get(url, config) {
    return axiosInstance.get(url, config) as Promise<ResType>;
  },
  post(url, data, config) {
    return axiosInstance.post(url, data, config) as Promise<ResType>;
  },
  put(url, data, config) {
    return axiosInstance.put(url, data, config) as Promise<ResType>;
  },
  patch(url, data, config) {
    return axiosInstance.patch(url, data, config) as Promise<ResType>;
  },
  delete(url, config) {
    return axiosInstance.delete(url, config) as Promise<ResType>;
  },
};

// 原始客户端（如果需要拿到完整 AxiosResponse，可以用这个）
export const rawApiClient: RestClient = {
  get(url, config) {
    return rawAxiosInstance.get(url, config) as Promise<ResType>;
  },
  post(url, data, config) {
    return rawAxiosInstance.post(url, data, config) as Promise<ResType>;
  },
  put(url, data, config) {
    return rawAxiosInstance.put(url, data, config) as Promise<ResType>;
  },
  patch(url, data, config) {
    return rawAxiosInstance.patch(url, data, config) as Promise<ResType>;
  },
  delete(url, config) {
    return rawAxiosInstance.delete(url, config) as Promise<ResType>;
  },
};
