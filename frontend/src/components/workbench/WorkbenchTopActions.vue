<template>
  <div class="wb-top-actions" role="group" aria-label="工作台顶部操作">
    <WorkbenchIconButton
      v-if="showSettings"
      label="系统设置"
      :paths="GEAR_ICON_PATHS"
      @click="goTo(settingsPath)"
    />

    <WorkbenchIconButton
      label="帮助中心"
      :paths="HELP_ICON_PATHS"
      @click="openHelp"
    />
  </div>
</template>

<script setup>
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import WorkbenchIconButton from "./WorkbenchIconButton.vue";
import { GEAR_ICON_PATHS, HELP_ICON_PATHS } from "./icons/iconPaths.js";
import { openWorkbenchHelpModal } from "../../services/workbenchHelpModalService.js";

const props = defineProps({
  settingsPath: {
    type: String,
    default: "/workbench/settings"
  }
});

const route = useRoute();
const router = useRouter();

const showSettings = computed(() => route.path !== props.settingsPath);

const goTo = (path) => {
  if (!path || route.path === path) return;
  router.push(path);
};

const openHelp = () => {
  openWorkbenchHelpModal();
};
</script>
