import { createApp } from "vue";
import { createPinia } from "pinia";
import App from "./App.vue";
import router from "./router";
import * as ElementPlusIconsVue from "@element-plus/icons-vue";
// 引入sentry
// import * as Sentry from "@sentry/vue";
// 引入暗黑主题
import "element-plus/theme-chalk/dark/css-vars.css";
import "element-plus/es/components/message/style/css";
import "element-plus/es/components/message-box/style/css";

// 创建应用
const pinia = createPinia();
const app = createApp(App);

window.addEventListener("error", (event) => {
  console.error("【全局捕获】错误:", event.error);
});
window.addEventListener("unhandledrejection", (event) => {
  console.error("【全局捕获】错误:", event.reason);
});

// 初始化 Sentry
// Sentry.init({
//   app,
//   dsn: "https://de1302f111fe559d673594fa874fc9be@o4510520530501632.ingest.de.sentry.io/4510520572969040",
//
//   sendDefaultPii: true,
//   integrations: [
//     Sentry.browserTracingIntegration({ router }),
//     Sentry.replayIntegration(),
//   ],
//   // dev 也启用
//   environment: "development",
//   enabled: true,
//
//   tracesSampleRate: 1.0,
//
//   tracePropagationTargets: ["localhost", /^https:\/\/yourserver\.io\/api/],
//
//   replaysSessionSampleRate: 0.1,
//   replaysOnErrorSampleRate: 1.0,
// });

// 使用路由器和Element Plus
app.use(router);
app.use(pinia);
app.mount("#app");

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component);
}
