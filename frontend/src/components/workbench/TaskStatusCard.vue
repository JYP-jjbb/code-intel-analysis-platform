<template>
  <el-card class="wb-card wb-status-card" shadow="never">
    <template #header>
      <div class="wb-card-head">
        <h3>任务状态总览</h3>
        <p>实时追踪当前验证任务状态与关键时间信息。</p>
      </div>
    </template>

    <div class="wb-status-top">
      <span class="wb-status-label">状态</span>
      <span class="wb-status-pill" :class="`is-${statusMeta.tone}`">{{ statusMeta.label }}</span>
    </div>

    <div class="wb-status-grid">
      <div class="wb-status-item">
        <span class="wb-status-key">Task ID</span>
        <span class="wb-status-value">{{ task.taskId || "-" }}</span>
      </div>
      <div class="wb-status-item">
        <span class="wb-status-key">创建时间</span>
        <span class="wb-status-value">{{ task.createdAt || "-" }}</span>
      </div>
      <div class="wb-status-item">
        <span class="wb-status-key">更新时间</span>
        <span class="wb-status-value">{{ task.updatedAt || "-" }}</span>
      </div>
      <div class="wb-status-item wb-status-item-full">
        <span class="wb-status-key">状态消息</span>
        <span class="wb-status-value">{{ task.message || "-" }}</span>
      </div>
    </div>
  </el-card>
</template>

<script setup>
defineProps({
  task: {
    type: Object,
    required: true
  },
  statusMeta: {
    type: Object,
    required: true
  }
});
</script>
