<template>
  <header ref="headerRef" class="wb-header-card" data-wb-header>
    <div class="wb-header-leading">
      <div class="wb-header-main">
        <h1 class="wb-header-title">{{ title }}</h1>
        <p v-if="description" class="wb-header-desc">{{ description }}</p>
      </div>
      <img v-if="decorImage" class="wb-header-decor" :src="decorImage" :alt="decorAlt" />
    </div>
    <WorkbenchTopActions class="wb-header-actions" />
  </header>
</template>

<script setup>
import WorkbenchTopActions from "./WorkbenchTopActions.vue";

defineProps({
  title: {
    type: String,
    default: "局部代码分析"
  },
  description: {
    type: String,
    default: ""
  },
  decorImage: {
    type: String,
    default: ""
  },
  decorAlt: {
    type: String,
    default: ""
  }
});
</script>
