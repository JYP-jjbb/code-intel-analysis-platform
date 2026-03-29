<template>
  <el-dialog
    v-model="dialogVisible"
    class="wb-help-modal"
    width="680px"
    :show-close="true"
    :close-on-click-modal="true"
    :close-on-press-escape="true"
    :append-to-body="true"
    destroy-on-close
  >
    <template #header>
      <div class="wb-help-modal-head">
        <h3>帮助中心</h3>
        <p>快速了解当前页面的输入方式、运行配置与结果查看路径。</p>
      </div>
    </template>

    <div class="wb-help-modal-body">
      <section class="wb-help-block">
        <h4>本页用途说明</h4>
        <p>用于提交局部代码分析任务，执行终止性验证并输出候选函数与 Checker 反馈。</p>
      </section>

      <section class="wb-help-block">
        <h4>如何输入代码 / 上传文件</h4>
        <p>左侧“验证任务配置”中可直接粘贴代码，或通过文件上传导入待验证代码片段。</p>
      </section>

      <section class="wb-help-block">
        <h4>Benchmark 与运行模式</h4>
        <p>根据任务规模选择 Benchmark；运行模式用于切换标准运行、阶段校验或手动 rank 检查。</p>
      </section>

      <section class="wb-help-block">
        <h4>参数配置示例</h4>
        <p><code>--max-attempts 5 --timeout 60</code></p>
      </section>

      <section class="wb-help-block">
        <h4>结果查看位置</h4>
        <p>任务启动后，右侧上方查看运行日志；下方查看候选函数、Checker 反馈与结果文件摘要。</p>
      </section>
    </div>
  </el-dialog>
</template>

<script setup>
import { computed } from "vue";

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(["update:visible"]);

const dialogVisible = computed({
  get: () => props.visible,
  set: (value) => emit("update:visible", value)
});
</script>
