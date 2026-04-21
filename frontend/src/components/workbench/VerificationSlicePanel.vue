<template>
  <section class="wb-verification-subpanel">
    <div class="wb-subpanel-head">
      <h4>关键代码切片</h4>
      <span class="wb-subpanel-meta">
        {{ normalizedLinesText }}
      </span>
    </div>
    <div class="wb-slice-scroll">
      <div
        v-for="row in normalizedRows"
        :key="`${row.line}-${row.code}`"
        class="wb-slice-row"
        :class="{ 'is-focus': row.isFocus }"
      >
        <span class="wb-slice-line">{{ row.line }}</span>
        <code class="wb-slice-code">{{ row.code || " " }}</code>
      </div>
      <div v-if="normalizedRows.length === 0" class="wb-slice-empty">
        当前暂无切片内容。
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed } from "vue";

const props = defineProps({
  slice: {
    type: Object,
    default: () => ({ lines: [], code: "" })
  },
  selectedLine: {
    type: Number,
    default: 1
  }
});

const normalizedRows = computed(() => {
  const lines = Array.isArray(props.slice?.lines) ? props.slice.lines.map((n) => Number(n)).filter(Number.isFinite) : [];
  const codeLines = String(props.slice?.code || "").split("\n");
  if (lines.length === 0 && codeLines.length === 1 && !codeLines[0]) {
    return [];
  }

  if (lines.length === 0) {
    return codeLines.map((code, index) => ({
      line: index + 1,
      code,
      isFocus: Number(props.selectedLine || 1) === index + 1
    }));
  }

  return lines.map((line, index) => ({
    line,
    code: String(codeLines[index] || ""),
    isFocus: Number(props.selectedLine || 1) === line
  }));
});

const normalizedLinesText = computed(() => {
  if (normalizedRows.value.length === 0) {
    return "无";
  }
  const start = normalizedRows.value[0].line;
  const end = normalizedRows.value[normalizedRows.value.length - 1].line;
  return start === end ? `第 ${start} 行` : `第 ${start}-${end} 行`;
});
</script>

<style scoped>
.wb-verification-subpanel {
  border: 1px solid rgba(66, 123, 206, 0.16);
  border-radius: 14px;
  background: #f9fcff;
  padding: 10px;
}

.wb-subpanel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
}

.wb-subpanel-head h4 {
  margin: 0;
  font-size: 13px;
  color: #2e557f;
}

.wb-subpanel-meta {
  font-size: 11px;
  color: #6a84a5;
}

.wb-slice-scroll {
  max-height: 190px;
  overflow: auto;
  display: grid;
  gap: 6px;
  padding-right: 2px;
}

.wb-slice-row {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr);
  gap: 8px;
  border-radius: 10px;
  border: 1px solid rgba(68, 128, 214, 0.12);
  background: #ffffff;
  padding: 6px 8px;
  align-items: center;
}

.wb-slice-row.is-focus {
  border-color: #f0b772;
  background: #fff7eb;
}

.wb-slice-line {
  text-align: center;
  font-size: 11px;
  color: #6a86a8;
}

.wb-slice-code {
  font-family: "JetBrains Mono", "Fira Code", monospace;
  font-size: 12px;
  color: #20456f;
  white-space: pre;
  overflow: hidden;
  text-overflow: ellipsis;
}

.wb-slice-empty {
  color: #6a84a5;
  font-size: 12px;
  padding: 12px 6px;
}
</style>

