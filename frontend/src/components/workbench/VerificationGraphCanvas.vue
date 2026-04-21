<template>
  <div class="wb-graph-canvas-shell">
    <svg
      v-if="layoutNodes.length > 0"
      class="wb-graph-svg"
      :viewBox="`0 0 ${viewBoxWidth} ${viewBoxHeight}`"
      preserveAspectRatio="xMidYMin meet"
    >
      <defs>
        <marker id="wb-graph-arrow" markerWidth="10" markerHeight="10" refX="8" refY="3" orient="auto">
          <path d="M0,0 L0,6 L9,3 z" fill="#7da1d1" />
        </marker>
      </defs>

      <g class="wb-graph-edges">
        <path
          v-for="edge in layoutEdges"
          :key="`${edge.source}->${edge.target}:${edge.type}`"
          :d="edge.path"
          class="wb-graph-edge"
          :class="edge.toneClass"
          marker-end="url(#wb-graph-arrow)"
        />
      </g>

      <g class="wb-graph-nodes">
        <g
          v-for="node in layoutNodes"
          :key="node.id"
          class="wb-graph-node"
          :class="[node.toneClass, { 'is-active': node.isActive }]"
          @click="handleNodeClick(node)"
        >
          <title>{{ node.tooltip }}</title>
          <rect :x="node.x" :y="node.y" :width="nodeWidth" :height="nodeHeight" rx="14" ry="14" />
          <text :x="node.x + 12" :y="node.y + 22" class="wb-graph-node-type">
            {{ node.typeLabel }}
          </text>
          <text :x="node.x + 12" :y="node.y + 44" class="wb-graph-node-label">
            {{ node.label }}
          </text>
          <text :x="node.x + 12" :y="node.y + 66" class="wb-graph-node-line">
            第 {{ node.lineStart }}{{ node.lineEnd !== node.lineStart ? `-${node.lineEnd}` : "" }} 行
          </text>
        </g>
      </g>
    </svg>
    <div v-else class="wb-graph-empty">
      暂无图结构数据，待验证后将自动生成。
    </div>
  </div>
</template>

<script setup>
import { computed } from "vue";

const props = defineProps({
  graph: {
    type: Object,
    default: () => ({ nodes: [], edges: [] })
  },
  selectedLine: {
    type: Number,
    default: 1
  },
  activeNodeId: {
    type: String,
    default: ""
  }
});

const emit = defineEmits(["node-click"]);

const nodeWidth = 220;
const nodeHeight = 78;
const colCount = 3;
const colGap = 24;
const rowGap = 24;
const padding = 18;

const normalizeNodeTypeLabel = (value) => {
  const type = String(value || "").toLowerCase();
  if (type === "function_entry") return "函数入口";
  if (type === "loop_guard") return "循环条件";
  if (type === "condition") return "分支判断";
  if (type === "candidate_update") return "候选相关更新";
  if (type === "variable_update") return "变量更新";
  if (type === "return") return "返回语句";
  return "结构节点";
};

const nodeToneClass = (status) => {
  const normalized = String(status || "").toLowerCase();
  if (normalized === "high-risk" || normalized === "risk") return "is-risk";
  if (normalized === "support") return "is-support";
  if (normalized === "focus" || normalized === "highlight") return "is-focus";
  return "is-normal";
};

const edgeToneClass = (status) => {
  const normalized = String(status || "").toLowerCase();
  if (normalized === "risk") return "is-risk";
  if (normalized === "highlight") return "is-focus";
  return "is-normal";
};

const normalizedNodes = computed(() => {
  const source = Array.isArray(props.graph?.nodes) ? props.graph.nodes : [];
  return source.map((node, index) => {
    const lineStart = Math.max(1, Number(node?.lineStart ?? node?.line_start ?? 1));
    const endRaw = Math.max(1, Number(node?.lineEnd ?? node?.line_end ?? lineStart));
    const lineEnd = Math.max(lineStart, endRaw);
    return {
      id: String(node?.id || `node-${index + 1}`),
      label: String(node?.label || "结构节点"),
      type: String(node?.type || "statement"),
      lineStart,
      lineEnd,
      status: String(node?.status || "normal"),
      explanation: String(node?.explanation || "")
    };
  });
});

const viewBoxWidth = computed(() => padding * 2 + colCount * nodeWidth + (colCount - 1) * colGap);
const viewBoxHeight = computed(() => {
  const rows = Math.max(1, Math.ceil(normalizedNodes.value.length / colCount));
  return padding * 2 + rows * nodeHeight + (rows - 1) * rowGap;
});

const layoutNodes = computed(() => {
  const selected = Math.max(1, Number(props.selectedLine || 1));
  return normalizedNodes.value.map((node, index) => {
    const col = index % colCount;
    const row = Math.floor(index / colCount);
    const x = padding + col * (nodeWidth + colGap);
    const y = padding + row * (nodeHeight + rowGap);
    const byLine = selected >= node.lineStart && selected <= node.lineEnd;
    const byId = props.activeNodeId && props.activeNodeId === node.id;
    return {
      ...node,
      x,
      y,
      cx: x + nodeWidth / 2,
      cy: y + nodeHeight / 2,
      typeLabel: normalizeNodeTypeLabel(node.type),
      toneClass: nodeToneClass(node.status),
      isActive: byLine || byId,
      tooltip: `${normalizeNodeTypeLabel(node.type)} | 第 ${node.lineStart}-${node.lineEnd} 行 | ${node.explanation || "无说明"}`
    };
  });
});

const nodeMap = computed(() => {
  const map = new Map();
  layoutNodes.value.forEach((node) => map.set(node.id, node));
  return map;
});

const layoutEdges = computed(() => {
  const source = Array.isArray(props.graph?.edges) ? props.graph.edges : [];
  return source
    .map((edge) => {
      const sourceId = String(edge?.source || "");
      const targetId = String(edge?.target || "");
      const from = nodeMap.value.get(sourceId);
      const to = nodeMap.value.get(targetId);
      if (!from || !to) {
        return null;
      }
      const x1 = from.cx;
      const y1 = from.y + nodeHeight;
      const x2 = to.cx;
      const y2 = to.y;
      const c1y = y1 + 18;
      const c2y = y2 - 18;
      const path = `M ${x1} ${y1} C ${x1} ${c1y}, ${x2} ${c2y}, ${x2} ${y2}`;
      return {
        source: sourceId,
        target: targetId,
        type: String(edge?.type || "flow"),
        path,
        toneClass: edgeToneClass(edge?.status)
      };
    })
    .filter(Boolean);
});

const handleNodeClick = (node) => {
  emit("node-click", {
    id: node.id,
    lineStart: node.lineStart,
    lineEnd: node.lineEnd,
    type: node.type,
    label: node.label,
    status: node.status,
    explanation: node.explanation
  });
};
</script>

<style scoped>
.wb-graph-canvas-shell {
  width: 100%;
  min-height: 220px;
  border: 1px solid rgba(68, 128, 214, 0.18);
  border-radius: 16px;
  background: linear-gradient(180deg, #fafdff 0%, #f3f8ff 100%);
  overflow: hidden;
}

.wb-graph-svg {
  width: 100%;
  height: auto;
  display: block;
}

.wb-graph-empty {
  min-height: 220px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #6983a5;
  font-size: 13px;
}

.wb-graph-edge {
  fill: none;
  stroke-width: 2;
  stroke: #9fb8da;
  opacity: 0.86;
}

.wb-graph-edge.is-focus {
  stroke: #f1a957;
}

.wb-graph-edge.is-risk {
  stroke: #e66c6c;
}

.wb-graph-node {
  cursor: pointer;
}

.wb-graph-node rect {
  fill: #f7fbff;
  stroke: #a9c4e8;
  stroke-width: 1.4;
}

.wb-graph-node.is-normal rect {
  fill: #f7fbff;
  stroke: #a9c4e8;
}

.wb-graph-node.is-focus rect {
  fill: #fff3e2;
  stroke: #f0ac5f;
}

.wb-graph-node.is-risk rect {
  fill: #ffecec;
  stroke: #e87e7e;
}

.wb-graph-node.is-support rect {
  fill: #e9f8ef;
  stroke: #7bcf95;
}

.wb-graph-node.is-active rect {
  stroke-width: 2.2;
  filter: drop-shadow(0 0 5px rgba(66, 125, 210, 0.25));
}

.wb-graph-node text {
  fill: #2f547f;
  font-size: 11px;
  font-family: "PingFang SC", "Microsoft YaHei", "Noto Sans SC", sans-serif;
}

.wb-graph-node-type {
  fill: #5b7ea8;
  font-weight: 600;
}

.wb-graph-node-label {
  fill: #1e4168;
  font-size: 12px;
}

.wb-graph-node-line {
  fill: #6a86a8;
}
</style>

