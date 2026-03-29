<template>
  <router-view />
  <PageTransitionOverlay ref="pageTransitionOverlayRef" />
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from "vue";
import PageTransitionOverlay from "./components/PageTransitionOverlay.vue";
import { registerPageTransitionPlayer } from "./services/pageTransitionService";

const pageTransitionOverlayRef = ref(null);

onMounted(() => {
  registerPageTransitionPlayer(pageTransitionOverlayRef.value);
});

onBeforeUnmount(() => {
  registerPageTransitionPlayer(null);
});
</script>

