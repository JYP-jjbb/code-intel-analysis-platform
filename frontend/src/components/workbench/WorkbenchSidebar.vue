<template>
  <aside class="wb-sidebar-shell" :class="{ 'is-collapsed': collapsed }">
    <div class="wb-sidebar-axis">
      <div class="wb-sidebar-top">
        <button
          type="button"
          class="wb-collapse-btn"
          :aria-label="collapsed ? '展开侧边栏' : '收起侧边栏'"
          @click="$emit('toggle')"
        >
          <span class="wb-nav-icon-text">{{ collapsed ? ">" : "<" }}</span>
        </button>
      </div>

      <nav class="wb-nav-list">
        <template v-for="item in menuItems" :key="item.path">
          <el-tooltip
            v-if="collapsed"
            :content="item.label"
            placement="right"
            :show-after="120"
          >
            <button
              type="button"
              class="wb-nav-item"
              :class="{ 'is-active': activePath === item.path, 'is-collapsed': collapsed }"
              @click="$emit('select', item.path)"
            >
              <span class="wb-nav-short">{{ item.short }}</span>
              <span class="wb-sidebar-detail">
                <span class="wb-sidebar-label">{{ item.label }}</span>
                <span class="wb-nav-tail" aria-hidden="true">
                  <svg class="wb-nav-tail-icon" viewBox="0 0 24 24">
                    <path v-for="(segment, idx) in item.iconSegments" :key="idx" :d="segment"></path>
                  </svg>
                </span>
              </span>
            </button>
          </el-tooltip>

          <button
            v-else
            type="button"
            class="wb-nav-item"
            :class="{ 'is-active': activePath === item.path, 'is-collapsed': collapsed }"
            @click="$emit('select', item.path)"
          >
            <span class="wb-nav-short">{{ item.short }}</span>
            <span class="wb-sidebar-detail">
              <span class="wb-sidebar-label">{{ item.label }}</span>
              <span class="wb-nav-tail" aria-hidden="true">
                <svg class="wb-nav-tail-icon" viewBox="0 0 24 24">
                  <path v-for="(segment, idx) in item.iconSegments" :key="idx" :d="segment"></path>
                </svg>
              </span>
            </span>
          </button>
        </template>
      </nav>
    </div>
  </aside>
</template>

<script setup>
import { GEAR_ICON_PATHS } from "./icons/iconPaths.js";

defineProps({
  collapsed: {
    type: Boolean,
    default: false
  },
  activePath: {
    type: String,
    default: ""
  }
});

defineEmits(["toggle", "select"]);

const menuItems = [
  {
    path: "/workbench/nutera",
    label: "局部代码分析",
    short: "L",
    iconSegments: ["M4 18h16", "M6 18V6h12v12", "M8 10h8", "M8 14h5"]
  },
  {
    path: "/workbench/code-review",
    label: "工程代码审查",
    short: "P",
    iconSegments: ["M4 7h16", "M7 7v10", "M17 7v10", "M4 17h16", "M10 11h4"]
  },
  {
    path: "/workbench/history",
    label: "历史任务",
    short: "H",
    iconSegments: ["M12 7v5l3 2", "M12 3a9 9 0 1 0 9 9", "M12 3v2", "M3 12h2"]
  },
  {
    path: "/workbench/reports",
    label: "报告中心",
    short: "R",
    iconSegments: ["M6 3h9l3 3v15H6z", "M15 3v3h3", "M9 11h6", "M9 15h6"]
  },
  {
    path: "/workbench/settings",
    label: "系统设置",
    short: "S",
    iconSegments: GEAR_ICON_PATHS
  }
];
</script>
