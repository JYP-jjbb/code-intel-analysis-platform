<template>
  <div class="workspace-split">
    <el-card shadow="hover">
      <template #header>
        <strong>操作区</strong>
      </template>
      <slot name="operation" />
    </el-card>
    <el-card shadow="hover">
      <template #header>
        <strong>显示区</strong>
      </template>
      <slot name="display" />
    </el-card>
  </div>
</template>

<script setup>
</script>
