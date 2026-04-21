<template>
  <el-card class="wb-card wb-program-output-card" shadow="never">
    <template #header>
      <div class="wb-card-head">
        <h3>程序输出</h3>
      </div>
    </template>

    <div class="wb-block wb-block-log-only">
      <pre class="wb-code-block wb-log-scroll wb-program-output-scroll">{{ output || "点击“开始讲解”后将在这里展示程序输出结果。" }}</pre>
    </div>
  </el-card>
</template>

<script setup>
defineProps({
  output: {
    type: String,
    default: ""
  }
});
</script>
