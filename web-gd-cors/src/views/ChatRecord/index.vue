<script setup>
// TODO：UI界面优化
// TODO：切换对话的时候停止按钮还是显示停止，排查bug
// TODO：刷新记忆是在线还是本地
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from "vue";
import { Position, Close } from "@element-plus/icons-vue";
import ChatMessage from "../../components/ChatMessage.vue";
import { chatAPI } from "../../services/sessions.js";
import IconStop from "../../components/icons/IconStop.vue";
import { ElMessage } from "element-plus";
import { ChatMode } from "../../interface/Tchat.ts";
import { messageCache } from "../../utils/UtilMessageCacheDB.ts";
import { useSkeleton } from "../../utils/UtilShowSkeleton.ts";

defineOptions({
  name: "Index",
});

const props = defineProps({
  chatId: {
    type: String,
    default: null,
  },
  isNewChat: {
    type: Boolean,
    default: false,
  },
});

// 事件：会话创建成功/会话被选中
const emit = defineEmits(["chat-created"]);

// 本地 UI 与分页状态
const messagesRef = ref(null); // 消息列表容器，用于滚动控制和无限加载
const topSentinelRef = ref(null); // 顶部哨兵元素，用于 Intersection Observer 检测
const bottomSentinelRef = ref(null); // 底部哨兵元素，用于检测是否接近底部
const textareaRef = ref(null); // textarea 元素引用，用于自动调整高度
const userInput = ref(""); // 文本输入框绑定的用户输入
const isStreaming = ref(false); // 当前是否在流式输出中，控制按钮禁用等
const isWaitingResponse = ref(false); // 是否正在等待后端响应（发送消息后到收到第一个chunk之间）
const currentMessages = ref([]); // 当前会话下展示的消息数组
const autoScrollEnabled = ref(true); // 是否允许自动滚动到底部（用户不阅读历史时）
const pageNum = ref(1); // 当前分页页码，用于向后端请求更多历史
const pageSize = ref(10); // 每页条数
const total = ref(0); // 当前会话消息总数
const loadingMore = ref(false); // 是否正在加载上一页历史，避免重复请求
const hasMore = ref(true); // 是否还有更多历史可加载
let skeletonTimer = null; // 骨架屏延迟显示定时器
let typingBuffer = ref(""); // 打字机效果缓冲区，保存尚未输出到界面的内容
let lastReceivedChunkId = null; // 最后收到的 SSE chunk ID，用于断点续传
const activeAssistantMessage = ref(null); // 正在流式输出的 AI 消息对象
// 是否显示骨架屏
const { skeletonRef: showSkeleton, runWithSkeleton } = useSkeleton();
let topObserver = null; // 顶部 Intersection Observer 实例
let bottomObserver = null; // 底部 Intersection Observer 实例

const isWaitingForChunk = computed(() => isWaitingResponse.value); // 是否在等待响应（只在发送消息后到收到第一个chunk之前显示省略号）

// 会话提升状态
const isPromotingFromLocal = ref(false); // 是否正在从本地临时会话提升到真实会话
let loadChatAbortController = null; // 用于取消旧的 loadChat 请求
let sessionSseHandle = null; // 当前会话的SSE连接句柄，用于监听实时消息

// 模式（本地/在线）（false表示本地，true表示在线）
const mode = ref(false);

/*根据缓冲区长度动态计算每帧输出的字符数
使用分级策略：缓冲区越长，每帧输出越多
- bufferLength ≤ 50：每帧 1 字符（平滑效果）
- bufferLength ≤ 100：每帧 2 字符
- bufferLength ≤ 200：每帧 4 字符
- bufferLength ≤ 400：每帧 8 字符
- bufferLength > 400：每帧 16 字符（快速追赶）*/
function computeCharsPerFrame(bufferLength) {
  if (bufferLength <= 50) return 1;
  if (bufferLength <= 100) return 2;
  if (bufferLength <= 200) return 4;
  if (bufferLength <= 400) return 8;
  return 16;
}

// 启动打字机渲染（使用 RAF）
let rafId = null; // RAF 句柄

function startTypingLoop() {
  if (rafId !== null) return;

  const tick = () => {
    if (!typingBuffer.value.length) {
      // 仍在流式，则保持轮询等待新 chunk
      if (isStreaming.value) {
        rafId = requestAnimationFrame(tick);
        return;
      }
      rafId = null;
      activeAssistantMessage.value = null;
      return;
    }

    // 根据缓冲区长度计算本帧输出字符数
    const charsToOutput = computeCharsPerFrame(typingBuffer.value.length);
    const outputChars = typingBuffer.value.slice(0, charsToOutput);
    typingBuffer.value = typingBuffer.value.slice(charsToOutput);

    const msg = activeAssistantMessage.value;
    if (msg) msg.content += outputChars;

    nextTick(() => scrollToBottom());

    rafId = requestAnimationFrame(tick);
  };

  rafId = requestAnimationFrame(tick);
}

// 重置分页状态
function resetPagination() {
  pageNum.value = 1;
  total.value = 0;
  hasMore.value = true;
}

// 是否正在加载当前会话的第一页
const isInitialLoading = ref(false);

// 发送信息时的sse
function NewSSE(sessionId) {
  const newHandle = chatAPI.subscribeChatStream({
    sessionId: sessionId,
    lastChunkId: null,
    onId(id) {
      lastReceivedChunkId = id;
    },
    onChunk(rawData) {
      // 收到第一个chunk，清除等待状态
      isWaitingResponse.value = false;

      let chunk = JSON.parse(rawData);

      if (typeof chunk !== "string") chunk = String(chunk);
      chunk = chunk.replace(/\r\n/g, "\n");
      chunk = chunk.replace(/^data:\s?/gm, "");

      if (!activeAssistantMessage.value) {
        currentMessages.value.push({
          senderType: "AI",
          content: "",
          stopped: false,
        });
        activeAssistantMessage.value =
          currentMessages.value[currentMessages.value.length - 1];
        isStreaming.value = true;
      }

      typingBuffer.value += chunk;

      startTypingLoop();
    },
    onFinish() {
      // 只有当这个连接确实是当前激活的连接时，才清理全局句柄
      if (sessionSseHandle === newHandle) sessionSseHandle = null;

      // 关闭流状态
      isStreaming.value = false;
      isWaitingResponse.value = false;
      // 流式结束，清除本地缓存
      messageCache.remove(sessionId);
    },
    onError(err) {
      console.error("[SSE] onError 连接出错", err);
      if (sessionSseHandle === newHandle) {
        sessionSseHandle = null;
      }
      isStreaming.value = false;
      isWaitingResponse.value = false;
      // 发生错误时也清除缓存
      messageCache.remove(sessionId);
      if (rafId !== null) {
        cancelAnimationFrame(rafId);
        rafId = null;
      }
      activeAssistantMessage.value = null;
    },
  });

  // 更新全局句柄
  sessionSseHandle = newHandle;
}

// 建立当前会话的SSE连接，监听实时消息
async function subscribeToSession(sessionId, sessionCache) {
  let sessionSseArchived = false;
  let cacheApplied = false;

  // 解析缓存数据
  let cachedContent = "";
  let lastChunkId = null;
  let cachedWaitingState = false;
  if (sessionCache) {
    cachedContent = sessionCache.content || "";
    lastChunkId = sessionCache.lastChunkId || null;
    cachedWaitingState = sessionCache.isWaitingResponse || false;

    // 恢复等待状态（用户消息已经通过 loadChat 加载了，不需要从缓存恢复）
    if (cachedWaitingState) {
      isWaitingResponse.value = true;
      isStreaming.value = true; // 设置为流式状态，以便显示停止按钮

      // 立即创建 AI 消息气泡以显示省略号
      currentMessages.value.push({
        senderType: "AI",
        content: cachedContent,
        stopped: false,
      });
      activeAssistantMessage.value =
        currentMessages.value[currentMessages.value.length - 1];
      cacheApplied = true;

      nextTick(() => scrollToBottom());
    }
  }

  const newHandle = chatAPI.subscribeChatStream({
    sessionId: sessionId,
    lastChunkId: lastChunkId,
    onId(id) {
      if (id === "ARCHIVE_FINISHED") {
        sessionSseArchived = true;
        isStreaming.value = false;
        messageCache
          .remove(sessionId)
          .catch((err) => console.error("清除缓存失败", err));
        newHandle.cancel();
        sessionSseHandle = null;
      } else {
        lastReceivedChunkId = id;
        isStreaming.value = true;
      }
    },
    onChunk(rawData) {
      if (sessionSseArchived) return;

      // 收到第一个chunk，清除等待状态
      isWaitingResponse.value = false;

      let chunk = JSON.parse(rawData);

      if (typeof chunk !== "string") chunk = String(chunk);
      chunk = chunk.replace(/\r\n/g, "\n");
      chunk = chunk.replace(/^data:\s?/gm, "");

      // 如果没有正在流式的AI消息，创建一个新的
      if (!activeAssistantMessage.value) {
        currentMessages.value.push({
          senderType: "AI",
          content: "",
          stopped: false,
        });
        activeAssistantMessage.value =
          currentMessages.value[currentMessages.value.length - 1];
        isStreaming.value = true;

        // 首次创建气泡时，一次性将缓存内容贴上去
        if (!cacheApplied && cachedContent) {
          activeAssistantMessage.value.content = cachedContent;
          cacheApplied = true;
          nextTick(() => scrollToBottom());
        }
      }

      typingBuffer.value += chunk;

      startTypingLoop();
    },
    onFinish() {
      // 只有当这个连接确实是当前激活的连接时，才清理全局句柄
      if (sessionSseHandle === newHandle) sessionSseHandle = null;

      // 关闭流状态
      isStreaming.value = false;
      isWaitingResponse.value = false;
      // 流式结束，清除本地缓存
      messageCache.remove(sessionId);
    },
    onError(err) {
      console.error("[SSE] onError 连接出错", err);
      if (sessionSseHandle === newHandle) {
        sessionSseHandle = null;
      }
      isStreaming.value = false;
      isWaitingResponse.value = false;
      // 发生错误时也清除缓存
      messageCache.remove(sessionId);
      if (rafId !== null) {
        cancelAnimationFrame(rafId);
        rafId = null;
      }
      activeAssistantMessage.value = null;
    },
  });

  // 更新全局句柄
  sessionSseHandle = newHandle;
}

// 加载指定会话的消息列表
async function loadChat(chatId) {
  // 取消之前的请求，避免旧请求覆盖新请求的结果
  if (loadChatAbortController) {
    loadChatAbortController.abort();
  }
  loadChatAbortController = new AbortController();
  const signal = loadChatAbortController.signal;

  // 重置分页状态
  isInitialLoading.value = true;
  resetPagination();
  try {
    // 加载第一页消息
    const response = await chatAPI.getChatMessagesByPage(
      chatId,
      pageNum.value,
      pageSize.value,
      signal,
    );

    const pageData = response.data || {};
    let records = Array.isArray(pageData.records) ? pageData.records : [];

    if (Array.isArray(records)) {
    } else if (records) {
      records = [records]; // 单个对象 => 单元素数组
    } else {
      records = [];
    }

    // 接口按创建时间倒序返回，这里翻转成正序显示
    currentMessages.value = [...records].reverse();
    total.value = pageData.total || 0;
    hasMore.value = pageNum.value * pageSize.value < total.value;

    await nextTick();
    await scrollToBottom(true);
    // 重新初始化 Observer，确保哨兵元素被正确观察
    initIntersectionObservers();
  } catch (error) {
    // 如果是主动取消的请求，不做处理
    if (error?.name === "CanceledError" || signal.aborted) {
      return;
    }
    console.error("加载对话消息失败:", error);
    currentMessages.value = [];
  } finally {
    isInitialLoading.value = false;
  }
}

// 加载更多历史消息（无限滚动）
async function loadMoreMessages() {
  if (loadingMore.value || !hasMore.value || !props.chatId) return;
  loadingMore.value = true;

  try {
    // 获得 messages 容器
    const container = messagesRef.value;
    // 记录加载前的 scrollHeight（内容高度）
    const previousScrollHeight = container ? container.scrollHeight : 0;

    const nextPage = pageNum.value + 1;
    // 调用接口加载下一页消息
    const response = await chatAPI.getChatMessagesByPage(
      props.chatId,
      nextPage,
      pageSize.value,
    );

    const pageData = response.data || {};
    const records = Array.isArray(pageData.records) ? pageData.records : [];

    if (records.length > 0) {
      const newMessages = [...records].reverse();
      currentMessages.value = [...newMessages, ...currentMessages.value];

      pageNum.value = nextPage;
      total.value = pageData.total || total.value;
      hasMore.value = pageNum.value * pageSize.value < total.value;

      // 追加旧消息后保持用户视口位置
      await nextTick();
      if (container) {
        const newScrollHeight = container.scrollHeight;
        container.scrollTop = newScrollHeight - previousScrollHeight;
      }
      // 重新初始化 Observer，确保哨兵元素被正确观察
      initIntersectionObservers();
    } else {
      hasMore.value = false;
    }
  } catch (error) {
    console.error("加载更多消息失败:", error);
  } finally {
    loadingMore.value = false;
  }
}

// 处理回车发送，需与按钮禁用逻辑保持一致
function handleInputEnter() {
  if (isStreaming.value || !userInput.value.trim()) {
    ElMessage.error("当前正在发送信息，请稍后再试！");
    return;
  }
  startStream(userInput.value);
}

// 处理键盘事件：Enter 发送，Shift+Enter 换行
const handleKeydown = (e) => {
  // 只处理 Enter 键
  if (e.key === "Enter" || e.keyCode === 13) {
    // Shift + Enter 换行，单独 Enter 发送
    if (e.shiftKey) {
      return; // 允许换行，不阻止默认行为
    }
    e.preventDefault();
    handleInputEnter();
  }
  // 其他按键不处理，允许正常输入
};

// 自动调整 textarea 高度
function adjustTextareaHeight() {
  const textarea = textareaRef.value;
  if (!textarea) return;

  // 重置高度以获取正确的 scrollHeight
  textarea.style.height = "auto";
  const minHeight = 40; // 最小高度（约等于 1 行）
  const maxHeight = 200; // 最大高度（约等于 6 行）
  const newHeight = Math.min(
    Math.max(textarea.scrollHeight, minHeight),
    maxHeight,
  );
  textarea.style.height = `${newHeight}px`;
}

// 清空输入框
function clearInput() {
  userInput.value = "";
  adjustTextareaHeight();
}

// 开始流式发送消息
async function startStream(data) {
  const prompt = data.trim();
  if (!prompt) return;

  // 新建会话时，先创建会话，发送消息并保存缓存，然后通知父组件切换
  if (props.chatId === "0") {
    isStreaming.value = true;
    userInput.value = "";
    typingBuffer.value = "";
    nextTick(() => {
      adjustTextareaHeight();
    });
    if (rafId !== null) {
      cancelAnimationFrame(rafId);
      rafId = null;
    }

    isPromotingFromLocal.value = true;
    try {
      const title = prompt.slice(0, 12);
      const res = await chatAPI.postCreateSession(title);
      const newId = res.data;

      // 发送消息
      await chatAPI.postMessage({
        message: prompt,
        sessionId: newId,
        mode: mode.value ? ChatMode.Online : ChatMode.Local,
      });

      // 立即将等待响应状态保存到缓存（用户消息已归档，不需要缓存）
      await messageCache.save(
        newId,
        "", // AI 消息内容为空
        null, // lastChunkId 为空
        true, // 等待响应状态
      );

      // 通知父组件切换到真实会话；切换后 watch 会触发并从缓存恢复状态
      emit("chat-created", { id: newId, title });
    } catch (error) {
      console.error("创建会话并发送消息失败:", error);
      ElMessage.error("发送失败，请稍后重试");
    } finally {
      isPromotingFromLocal.value = false;
      isStreaming.value = false;
    }

    return;
  }

  isStreaming.value = true;
  userInput.value = "";
  typingBuffer.value = "";
  // 重置 textarea 高度
  nextTick(() => {
    adjustTextareaHeight();
  });
  if (rafId !== null) {
    cancelAnimationFrame(rafId);
    rafId = null;
  }

  // 将临时用户消息添加到当前消息列表
  currentMessages.value.push({
    senderType: "USER",
    content: prompt,
  });
  // 将 AI 消息添加到当前消息列表，并记录当前正在流式输出的消息对象
  currentMessages.value.push({
    senderType: "AI",
    content: "",
    stopped: false,
  });

  activeAssistantMessage.value =
    currentMessages.value[currentMessages.value.length - 1];

  if (!data) userInput.value = "";
  // 发送时滚动到底部
  await scrollToBottom(true);

  let sid = props.chatId ?? 0;

  // 设置等待响应状态
  isWaitingResponse.value = true;

  try {
    // 发送消息
    await chatAPI.postMessage({
      message: prompt,
      sessionId: sid,
      mode: mode.value ? ChatMode.Online : ChatMode.Local,
    });
    // 清除本地缓存
    await messageCache.remove(sid);
    // 订阅sse
    NewSSE(sid, null);
  } catch (err) {
    // startChat 或 subscribeChatStream 出错
    isStreaming.value = false;
    isWaitingResponse.value = false;
    if (!typingBuffer.value.length && rafId !== null) {
      cancelAnimationFrame(rafId);
      rafId = null;
    }
    console.error("发送消息失败:", err);
  }
}

// 停止当前流式输出
function stopStream() {
  if (sessionSseHandle) closeSse();
  chatAPI
    .postStopMessage(props.chatId)
    .catch((err) => console.error("停止对话失败:", err));
  isStreaming.value = false;
  isWaitingResponse.value = false;

  // 清除缓存，避免切换页面后恢复等待状态
  if (props.chatId) {
    messageCache
      .remove(props.chatId)
      .catch((err) => console.error("清除缓存失败", err));
  }

  typingBuffer.value = "";
  if (rafId !== null) {
    cancelAnimationFrame(rafId);
    rafId = null;
  }
  if (activeAssistantMessage.value) {
    const msg = activeAssistantMessage.value;
    msg.stopped = true;
    activeAssistantMessage.value = null;
  }
}

// 重新生成：将选中的用户消息填回输入框并直接重新发送
function handleRegenerate(content) {
  if (!content || isStreaming.value) return;
  userInput.value = content;
  // 直接使用这段内容重新开始一次流式对话
  startStream(content);
}

// 滚动到底部
async function scrollToBottom(force = false) {
  await nextTick();
  const container = messagesRef.value;
  if (!container) return;

  if (!force && !autoScrollEnabled.value) {
    return;
  }

  container.scrollTop = container.scrollHeight;
}

// 初始化 Intersection Observer，用于检测顶部和底部哨兵元素
function initIntersectionObservers() {
  const container = messagesRef.value;
  if (!container) return;

  // 清理旧的 Observer
  if (topObserver) {
    topObserver.disconnect();
    topObserver = null;
  }
  if (bottomObserver) {
    bottomObserver.disconnect();
    bottomObserver = null;
  }

  // 创建顶部 Observer：当顶部哨兵元素进入视口时，加载更多历史消息
  topObserver = new IntersectionObserver(
    (entries) => {
      const entry = entries[0];
      if (
        entry.isIntersecting &&
        !isInitialLoading.value &&
        !loadingMore.value &&
        hasMore.value
      ) {
        loadMoreMessages();
      }
    },
    {
      root: container,
      rootMargin: "0px",
      threshold: 0.1, // 当哨兵元素 10% 可见时触发
    },
  );

  // 创建底部 Observer：当底部哨兵元素进入视口时，启用自动滚动
  bottomObserver = new IntersectionObserver(
    (entries) => {
      const entry = entries[0];
      // 当底部哨兵元素可见时，说明用户已经滚动到底部附近，启用自动滚动
      autoScrollEnabled.value = entry.isIntersecting;
    },
    {
      root: container,
      rootMargin: "0px",
      threshold: 0.1,
    },
  );

  // 开始观察哨兵元素
  if (topSentinelRef.value) {
    topObserver.observe(topSentinelRef.value);
  }
  if (bottomSentinelRef.value) {
    bottomObserver.observe(bottomSentinelRef.value);
  }
}

// 计算并保存当前流式缓存到本地
async function saveStreamCache() {
  if (props.chatId && (isStreaming.value || isWaitingResponse.value)) {
    const fullContent = activeAssistantMessage.value
      ? activeAssistantMessage.value.content + typingBuffer.value
      : "";
    await messageCache.save(
      props.chatId,
      fullContent,
      lastReceivedChunkId,
      isWaitingResponse.value,
    );
  }
}

// 关闭旧连接
const closeSse = () => {
  sessionSseHandle.cancel();
  sessionSseHandle = null;
};

// 清理 Intersection Observer
const cleanupObservers = () => {
  if (topObserver) {
    topObserver.disconnect();
    topObserver = null;
  }
  if (bottomObserver) {
    bottomObserver.disconnect();
    bottomObserver = null;
  }
};

/*输入框滑动/欢迎语相关
 * */
const isNewChatView = computed(() => {
  return props.chatId === "0" || props.chatId == null;
});
// 欢迎语数组
const welcomeMessages = [
  "您今天在想什么？",
  "有什么我可以帮您的吗？",
  "想从哪里开始？",
  "欢迎回来，需要我做点什么？",
  "今天有什么想解决的问题？",
];
// 选中的欢迎语
const randomWelcomeMessage = ref("");
// 生成随机欢迎语的函数
const generateRandomWelcome = () => {
  const randomIndex = Math.floor(Math.random() * welcomeMessages.length);
  randomWelcomeMessage.value = welcomeMessages[randomIndex];
};

// 组件挂载
onMounted(() => {
  // 从 localStorage 读取保存的模式
  const savedMode = localStorage.getItem("chatMode");
  if (savedMode !== null) mode.value = savedMode === "true"; // localStorage 存储的是字符串

  // 生成随机欢迎语
  generateRandomWelcome();

  // 监听浏览器刷新/关闭/跳转事件
  window.addEventListener("beforeunload", () => {
    // 使用 sendBeacon 或同步方式保存缓存（beforeunload 中异步操作可能不会完成）
    saveStreamCache().catch((err) => console.error("保存缓存失败", err));
    localStorage.setItem("chatMode", String(mode.value));
  });

  // 初始化 Intersection Observer
  nextTick(() => {
    initIntersectionObservers();
    // 初始化 textarea 高度
    adjustTextareaHeight();
  });
});

// 监听 userInput 变化，自动调整高度
watch(userInput, () => {
  nextTick(() => {
    adjustTextareaHeight();
  });
});

// 监听：当组件接收到的值改变时，根据该值加载对应会话消息
watch(
  () => props.chatId,
  async (newId, oldId) => {
    // 清理 Intersection Observer
    cleanupObservers();

    // 有sse => 关闭连接 + 保存旧会话缓存
    if (sessionSseHandle) {
      closeSse();
      // 完整内容 = 已渲染 + 打字机缓冲区
      if (activeAssistantMessage.value || isWaitingResponse.value) {
        const fullContent = activeAssistantMessage.value
          ? activeAssistantMessage.value.content + typingBuffer.value
          : "";
        messageCache
          .save(
            oldId,
            fullContent,
            lastReceivedChunkId,
            isWaitingResponse.value,
          )
          .catch((err) => console.error("保存缓存失败", err));
      }
    }
    // 清理所有状态
    isStreaming.value = false;
    isWaitingResponse.value = false;
    typingBuffer.value = "";
    activeAssistantMessage.value = null;
    lastReceivedChunkId = null;
    currentMessages.value = [];
    resetPagination();
    showSkeleton.value = false;
    if (skeletonTimer) {
      clearTimeout(skeletonTimer);
      skeletonTimer = null;
    }
    if (rafId !== null) {
      cancelAnimationFrame(rafId);
      rafId = null;
    }

    // 进入
    // 如果进入新对话窗口，生成新的随机欢迎语并return
    if (newId === "0" || newId == null) {
      generateRandomWelcome();
      return;
    }

    await runWithSkeleton(loadChat(newId));

    // 从本地缓存中读取：content，lastReceivedChunkId
    let sessionCache = null;
    if (await messageCache.has(newId)) {
      sessionCache = await messageCache.get(newId);
    }

    // 建立当前会话的SSE连接，监听实时消息
    await subscribeToSession(newId, sessionCache);

    // 确保 Observer 已正确初始化
    await nextTick();
    initIntersectionObservers();
  },
);

// 组件卸载前
onBeforeUnmount(() => {
  // 卸载前保存当前流式缓存
  saveStreamCache();

  // 保存模式设置到 localStorage
  localStorage.setItem("chatMode", String(mode.value));

  // 清理 Intersection Observer
  cleanupObservers();

  // 清理定时器
  if (rafId !== null) {
    cancelAnimationFrame(rafId);
    rafId = null;
  }
  if (skeletonTimer) {
    clearTimeout(skeletonTimer);
    skeletonTimer = null;
  }
  // 关闭会话SSE连接
  if (sessionSseHandle) {
    sessionSseHandle.cancel();
    sessionSseHandle = null;
  }
});
</script>

<template>
  <div class="chat-record" :class="{ 'is-new-chat': isNewChatView }">
    <div class="messages" ref="messagesRef">
      <!-- 顶部哨兵元素：用于检测是否滚动到顶部，触发加载更多 -->
      <div ref="topSentinelRef" class="sentinel"></div>

      <!-- 骨架屏：加载超过300ms时显示，模拟一问一答的对话形式 -->
      <template v-if="showSkeleton">
        <template v-for="i in 2" :key="'skeleton-pair-' + i">
          <!-- 用户消息骨架（右侧） -->
          <div class="skeleton-message skeleton-user">
            <div class="skeleton-bubble">
              <el-skeleton :rows="1" animated />
            </div>
          </div>
          <!-- AI消息骨架（左侧） -->
          <div class="skeleton-message skeleton-ai">
            <div class="skeleton-bubble">
              <el-skeleton :rows="2" animated />
            </div>
          </div>
        </template>
      </template>
      <!-- 消息列表 -->
      <template v-else>
        <ChatMessage
          v-for="(message, index) in currentMessages"
          :key="index"
          :message="{
            role: message.senderType,
            content: message.content,
            stopped: message.stopped,
            createdAt: message.created,
          }"
          :isStreaming="isStreaming"
          :isWaiting="isWaitingForChunk && message === activeAssistantMessage"
          @regenerate="handleRegenerate"
        />
      </template>

      <!-- 底部哨兵元素：用于检测是否滚动到底部，控制自动滚动 -->
      <div ref="bottomSentinelRef" class="sentinel"></div>
    </div>
    <div class="input-area">
      <div class="input-wrapper">
        <!-- 新对话时显示的提示文字 -->
        <div v-if="isNewChatView" class="welcome-text">
          {{ randomWelcomeMessage }}
        </div>
        <div class="input-row">
          <!-- TODO：暂时修改死宽度，后面要优化 -->
          <div class="textarea-wrapper" style="max-width: 685px">
            <textarea
              ref="textareaRef"
              v-model="userInput"
              @keydown="handleKeydown"
              @input="adjustTextareaHeight"
              placeholder="给 CORS 发送消息"
              rows="1"
              class="custom-textarea"
            ></textarea>
            <!-- 清空按钮 -->
            <button
              v-if="userInput"
              @click="clearInput"
              class="clear-button"
              type="button"
            >
              <el-icon><Close /></el-icon>
            </button>
          </div>
          <el-switch
            v-model="mode"
            active-text="在线"
            inactive-text="本地"
            size="large"
          />

          <el-button
            round
            class="send-button"
            @click="
              isStreaming || typingBuffer.length
                ? stopStream()
                : startStream(userInput)
            "
            :disabled="!isStreaming && !userInput.trim()"
          >
            <template v-if="isStreaming">
              <IconStop class="icon-stop" />
            </template>
            <template v-else>
              <el-icon class="icon-send" size="small"><Position /></el-icon>
            </template>
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.chat-record {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.chat-record.is-new-chat {
  .messages {
    /* 新对话时弱化消息区存在感 */
    opacity: 0;
    pointer-events: none;
  }

  .input-area {
    /* 向上平移到视觉中心 */
    transform: translateY(-40vh);
    padding-bottom: 0;
  }
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 1rem;
  display: flex;
  flex-direction: column;
  gap: 1.5rem;

  > * {
    width: 70%;
    margin: 0 auto;
  }

  // 哨兵元素：用于 Intersection Observer 检测
  .sentinel {
    width: 100%;
    height: 1px;
    flex-shrink: 0;
  }

  // 骨架屏消息样式，模拟聊天对话形式
  .skeleton-message {
    display: flex;
    max-width: 48rem;
    width: 100%;
    margin: 0 auto;

    .skeleton-bubble {
      padding: 0.75rem 1.25rem;
      border-radius: 1.25rem;
      background: var(--el-bg-color);
    }

    // 用户消息：右对齐，短一点
    &.skeleton-user {
      justify-content: flex-end;

      .skeleton-bubble {
        width: 40%;
        min-width: 120px;
        max-width: 280px;
        background: var(--el-color-primary-light-9);
      }
    }

    // AI消息：左对齐，长一点
    &.skeleton-ai {
      justify-content: flex-start;

      .skeleton-bubble {
        width: 70%;
        min-width: 200px;
        max-width: 500px;
      }
    }
  }
}

.input-area {
  flex-shrink: 0;
  padding: 1rem;
  display: flex;
  flex-direction: column;
  align-items: center;

  /* 动画关键 */
  transition:
    transform 0.45s cubic-bezier(0.4, 0, 0.2, 1),
    padding 0.45s cubic-bezier(0.4, 0, 0.2, 1);

  .input-wrapper {
    width: 100%;
    max-width: 900px;
    margin: 0 auto;
    display: flex;
    flex-direction: column;
    gap: 1rem;

    .welcome-text {
      text-align: center;
      font-size: 1.75rem;
      color: var(--el-text-color-primary);
      margin-bottom: 1.5rem;
      opacity: 0;
      animation: fadeIn 0.6s ease-in-out 0.2s forwards;
    }

    @keyframes fadeIn {
      from {
        opacity: 0;
        transform: translateY(-10px);
      }
      to {
        opacity: 1;
        transform: translateY(0);
      }
    }
  }

  .input-row {
    display: flex;
    gap: 10px;
    align-items: center;
    background: var(--el-bg-color);
    padding: 0.75rem;
    border-radius: 1.5rem;
    border: 0;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
    width: 100%;
    overflow: hidden;
    transition:
      border-color 0.2s,
      box-shadow 0.2s;

    &:focus-within {
      border-color: var(--el-border-color-darker);
      box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
    }

    .textarea-wrapper {
      flex: 1;
      position: relative;
      display: flex;
      align-items: center;

      .custom-textarea {
        width: 100%;
        resize: none;
        border: none;
        background: transparent;
        padding: 0.5rem 2rem 0.5rem 0.5rem;
        color: inherit;
        font-family: inherit;
        font-size: 1rem;
        line-height: 1.5;
        min-height: 40px;
        max-height: 200px;
        overflow-y: auto;

        &:focus {
          outline: none;
        }

        &::placeholder {
          color: var(--el-text-color-placeholder);
        }
      }

      .clear-button {
        position: absolute;
        right: 0.5rem;
        top: 50%;
        transform: translateY(-50%);
        background: transparent;
        border: none;
        cursor: pointer;
        padding: 0.25rem;
        display: flex;
        align-items: center;
        justify-content: center;
        color: var(--el-text-color-placeholder);
        transition: color 0.2s;
        z-index: 1;

        &:hover {
          color: var(--el-text-color-regular);
        }

        .el-icon {
          font-size: 0.875rem;
        }
      }
    }

    .send-button {
      width: 2rem;
      height: 2rem;
      display: flex;
      align-items: center;
      justify-content: center;
      border: none;
      background: var(--el-text-color-primary);
      color: var(--el-bg-color);
      cursor: pointer;
      transition: all 0.2s ease;
      flex-shrink: 0;

      &:hover:not(:disabled) {
        background: var(--el-text-color-regular);
      }

      &:disabled {
        background: var(--el-fill-color);
        color: var(--el-text-color-regular);
        cursor: not-allowed;
      }

      .icon-send {
        width: 1.25rem;
        height: 1.25rem;
      }

      .icon-stop {
        width: 1rem;
        height: 1rem;
      }
    }
  }
}
</style>
