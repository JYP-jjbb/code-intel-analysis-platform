import { createRouter, createWebHistory } from "vue-router";
import Home from "../pages/Home.vue";
import WorkbenchLayout from "../layouts/WorkbenchLayout.vue";
import NuteraWorkspace from "../pages/NuteraWorkspace.vue";
import CodeReviewWorkspace from "../pages/CodeReviewWorkspace.vue";
import HistoryPage from "../pages/HistoryPage.vue";
import ReportsPage from "../pages/ReportsPage.vue";
import SettingsPage from "../pages/SettingsPage.vue";

const routes = [
  {
    path: "/",
    name: "home",
    component: Home
  },
  {
    path: "/workbench",
    component: WorkbenchLayout,
    children: [
      {
        path: "",
        redirect: "/workbench/nutera"
      },
      {
        path: "nutera",
        name: "nutera",
        component: NuteraWorkspace,
        meta: { moduleName: "局部代码分析" }
      },
      {
        path: "code-review",
        name: "code-review",
        component: CodeReviewWorkspace,
        meta: { moduleName: "工程代码审查" }
      },
      {
        path: "history",
        name: "history",
        component: HistoryPage,
        meta: { moduleName: "历史任务" }
      },
      {
        path: "reports",
        name: "reports",
        component: ReportsPage,
        meta: { moduleName: "报告中心" }
      },
      {
        path: "settings",
        name: "settings",
        component: SettingsPage,
        meta: { moduleName: "系统设置" }
      }
    ]
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

export default router;
