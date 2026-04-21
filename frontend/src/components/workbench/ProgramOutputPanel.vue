<template>
  <el-card
    class="wb-card wb-program-output-card"
    :class="{ 'is-collapsed': collapsed }"
    shadow="never"
  >
    <template #header>
      <div class="wb-card-head wb-card-head-split">
        <h3>程序输出</h3>
        <button
          class="wb-collapse-btn"
          :class="{ 'is-collapsed': collapsed }"
          :title="collapsed ? '展开' : '收起'"
          @click="$emit('toggle-collapse')"
        >
          <el-icon><ArrowDown /></el-icon>
        </button>
      </div>
    </template>

    <div class="wb-collapsible-wrap" :class="{ 'is-collapsed': collapsed }">
      <div class="wb-collapsible-inner">
        <div class="wb-block wb-block-log-only">
          <pre class="wb-code-block wb-log-scroll wb-program-output-scroll">{{ output || '点击"开始讲解"后将在这里展示程序输出结果。' }}</pre>
        </div>
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { ArrowDown } from "@element-plus/icons-vue";

defineProps({
  output: {
    type: String,
    default: ""
  },
  collapsed: {
    type: Boolean,
    default: false
  }
});

defineEmits(["toggle-collapse"]);
</script>
