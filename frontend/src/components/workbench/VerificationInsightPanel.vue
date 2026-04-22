<template>
  <section class="wb-verification-subpanel">
    <div class="wb-subpanel-head">
      <h4>验证结论说明</h4>
    </div>
    <div class="wb-insight-grid">
      <div class="wb-insight-item">
        <span class="wb-insight-key">当前验证对象</span>
        <span class="wb-insight-value">{{ targetText }}</span>
      </div>
      <div class="wb-insight-item">
        <span class="wb-insight-key">候选函数</span>
        <code class="wb-insight-value wb-mono">{{ candidateFunctionText }}</code>
      </div>
      <div class="wb-insight-item">
        <span class="wb-insight-key">Checker 结论</span>
        <span class="wb-insight-value">{{ checkerConclusionText }}</span>
      </div>
      <div class="wb-insight-item">
        <span class="wb-insight-key">证明状态</span>
        <span class="wb-insight-value" :class="proofToneClass">{{ proofOutcomeText }}</span>
      </div>
      <div class="wb-insight-item wb-insight-item-full">
        <span class="wb-insight-key">失败原因 / 提示</span>
        <span class="wb-insight-value">{{ failureReasonText }}</span>
      </div>
      <div class="wb-insight-item wb-insight-item-full">
        <span class="wb-insight-key">节点语义说明</span>
        <span class="wb-insight-value">{{ highlightExplanationText }}</span>
      </div>
    </div>
    <div class="wb-summary-text">
      {{ summaryText }}
    </div>
  </section>
</template>

<script setup>
import { computed } from "vue";

const props = defineProps({
  summaryData: {
    type: Object,
    default: () => ({})
  },
  fallbackSummaryText: {
    type: String,
    default: ""
  },
  fallbackCandidateFunction: {
    type: String,
    default: ""
  },
  fallbackCheckerConclusion: {
    type: String,
    default: ""
  },
  fallbackVerificationStatus: {
    type: String,
    default: ""
  },
  fallbackCheckerMessage: {
    type: String,
    default: ""
  }
});

const insight = computed(() => props.summaryData?.insight || {});

const targetText = computed(() => String(insight.value?.target || "当前程序核心控制流"));
const candidateFunctionText = computed(() => String(
  props.summaryData?.candidateFunction || props.fallbackCandidateFunction || "（空）"
));
const checkerConclusionText = computed(() => String(
  insight.value?.checkerConclusion || props.fallbackCheckerConclusion || "UNKNOWN"
));
const proofOutcomeText = computed(() => String(
  insight.value?.proofOutcome || props.summaryData?.verificationStatus || props.fallbackVerificationStatus || "NOT_PROVED"
));
const failureReasonText = computed(() => String(
  insight.value?.failureReason || props.fallbackCheckerMessage || "无"
));
const highlightExplanationText = computed(() => String(
  insight.value?.highlightExplanation
  || "灰蓝表示普通结构，浅蓝表示未证明关键点，绿色表示证明支持路径，红色表示不支持证明或可能导致非终止的位置。"
));
const summaryText = computed(() => String(
  props.summaryData?.summaryText || props.fallbackSummaryText || "暂无结构化摘要。"
));

const proofToneClass = computed(() => {
  const normalized = String(proofOutcomeText.value || "").toUpperCase();
  if (normalized === "PROVED") return "is-good";
  if (normalized === "NOT_PROVED") return "is-risk";
  if (normalized === "STOP") return "is-warn";
  return "";
});
</script>

<style scoped>
.wb-verification-subpanel {
  border: 1px solid rgba(66, 123, 206, 0.16);
  border-radius: 14px;
  background: #f9fcff;
  padding: 10px;
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}

.wb-subpanel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  flex-shrink: 0;
}

.wb-subpanel-head h4 {
  margin: 0;
  font-size: 13px;
  color: #2e557f;
}

.wb-insight-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 10px;
  flex-shrink: 0;
}

.wb-insight-item {
  display: grid;
  gap: 2px;
}

.wb-insight-item-full {
  grid-column: 1 / -1;
}

.wb-insight-key {
  font-size: 11px;
  color: #6c84a4;
}

.wb-insight-value {
  font-size: 12px;
  color: #264d78;
  line-height: 1.45;
}

.wb-insight-value.wb-mono {
  font-family: "JetBrains Mono", "Fira Code", monospace;
}

.wb-insight-value.is-good {
  color: #16703b;
}

.wb-insight-value.is-risk {
  color: #b13a3a;
}

.wb-insight-value.is-warn {
  color: #9c6a09;
}

.wb-summary-text {
  margin-top: 10px;
  border-radius: 10px;
  border: 1px solid rgba(66, 123, 206, 0.14);
  background: #ffffff;
  padding: 8px 10px;
  font-size: 12px;
  line-height: 1.55;
  color: #2c4f77;
  white-space: pre-wrap;
  word-break: break-word;
  flex: 1 1 auto;
  min-height: 0;
}
</style>
