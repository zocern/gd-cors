import { createRouter, createWebHistory } from "vue-router";
import { useUserStore } from "../stores/user";

// 懒加载组件封装，支持错误处理
function lazyLoad(view) {
  return () =>
    import(`../views/${view}/index.vue`).catch(() => {
      console.error(`Failed to load view: ${view}`);
    });
}

const routes = [
  {
    path: "/",
    redirect: "/login",
  },
  {
    path: "/home",
    name: "Home",
    component: lazyLoad("Home"),
    meta: { requiresAuth: true },
  },
  {
    path: "/ai-chat",
    name: "AIChat",
    component: lazyLoad("AIChat"),
    meta: { requiresAuth: true },
  },
  {
    path: "/comfort-simulator",
    name: "ComfortSimulator",
    component: lazyLoad("ComfortSimulator"),
    meta: { requiresAuth: true },
  },
  {
    path: "/customer-service",
    name: "CustomerService",
    component: lazyLoad("CustomerService"),
    meta: { requiresAuth: true },
  },
  {
    path: "/login",
    name: "Login",
    component: lazyLoad("Login"),
  },
  {
    path: "/register",
    name: "Register",
    component: lazyLoad("Register"),
  },
  {
    path: "/root",
    name: "Root",
    component: lazyLoad("Root"),
    meta: { requiresAuth: true },
  },
  {
    path: "/file",
    name: "FileSystem",
    component: lazyLoad("FileSystem"),
    meta: { requiresAuth: true },
  },
  {
    path: "/upload",
    name: "Upload",
    component: lazyLoad("Upload"),
    meta: { requiresAuth: true },
  },
  {
    path: "/vector-status",
    name: "VectorStatus",
    component: lazyLoad("VectorStatus"),
    meta: { requiresAuth: true },
  },
  {
    path: "/:pathMatch(.*)*",
    name: "NotFound",
    component: () => import("../views/NotFound/index.vue"),
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach((to, from, next) => {
  const userStore = useUserStore();
  const hasToken = !!userStore.token;

  // 只拦截明确声明 requiresAuth 的路由
  if (to.meta.requiresAuth && !hasToken) {
    next("/login");
    return;
  }

  // 已登录用户访问登录/注册页
  if (hasToken && (to.path === "/login" || to.path === "/register")) {
    next("/home");
    return;
  }

  next();
});
export default router;
