<template>
  <div class="message" :class="{ 'message-user': isUser }">
    <div class="content">
      <div class="text-container">
        <template v-if="isUser">
          <el-button
            class="user-regenerate-button"
            type="text"
            @click="clickRegenerate"
            title="重新生成"
            :disabled="props.isStreaming"
            round
          >
            <ArrowPathIcon class="copy-icon" />
          </el-button>
          <button
            class="user-copy-button"
            @click="copyContent"
            :title="copyButtonTitle"
          >
            <DocumentDuplicateIcon v-if="!copied" class="copy-icon" />
            <CheckIcon v-else class="copy-icon copied" />
          </button>
        </template>
        <div class="text" ref="contentRef" v-if="isUser">
          {{ message.content }}
        </div>
        <div class="text markdown-content" ref="contentRef" v-else>
          <span class="markdown-body" v-html="processedContent"></span>
          <span v-if="showWaitingIndicator" class="waiting-ellipsis"
            ><el-icon><MoreFilled /></el-icon
          ></span>
        </div>
      </div>
      <div v-if="!isUser && message.stopped" class="message-status stopped">
        这条消息已停止
      </div>
      <div class="message-footer">
        <span class="time">{{ formatTime(message.createdAt) }}</span>
        <button
          v-if="!isUser"
          class="copy-button"
          @click="copyContent"
          :title="copyButtonTitle"
        >
          <DocumentDuplicateIcon v-if="!copied" class="copy-icon" />
          <CheckIcon v-else class="copy-icon copied" />
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, nextTick, ref, watch } from "vue";
import { marked } from "marked";
import DOMPurify from "dompurify";
import {
  DocumentDuplicateIcon,
  CheckIcon,
  ArrowPathIcon,
} from "@heroicons/vue/24/outline";
import hljs from "highlight.js";
import "highlight.js/styles/github-dark.css";
import { MoreFilled } from "@element-plus/icons-vue";
const props = defineProps({
  message: {
    type: Object,
    required: true,
  },
  isStreaming: {
    type: Boolean,
    default: false,
  },
  isWaiting: {
    type: Boolean,
    default: false,
  },
});
const emit = defineEmits(["regenerate"]);

/* 响应式数据 */
// 引用消息内容的 DOM 元素
const contentRef = ref(null);
// 复制状态
const copied = ref(false);

/* 计算属性 */
// 计算复制按钮的标题
const copyButtonTitle = computed(() => (copied.value ? "已复制" : "复制内容"));
// 计算当前消息是否为用户消息
const isUser = computed(() => props.message.role === "USER");
// 计算显示省略号
const showWaitingIndicator = computed(() => !isUser.value && props.isWaiting);

// 配置 marked
marked.setOptions({
  breaks: true,
  gfm: true,
  sanitize: false,
});

// 处理内容
const processContent = (content) => {
  if (!content) return "";

  // 分析内容中的 think 标签
  let result = "";
  let isInThinkBlock = false;
  let currentBlock = "";

  // 逐字符分析，处理 think 标签
  for (let i = 0; i < content.length; i++) {
    if (content.slice(i, i + 7) === "<think>") {
      isInThinkBlock = true;
      if (currentBlock) {
        // 将之前的普通内容转换为 HTML
        result += marked.parse(currentBlock);
      }
      currentBlock = "";
      i += 6; // 跳过 <think>
      continue;
    }

    if (content.slice(i, i + 8) === "</think>") {
      isInThinkBlock = false;
      // 将 think 块包装在特殊 div 中
      result += `<div class="think-block">${marked.parse(currentBlock)}</div>`;
      currentBlock = "";
      i += 7; // 跳过 </think>
      continue;
    }

    currentBlock += content[i];
  }

  // 处理剩余内容
  if (currentBlock) {
    if (isInThinkBlock) {
      result += `<div class="think-block">${marked.parse(currentBlock)}</div>`;
    } else {
      result += marked.parse(currentBlock);
    }
  }

  // 净化处理后的 HTML
  const cleanHtml = DOMPurify.sanitize(result, {
    ADD_TAGS: ["think", "code", "pre", "span"],
    ADD_ATTR: ["class", "language"],
  });

  // 在净化后的 HTML 中查找代码块并添加复制按钮
  const tempDiv = document.createElement("div");
  tempDiv.innerHTML = cleanHtml;

  // 查找所有代码块
  const preElements = tempDiv.querySelectorAll("pre");
  preElements.forEach((pre) => {
    const code = pre.querySelector("code");
    if (code) {
      // 创建包装器
      const wrapper = document.createElement("div");
      wrapper.className = "code-block-wrapper";

      // 添加复制按钮
      const copyBtn = document.createElement("button");
      copyBtn.className = "code-copy-button";
      copyBtn.title = "复制代码";
      copyBtn.innerHTML = `
        <svg xmlns="http://www.w3.org/2000/svg" class="code-copy-icon" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
        </svg>
      `;

      // 添加成功消息
      const successMsg = document.createElement("div");
      successMsg.className = "copy-success-message";
      successMsg.textContent = "已复制!";

      // 组装结构
      wrapper.appendChild(copyBtn);
      wrapper.appendChild(pre.cloneNode(true));
      wrapper.appendChild(successMsg);

      // 替换原始的 pre 元素
      pre.parentNode.replaceChild(wrapper, pre);
    }
  });

  return tempDiv.innerHTML;
};

// 修改计算属性
const processedContent = computed(() => {
  if (!props.message.content) return "";
  return processContent(props.message.content);
});

// 为代码块添加复制功能
const setupCodeBlockCopyButtons = () => {
  if (!contentRef.value) return;

  const codeBlocks = contentRef.value.querySelectorAll(".code-block-wrapper");
  codeBlocks.forEach((block) => {
    const copyButton = block.querySelector(".code-copy-button");
    const codeElement = block.querySelector("code");
    const successMessage = block.querySelector(".copy-success-message");

    if (copyButton && codeElement) {
      // 移除旧的事件监听器
      const newCopyButton = copyButton.cloneNode(true);
      copyButton.parentNode.replaceChild(newCopyButton, copyButton);

      // 添加新的事件监听器
      newCopyButton.addEventListener("click", async (e) => {
        e.preventDefault();
        e.stopPropagation();
        try {
          const code = codeElement.textContent || "";
          await copyToClipboard(code);

          // 显示成功消息
          if (successMessage) {
            successMessage.classList.add("visible");
            setTimeout(() => {
              successMessage.classList.remove("visible");
            }, 2000);
          }
        } catch (err) {
          console.error("复制代码失败:", err);
        }
      });
    }
  });
};

// 在内容更新后手动应用高亮和设置复制按钮
const highlightCode = async () => {
  await nextTick();
  if (contentRef.value) {
    contentRef.value.querySelectorAll("pre code").forEach((block) => {
      hljs.highlightElement(block);
    });

    // 设置代码块复制按钮
    setupCodeBlockCopyButtons();
  }
};

// 点击重新生成按钮
const clickRegenerate = () => {
  if (!props.message || !props.message.content) return;
  emit("regenerate", props.message.content);
};

// 复制函数
const copyToClipboard = async (text) => {
  // 使用 document.execCommand
  const textArea = document.createElement("textarea");
  textArea.value = text;

  // 确保 textarea 不可见但仍在文档流中
  textArea.style.position = "fixed";
  textArea.style.left = "-9999px";
  textArea.style.top = "0";

  document.body.appendChild(textArea);
  textArea.focus();
  textArea.select();

  try {
    document.execCommand("copy");
    document.body.removeChild(textArea);
  } catch (err) {
    document.body.removeChild(textArea);
    throw err;
  }
};

// 复制内容到剪贴板
const copyContent = async () => {
  try {
    // 获取纯文本内容
    let textToCopy = props.message.content;

    await copyToClipboard(textToCopy);
    copied.value = true;

    // 3秒后重置复制状态
    setTimeout(() => {
      copied.value = false;
    }, 3000);
  } catch (err) {
    console.error("复制失败:", err);
  }
};

// 监听内容变化
watch(
  () => props.message.content,
  () => {
    if (!isUser.value) {
      highlightCode();
    }
  },
);

// 初始化时也执行一次
onMounted(() => {
  if (!isUser.value) {
    highlightCode();
  }
});

const formatTime = (timestamp) => {
  if (!timestamp) return "";
  return new Date(timestamp).toLocaleString();
};
</script>

<style scoped lang="scss">
.message {
  display: flex;
  margin-bottom: 1.5rem;
  gap: 1rem;

  &.message-user {
    flex-direction: row-reverse;

    .content {
      align-items: flex-end;

      .text-container {
        position: relative;

        .text {
          background: var(--el-color-primary-light-9);
          color: var(--el-text-color-primary);
          border-radius: 1.25rem; // 更圆润
          padding: 0.75rem 1.25rem; // 调整内边距
          border: none; // 去掉边框
          position: relative;
        }

        // 用户侧的复制/重新生成按钮：样式完全一致，只是图标和点击逻辑不同
        .user-copy-button,
        .user-regenerate-button {
          position: absolute;
          left: -30px;
          background: transparent;
          border: none;
          width: 24px;
          height: 24px;
          display: flex;
          align-items: center;
          justify-content: center;
          cursor: pointer;
          opacity: 0;
          transition: opacity 0.2s;

          &:disabled {
            background: var(--el-fill-color);
            color: var(--el-text-color-placeholder);
            cursor: not-allowed;
          }

          .copy-icon {
            width: 16px;
            height: 16px;
            color: var(--el-text-color-secondary);

            &.copied {
              color: #4ade80;
            }
          }
        }

        // 两个按钮在垂直方向居中，水平方向左右并排
        .user-regenerate-button,
        .user-copy-button {
          top: 50%;
          transform: translateY(-50%);
        }

        // 重新生成按钮更靠外侧，复制按钮更靠近气泡
        .user-regenerate-button {
          left: -60px;
        }

        .user-copy-button {
          left: -30px;
        }

        &:hover .user-copy-button,
        &:hover .user-regenerate-button {
          opacity: 1;
        }
      }

      .message-footer {
        flex-direction: row-reverse;
      }
    }
  }

  .avatar {
    width: 40px;
    height: 40px;
    flex-shrink: 0;

    .icon {
      width: 100%;
      height: 100%;
      color: var(--el-text-color-secondary);
      padding: 4px;
      border-radius: 8px;
      transition: all 0.3s ease;

      &.assistant {
        color: var(--el-text-color-primary);
        background: var(--el-fill-color);

        &:hover {
          background: var(--el-fill-color-dark);
          transform: scale(1.05);
        }
      }
    }
  }

  .content {
    display: flex;
    flex-direction: column;
    gap: 0.25rem;
    max-width: 100%; // 增加最大宽度

    .text-container {
      position: relative;
    }

    .message-footer {
      display: flex;
      align-items: center;
      margin-top: 0.25rem;

      .time {
        font-size: 0.75rem;
        color: var(--el-text-color-secondary);
      }

      .copy-button {
        display: flex;
        align-items: center;
        gap: 0.25rem;
        background: transparent;
        border: none;
        font-size: 0.75rem;
        color: var(--el-text-color-secondary);
        padding: 0.25rem 0.5rem;
        border-radius: 4px;
        cursor: pointer;
        margin-right: auto;
        transition: background-color 0.2s;

        &:hover {
          background-color: var(--el-fill-color-light);
        }

        .copy-icon {
          width: 14px;
          height: 14px;

          &.copied {
            color: #4ade80;
          }
        }

        .copy-text {
          font-size: 0.75rem;
        }
      }
    }

    .text {
      padding: 1rem;
      border-radius: 1rem; // 统一圆角
      line-height: 1.6; // 增加行高
      white-space: pre-wrap;
      color: var(--el-text-color-primary);
      background: transparent; // 默认透明
      border: none; // 去掉边框
      position: relative;

      .markdown-body {
        display: inline;

        // 确保最后一个段落不会导致省略号换行
        :deep(p:last-child) {
          margin-bottom: 0;
          display: inline;
        }

        // 如果最后是其他块级元素（除了代码块、引用等），也设为内联
        :deep(
          > *:last-child:not(pre):not(blockquote):not(table):not(ul):not(
              ol
            ):not(.code-block-wrapper):not(.think-block)
        ) {
          display: inline;
        }
      }

      .cursor {
        animation: blink 1s infinite;
      }

      .typing-cursor {
        animation: blink 1s infinite;
        color: var(--el-color-primary);
        font-weight: bold;
        margin-left: 2px;
      }

      .waiting-ellipsis {
        display: inline-flex;
        margin-left: 0.35rem;
        font-weight: 600;
        letter-spacing: 0.2rem;
        color: var(--el-text-color-secondary);
        animation: ellipsisPulse 1.2s ease-in-out infinite;
        vertical-align: baseline;
        white-space: nowrap;
      }

      :deep(.think-block) {
        position: relative;
        padding: 0.75rem 1rem 0.75rem 1.5rem;
        margin: 0.5rem 0;
        color: var(--el-text-color-secondary);
        font-style: italic;
        border-left: 4px solid var(--el-border-color);
        background-color: var(--el-fill-color-light);
        border-radius: 0 0.5rem 0.5rem 0;

        // 添加平滑过渡效果
        opacity: 1;
        transform: translateX(0);
        transition:
          opacity 0.3s ease,
          transform 0.3s ease;

        &::before {
          content: "思考";
          position: absolute;
          top: -0.75rem;
          left: 1rem;
          padding: 0 0.5rem;
          font-size: 0.75rem;
          background: var(--el-fill-color);
          border-radius: 0.25rem;
          color: var(--el-text-color-placeholder);
          font-style: normal;
        }

        // 添加进入动画
        &:not(:first-child) {
          animation: slideIn 0.3s ease forwards;
        }
      }

      :deep(pre) {
        background: var(--el-fill-color-light);
        padding: 1rem;
        border-radius: 0.5rem;
        overflow-x: auto;
        margin: 0.5rem 0;
        border: 2px solid var(--el-border-color);

        code {
          background: transparent;
          padding: 0;
          font-family:
            ui-monospace,
            SFMono-Regular,
            SF Mono,
            Menlo,
            Consolas,
            Liberation Mono,
            monospace;
          font-size: 0.9rem;
          line-height: 1.5;
          tab-size: 2;
        }
      }

      :deep(.hljs) {
        color: var(--el-text-color-primary);
        background: transparent;
      }

      :deep(.hljs-keyword) {
        color: #d73a49;
      }

      :deep(.hljs-built_in) {
        color: #005cc5;
      }

      :deep(.hljs-type) {
        color: #6f42c1;
      }

      :deep(.hljs-literal) {
        color: #005cc5;
      }

      :deep(.hljs-number) {
        color: #005cc5;
      }

      :deep(.hljs-regexp) {
        color: #032f62;
      }

      :deep(.hljs-string) {
        color: #032f62;
      }

      :deep(.hljs-subst) {
        color: #24292e;
      }

      :deep(.hljs-symbol) {
        color: #e36209;
      }

      :deep(.hljs-class) {
        color: #6f42c1;
      }

      :deep(.hljs-function) {
        color: #6f42c1;
      }

      :deep(.hljs-title) {
        color: #6f42c1;
      }

      :deep(.hljs-params) {
        color: #24292e;
      }

      :deep(.hljs-comment) {
        color: #6a737d;
      }

      :deep(.hljs-doctag) {
        color: #d73a49;
      }

      :deep(.hljs-meta) {
        color: #6a737d;
      }

      :deep(.hljs-section) {
        color: #005cc5;
      }

      :deep(.hljs-name) {
        color: #22863a;
      }

      :deep(.hljs-attribute) {
        color: #005cc5;
      }

      :deep(.hljs-variable) {
        color: #e36209;
      }
    }

    .message-status {
      font-size: 0.75rem;
      margin-top: 0.25rem;
      color: var(--el-text-color-secondary);

      &.stopped {
        color: var(--el-text-color-secondary);
      }
    }
  }
}

@keyframes blink {
  0%,
  100% {
    opacity: 1;
  }

  50% {
    opacity: 0;
  }
}

@keyframes ellipsisPulse {
  0%,
  100% {
    opacity: 0.3;
  }

  50% {
    opacity: 1;
  }
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateX(-10px);
  }

  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.dark {
  .message {
    .avatar .icon {
      &.assistant {
        color: var(--el-text-color-primary);
        background: var(--el-fill-color-dark);

        &:hover {
          background: var(--el-fill-color-darker);
        }
      }
    }

    &.message-user {
      .content .text-container {
        .text {
          background: var(--el-fill-color-dark); // 深色模式下用户背景
          color: var(--el-text-color-primary);
          border: none;
        }

        .user-copy-button,
        .user-regenerate-button {
          .copy-icon {
            color: #999;

            &.copied {
              color: #4ade80;
            }
          }
        }
      }
    }

    .content {
      .message-footer {
        .time {
          color: var(--el-text-color-regular);
        }

        .copy-button {
          color: var(--el-text-color-regular);

          &:hover {
            background-color: var(--el-fill-color);
          }
        }
      }

      .text {
        :deep(.think-block) {
          background-color: var(--el-fill-color);
          border-left-color: var(--el-border-color);
          color: var(--el-text-color-regular);

          &::before {
            background: var(--el-fill-color-dark);
            color: var(--el-text-color-secondary);
          }
        }

        :deep(pre) {
          background: var(--el-fill-color-dark);
          border-color: var(--el-border-color);

          code {
            color: #c9d1d9;
          }
        }

        :deep(.hljs) {
          color: #c9d1d9;
          background: transparent;
        }

        :deep(.hljs-keyword) {
          color: #ff7b72;
        }

        :deep(.hljs-built_in) {
          color: #79c0ff;
        }

        :deep(.hljs-type) {
          color: #ff7b72;
        }

        :deep(.hljs-literal) {
          color: #79c0ff;
        }

        :deep(.hljs-number) {
          color: #79c0ff;
        }

        :deep(.hljs-regexp) {
          color: #a5d6ff;
        }

        :deep(.hljs-string) {
          color: #a5d6ff;
        }

        :deep(.hljs-subst) {
          color: var(--el-text-color-primary);
        }

        :deep(.hljs-symbol) {
          color: #ffa657;
        }

        :deep(.hljs-class) {
          color: #f2cc60;
        }

        :deep(.hljs-function) {
          color: #d2a8ff;
        }

        :deep(.hljs-title) {
          color: #d2a8ff;
        }

        :deep(.hljs-params) {
          color: #c9d1d9;
        }

        :deep(.hljs-comment) {
          color: #8b949e;
        }

        :deep(.hljs-doctag) {
          color: #ff7b72;
        }

        :deep(.hljs-meta) {
          color: #8b949e;
        }

        :deep(.hljs-section) {
          color: #79c0ff;
        }

        :deep(.hljs-name) {
          color: #7ee787;
        }

        :deep(.hljs-attribute) {
          color: #79c0ff;
        }

        :deep(.hljs-variable) {
          color: #ffa657;
        }
      }

      &.message-user .content .text {
        background: #e5e7eb; // 浅灰背景，类似 ChatGPT
        color: #000;
      }
    }
  }
}

.markdown-content {
  :deep(p) {
    margin: 0.5rem 0;

    &:first-child {
      margin-top: 0;
    }

    &:last-child {
      margin-bottom: 0;
    }
  }

  :deep(ul),
  :deep(ol) {
    margin: 0.5rem 0;

    padding-left: 1.5rem;
  }

  :deep(li) {
    margin: 0.25rem 0;
  }

  :deep(code) {
    background: var(--el-fill-color-light);
    padding: 0.2em 0.4em;
    border-radius: 3px;
    font-size: 0.9em;
    font-family: ui-monospace, monospace;
  }

  :deep(pre code) {
    background: transparent;
    padding: 0;
  }

  :deep(table) {
    border-collapse: collapse;
    margin: 0.5rem 0;
    width: 100%;
  }

  :deep(th),
  :deep(td) {
    border: 1px solid var(--el-border-color);
    padding: 0.5rem;
    text-align: left;
  }

  :deep(th) {
    background: var(--el-fill-color-light);
  }

  :deep(blockquote) {
    margin: 0.5rem 0;
    padding-left: 1rem;
    border-left: 4px solid var(--el-border-color);
    color: var(--el-text-color-secondary);
  }

  :deep(.code-block-wrapper) {
    position: relative;
    margin: 1rem 0;
    border-radius: 6px;
    overflow: hidden;

    .code-copy-button {
      position: absolute;
      top: 0.5rem;
      right: 0.5rem;
      background: var(--el-fill-color);
      border: none;
      color: #e6e6e6;
      cursor: pointer;
      padding: 0.25rem;
      border-radius: 4px;
      display: flex;
      align-items: center;
      justify-content: center;
      opacity: 0;
      transition:
        opacity 0.2s,
        background-color 0.2s;
      z-index: 10;

      &:hover {
        background-color: var(--el-fill-color-dark);
      }

      .code-copy-icon {
        width: 16px;
        height: 16px;
      }
    }

    &:hover .code-copy-button {
      opacity: 0.8;
    }

    pre {
      margin: 0;
      padding: 1rem;
      background: var(--el-fill-color-dark);
      overflow-x: auto;

      code {
        background: transparent;
        padding: 0;
        font-family: ui-monospace, monospace;
      }
    }

    .copy-success-message {
      position: absolute;
      top: 0.5rem;
      right: 0.5rem;
      background: var(--el-color-success);
      color: var(--el-color-white, #fff);
      padding: 0.25rem 0.5rem;
      border-radius: 4px;
      font-size: 0.75rem;
      opacity: 0;
      transform: translateY(-10px);
      transition:
        opacity 0.3s,
        transform 0.3s;
      pointer-events: none;
      z-index: 20;

      &.visible {
        opacity: 1;
        transform: translateY(0);
      }
    }
  }
}

.dark {
  .markdown-content {
    :deep(.code-block-wrapper) {
      .code-copy-button {
        background: var(--el-fill-color);

        &:hover {
          background-color: var(--el-fill-color-dark);
        }
      }

      pre {
        background: var(--el-fill-color-darker);
      }
    }

    :deep(code) {
      background: var(--el-fill-color);
    }

    :deep(th),
    :deep(td) {
      border-color: var(--el-border-color);
    }

    :deep(th) {
      background: var(--el-fill-color);
    }

    :deep(blockquote) {
      border-left-color: var(--el-border-color);
      color: var(--el-text-color-regular);
    }
  }
}
</style>
