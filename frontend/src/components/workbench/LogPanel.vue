<template>
  <el-card class="wb-card wb-log-card" shadow="never">
    <template #header>
      <div class="wb-card-head">
        <h3>运行日志</h3>
      </div>
    </template>

    <div class="wb-block wb-block-log-only">
      <pre
        ref="logRef"
        class="wb-code-block wb-log-scroll"
        @scroll="handleScroll"
      >{{ logs || "执行日志将在任务运行后显示在这里" }}</pre>
    </div>
  </el-card>
</template>

<script setup>
import { nextTick, onMounted, ref, watch } from "vue";

const props = defineProps({
  logs: {
    type: String,
    default: ""
  }
});

const logRef = ref(null);
const autoStickToBottom = ref(true);

const isNearBottom = (element) => {
  if (!element) return true;
  const threshold = 24;
  return element.scrollTop + element.clientHeight >= element.scrollHeight - threshold;
};

const handleScroll = () => {
  if (!logRef.value) return;
  autoStickToBottom.value = isNearBottom(logRef.value);
};

const scrollToBottom = () => {
  if (!logRef.value) return;
  logRef.value.scrollTop = logRef.value.scrollHeight;
};

watch(
  () => props.logs,
  () => {
    nextTick(() => {
      if (autoStickToBottom.value) {
        scrollToBottom();
      }
    });
  }
);

onMounted(() => {
  nextTick(scrollToBottom);
});
</script>
