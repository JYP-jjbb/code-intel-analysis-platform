<template>
  <el-card
    class="wb-card wb-program-output-card"
    :class="{ 'is-collapsed': collapsed }"
    shadow="never"
  >
    <template #header>
      <div class="wb-card-head wb-card-head-split">
        <h3>输入与输出</h3>
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
      <div class="wb-collapsible-inner wb-io-collapsible-inner">
        <div class="wb-io-split">
          <section class="wb-io-pane" aria-label="标准输入">
            <div class="wb-io-pane-head wb-io-pane-head-with-tip">
              <span>标准输入 (stdin)</span>
              <span class="wb-io-pane-tip">{{ stdinHint }}</span>
            </div>
            <textarea
              class="wb-io-pane-body wb-io-pane-scroll wb-io-stdin-editor"
              :value="stdin"
              placeholder="按行输入程序运行所需数据；无需输入时可留空"
              @input="$emit('update:stdin', $event.target.value)"
            />
          </section>
          <section class="wb-io-pane" aria-label="输出">
            <div class="wb-io-pane-head">输出</div>
            <pre class="wb-io-pane-body wb-io-pane-scroll wb-program-output-scroll">{{ displayOutputPane }}</pre>
          </section>
        </div>
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { computed } from "vue";
import { ArrowDown } from "@element-plus/icons-vue";
import { detectNeedsRuntimeStdin } from "../../utils/detectRuntimeStdin.js";

const props = defineProps({
  /** Current editor source; used only for stdin-need heuristic, never shown verbatim here. */
  sourceCode: {
    type: String,
    default: ""
  },
  language: {
    type: String,
    default: ""
  },
  stdin: {
    type: String,
    default: ""
  },
  /** Real stdout/stderr from a run, when available */
  output: {
    type: String,
    default: ""
  },
  collapsed: {
    type: Boolean,
    default: false
  }
});

defineEmits(["toggle-collapse", "update:stdin"]);

const STDIN_NONE_HINT = "检测为可选输入，可留空";
const STDIN_NEED_HINT = "检测到程序可能读取输入，建议填写测试数据";
const OUTPUT_EMPTY_HINT = "暂无输出\n运行后显示结果";

const needsStdin = computed(() =>
  detectNeedsRuntimeStdin(props.sourceCode, props.language)
);

const stdinHint = computed(() =>
  needsStdin.value ? STDIN_NEED_HINT : STDIN_NONE_HINT
);

const displayOutputPane = computed(() => {
  const t = String(props.output || "").trim();
  return t || OUTPUT_EMPTY_HINT;
});
</script>

<style scoped>
.wb-io-collapsible-inner {
  display: flex;
  flex-direction: column;
  min-height: 0;
  max-height: 100%;
  flex: 1 1 0;
  overflow: hidden;
}

.wb-io-split {
  flex: 1 1 0;
  min-height: 0;
  max-height: 100%;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  grid-template-rows: minmax(0, 1fr);
  gap: 10px;
  align-items: stretch;
  align-content: stretch;
}

.wb-io-pane {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  max-height: 100%;
  overflow: hidden;
  border-radius: 14px;
  border: 1px solid rgba(44, 107, 255, 0.14);
  background: linear-gradient(180deg, #fbfdff 0%, #f5f9ff 100%);
  box-shadow: 0 6px 16px rgba(32, 84, 151, 0.06);
  padding: 10px 10px 8px;
  box-sizing: border-box;
}

.wb-io-pane-head {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 600;
  color: #325987;
  margin: 0 0 8px;
  letter-spacing: 0.02em;
}

.wb-io-pane-head-with-tip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.wb-io-pane-tip {
  font-size: 11px;
  font-weight: 500;
  color: rgba(63, 107, 173, 0.88);
  letter-spacing: 0.01em;
  text-align: right;
}

.wb-io-pane-body {
  flex: 1 1 0;
  min-height: 0;
  margin: 0;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid rgba(44, 107, 255, 0.1);
  background: #f7fbff;
  color: #20466f;
  font-size: 12px;
  line-height: 1.56;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: "JetBrains Mono", "Fira Code", monospace;
  overflow: auto;
  overflow-x: hidden;
  box-sizing: border-box;
}

.wb-io-stdin-editor {
  resize: none;
  outline: none;
}

.wb-io-stdin-editor:focus {
  border-color: rgba(44, 107, 255, 0.32);
  box-shadow: 0 0 0 2px rgba(78, 132, 233, 0.12);
}

.wb-io-pane-scroll {
  scrollbar-gutter: stable;
  scrollbar-width: thin;
  scrollbar-color: transparent transparent;
}

.wb-io-pane-scroll:hover {
  scrollbar-color: rgba(68, 125, 207, 0.32) transparent;
}

.wb-io-pane-scroll::-webkit-scrollbar {
  width: 6px;
}

.wb-io-pane-scroll::-webkit-scrollbar-track {
  background: transparent;
}

.wb-io-pane-scroll::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: transparent;
  transition: background-color 0.2s ease;
}

.wb-io-pane-scroll:hover::-webkit-scrollbar-thumb {
  background: rgba(68, 125, 207, 0.32);
}

@media (max-width: 900px) {
  .wb-io-split {
    grid-template-columns: 1fr;
    grid-template-rows: minmax(0, 1fr) minmax(0, 1fr);
    min-height: 0;
  }

  .wb-io-pane {
    min-height: 0;
    max-height: none;
  }

  .wb-io-pane-body {
    min-height: 0;
    max-height: min(38vh, 320px);
  }
}
</style>
