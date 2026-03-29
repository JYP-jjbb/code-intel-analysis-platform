<template>
  <button
    type="button"
    class="wb-action-icon-btn"
    :aria-label="label"
    :title="label"
    @click="$emit('click')"
  >
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path v-for="(segment, idx) in paths" :key="idx" :d="segment" />
    </svg>
  </button>
</template>

<script setup>
defineProps({
  label: {
    type: String,
    required: true
  },
  paths: {
    type: Array,
    default: () => []
  }
});

defineEmits(["click"]);
</script>
