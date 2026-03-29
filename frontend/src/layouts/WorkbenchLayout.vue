<template>
  <div class="workbench-shell" :class="{ 'is-nutera-route': route.path === '/workbench/nutera' }">
    <div ref="sidebarRef" class="workbench-sidebar">
      <WorkbenchSidebar
        :collapsed="sidebarCollapsed"
        :active-path="activeMenu"
        @toggle="toggleSidebar"
        @select="handleSelect"
      />
    </div>

    <div
      ref="contentRef"
      class="workbench-content-shell"
      :class="{
        'is-nutera-route': route.path === '/workbench/nutera',
        'is-unified-header': isUnifiedHeaderRoute
      }"
    >
      <WorkbenchHeader
        v-if="showWorkbenchHeader"
        :title="headerTitle"
        :description="headerDescription"
        :decor-image="headerDecorImage"
        :decor-alt="headerDecorAlt"
      />
      <main
        class="workbench-main"
        :class="{
          'is-nutera-route': route.path === '/workbench/nutera',
          'is-settings-route': route.path === '/workbench/settings',
          'is-unified-header-route': isUnifiedHeaderRoute
        }"
      >
        <router-view />
      </main>
    </div>

    <HelpModal v-model:visible="helpVisible" />
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import gsap from "gsap";
import WorkbenchHeader from "../components/workbench/WorkbenchHeader.vue";
import WorkbenchSidebar from "../components/workbench/WorkbenchSidebar.vue";
import HelpModal from "../components/workbench/HelpModal.vue";
import { registerWorkbenchHelpOpener } from "../services/workbenchHelpModalService.js";
import codeWordmarkImage from "../assets/media/Code.png";
import projectWordmarkImage from "../assets/media/Project.png";
import historyWordmarkImage from "../assets/media/History.png";
import reportWordmarkImage from "../assets/media/Report.png";

const route = useRoute();
const router = useRouter();

const sidebarCollapsed = ref(false);
const sidebarRef = ref(null);
const contentRef = ref(null);
const helpVisible = ref(false);
const SIDEBAR_EXPANDED_WIDTH = 228;
const SIDEBAR_COLLAPSED_WIDTH = 76;
let unregisterHelpOpener = () => {};

const activeMenu = computed(() => route.path);
const unifiedHeaderRoutes = new Set([
  "/workbench/nutera",
  "/workbench/code-review",
  "/workbench/history",
  "/workbench/reports"
]);
const isUnifiedHeaderRoute = computed(() => unifiedHeaderRoutes.has(route.path));
const showWorkbenchHeader = computed(() => route.path !== "/workbench/settings");

const headerTitle = computed(() => {
  if (route.path === "/workbench/nutera") return "局部代码分析";
  return route.meta.moduleName || "代码智能分析与验证平台";
});

const headerDescription = computed(() => {
  if (isUnifiedHeaderRoute.value || route.path === "/workbench/settings") {
    return "";
  }
  return "";
});

const headerDecorImage = computed(() => {
  const imageByRoute = {
    "/workbench/nutera": codeWordmarkImage,
    "/workbench/code-review": projectWordmarkImage,
    "/workbench/history": historyWordmarkImage,
    "/workbench/reports": reportWordmarkImage
  };
  return imageByRoute[route.path] || "";
});

const headerDecorAlt = computed(() => {
  if (!headerDecorImage.value) {
    return "";
  }
  return `${headerTitle.value} visual`;
});

const applySidebarLayout = (animate = true) => {
  if (!sidebarRef.value || !contentRef.value) return;
  const width = sidebarCollapsed.value ? SIDEBAR_COLLAPSED_WIDTH : SIDEBAR_EXPANDED_WIDTH;
  const detailNodes = sidebarRef.value.querySelectorAll(".wb-sidebar-detail");

  gsap.to(sidebarRef.value, {
    width,
    duration: animate ? 0.44 : 0,
    ease: "power2.out"
  });

  gsap.to(contentRef.value, {
    marginLeft: width,
    duration: animate ? 0.44 : 0,
    ease: "power2.out"
  });

  gsap.to(detailNodes, {
    x: sidebarCollapsed.value ? -8 : 0,
    autoAlpha: sidebarCollapsed.value ? 0 : 1,
    duration: animate ? 0.32 : 0,
    stagger: 0.01,
    ease: "power2.out"
  });
};

const toggleSidebar = () => {
  sidebarCollapsed.value = !sidebarCollapsed.value;
};

const handleSelect = (path) => {
  if (path !== route.path) {
    router.push(path);
  }
};

onMounted(() => {
  unregisterHelpOpener = registerWorkbenchHelpOpener(() => {
    helpVisible.value = true;
  });

  nextTick(() => {
    applySidebarLayout(false);

    const headerEl = contentRef.value?.querySelector("[data-wb-header]");
    if (headerEl) {
      gsap.fromTo(
        headerEl,
        { autoAlpha: 0, y: 14 },
        { autoAlpha: 1, y: 0, duration: 0.48, ease: "power2.out" }
      );
    }
  });
});

onBeforeUnmount(() => {
  unregisterHelpOpener();
});

watch(sidebarCollapsed, () => {
  applySidebarLayout(true);
});
</script>
