<template>
  <div ref="shellRef" class="wb-graph-canvas-shell">
    <div v-if="layoutNodes.length > 0" class="wb-graph-controls-layer">
      <div class="wb-graph-zoom-group">
        <button type="button" class="wb-graph-tool-btn" data-tip="放大" aria-label="放大" @click="zoomIn">
          <el-icon><ZoomInIcon /></el-icon>
        </button>
        <button type="button" class="wb-graph-tool-btn" data-tip="缩小" aria-label="缩小" @click="zoomOut">
          <el-icon><ZoomOutIcon /></el-icon>
        </button>
      </div>

      <div class="wb-graph-nav-pad" aria-label="方向控制">
        <button type="button" class="wb-graph-tool-btn is-nav is-up" data-tip="上移" aria-label="上移" @click="panUp">
          <el-icon><ArrowUpIcon /></el-icon>
        </button>
        <button type="button" class="wb-graph-tool-btn is-nav is-left" data-tip="左移" aria-label="左移" @click="panLeft">
          <el-icon><ArrowLeftIcon /></el-icon>
        </button>
        <span class="wb-graph-nav-center" aria-hidden="true"></span>
        <button type="button" class="wb-graph-tool-btn is-nav is-right" data-tip="右移" aria-label="右移" @click="panRight">
          <el-icon><ArrowRightIcon /></el-icon>
        </button>
        <button type="button" class="wb-graph-tool-btn is-nav is-down" data-tip="下移" aria-label="下移" @click="panDown">
          <el-icon><ArrowDownIcon /></el-icon>
        </button>
      </div>

      <button
        type="button"
        class="wb-graph-tool-btn wb-graph-reset-btn"
        data-tip="重置视图"
        aria-label="重置视图"
        @click="resetView"
      >
        <el-icon><RefreshRightIcon /></el-icon>
      </button>
    </div>

    <svg
      v-if="layoutNodes.length > 0"
      class="wb-graph-svg"
      :viewBox="`0 0 ${viewBoxWidth} ${viewBoxHeight}`"
      preserveAspectRatio="xMidYMid meet"
    >
      <g class="wb-graph-viewport" :transform="viewportTransform">
        <g class="wb-graph-edges">
          <path
            v-for="edge in layoutEdges"
            :key="`${edge.source}->${edge.target}:${edge.type}`"
            :d="edge.path"
            class="wb-graph-edge"
            fill="none"
            :style="{
              stroke: edge.stroke,
              strokeWidth: `${edge.strokeWidth}px`,
              opacity: String(edge.opacity)
            }"
          />
        </g>

        <g class="wb-graph-nodes">
          <g
            v-for="node in layoutNodes"
            :key="node.id"
            class="wb-graph-node"
            @click="handleNodeClick(node)"
            @mouseenter="handleNodeEnter(node, $event)"
            @mousemove="handleNodeMove(node, $event)"
            @mouseleave="hideTooltip"
          >
            <circle
              v-if="node.isActive"
              class="wb-graph-node-active-ring"
              :cx="node.cx"
              :cy="node.cy"
              :r="nodeRadius + 6"
              :stroke="ACTIVE_RING_COLOR"
            />
            <circle
              class="wb-graph-node-core"
              :cx="node.cx"
              :cy="node.cy"
              :r="nodeRadius"
              :style="{
                fill: node.colors.fill,
                stroke: node.colors.stroke,
                strokeWidth: `${node.colors.strokeWidth}px`
              }"
            />
            <text
              :x="node.cx"
              :y="node.cy - 4"
              class="wb-graph-node-type-short"
              :style="{ fill: node.colors.typeText }"
            >
              {{ node.shortType }}
            </text>
            <text
              :x="node.cx"
              :y="node.cy + 13"
              class="wb-graph-node-line-short"
              :style="{ fill: node.colors.lineText }"
            >
              {{ node.lineTag }}
            </text>
          </g>
        </g>
      </g>
    </svg>

    <div v-else class="wb-graph-empty">
      暂无图结构数据，待验证后将自动生成。
    </div>

    <div
      v-if="tooltip.visible"
      class="wb-graph-tooltip"
      :style="{ left: `${tooltip.x}px`, top: `${tooltip.y}px` }"
    >
      <div class="wb-graph-tooltip-title">{{ tooltip.nodeName }}</div>
      <div class="wb-graph-tooltip-row"><span>行号</span><strong>{{ tooltip.lineRange }}</strong></div>
      <div class="wb-graph-tooltip-row"><span>类型</span><strong>{{ tooltip.typeLabel }}</strong></div>
      <div class="wb-graph-tooltip-row"><span>验证角色</span><strong>{{ tooltip.roleLabel }}</strong></div>
      <div class="wb-graph-tooltip-desc">{{ tooltip.explanation }}</div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from "vue";
import {
  ArrowDown as ArrowDownIcon,
  ArrowLeft as ArrowLeftIcon,
  ArrowRight as ArrowRightIcon,
  ArrowUp as ArrowUpIcon,
  RefreshRight as RefreshRightIcon,
  ZoomIn as ZoomInIcon,
  ZoomOut as ZoomOutIcon
} from "@element-plus/icons-vue";

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

const ACTIVE_RING_COLOR = "#1f4f97";
const nodeDiameter = 74;
const nodeRadius = nodeDiameter / 2;
const layoutPadding = 28;

const shellRef = ref(null);
const tooltip = ref({
  visible: false,
  x: 0,
  y: 0,
  nodeName: "",
  lineRange: "",
  typeLabel: "",
  roleLabel: "",
  explanation: ""
});

const viewScale = ref(1);
const viewOffsetX = ref(0);
const viewOffsetY = ref(0);

const TONE_COLORS = {
  normal: {
    fill: "#f1f6fd",
    stroke: "#9fb8d4",
    typeText: "#274b72",
    lineText: "#5c7ea5",
    strokeWidth: 1.3,
    edge: "#a9bfd9"
  },
  pending: {
    fill: "#e8f2ff",
    stroke: "#74a5dc",
    typeText: "#1f4f8a",
    lineText: "#4f78aa",
    strokeWidth: 1.4,
    edge: "#7faee7"
  },
  support: {
    fill: "#e8f6ee",
    stroke: "#6bb48d",
    typeText: "#1b5e3f",
    lineText: "#4f9074",
    strokeWidth: 1.4,
    edge: "#72b993"
  },
  unsupport: {
    fill: "#fdecec",
    stroke: "#d77878",
    typeText: "#8f3030",
    lineText: "#b06464",
    strokeWidth: 1.4,
    edge: "#dd8888"
  }
};

const clamp = (value, min, max) => Math.min(max, Math.max(min, value));

const viewBoxWidth = computed(() => {
  const n = Math.max(1, normalizedNodes.value.length);
  const estimated = 860 + Math.ceil(Math.sqrt(n)) * 160;
  return clamp(estimated, 860, 1680);
});

const viewBoxHeight = computed(() => {
  const n = Math.max(1, normalizedNodes.value.length);
  const estimated = 520 + Math.ceil(n / 4) * 145;
  return clamp(estimated, 520, 1220);
});

const viewportTransform = computed(() => (
  `translate(${viewOffsetX.value} ${viewOffsetY.value}) scale(${viewScale.value})`
));

const zoomBy = (factor) => {
  const oldScale = viewScale.value;
  const nextScale = clamp(oldScale * factor, 0.72, 2.4);
  if (Math.abs(nextScale - oldScale) < 0.0001) {
    return;
  }
  const cx = viewBoxWidth.value / 2;
  const cy = viewBoxHeight.value / 2;
  viewOffsetX.value += (oldScale - nextScale) * cx;
  viewOffsetY.value += (oldScale - nextScale) * cy;
  viewScale.value = nextScale;
};

const panBy = (dx, dy) => {
  const step = 58 / Math.max(0.72, viewScale.value);
  viewOffsetX.value += dx * step;
  viewOffsetY.value += dy * step;
};

const resetView = () => {
  viewScale.value = 1;
  viewOffsetX.value = 0;
  viewOffsetY.value = 0;
};

const zoomIn = () => zoomBy(1.14);
const zoomOut = () => zoomBy(0.88);
const panUp = () => panBy(0, 1);
const panDown = () => panBy(0, -1);
const panLeft = () => panBy(1, 0);
const panRight = () => panBy(-1, 0);

const hashCode = (value) => {
  const text = String(value || "");
  let h = 2166136261;
  for (let i = 0; i < text.length; i += 1) {
    h ^= text.charCodeAt(i);
    h += (h << 1) + (h << 4) + (h << 7) + (h << 8) + (h << 24);
  }
  return Math.abs(h >>> 0);
};

const normalizeNodeTypeLabel = (value) => {
  const type = String(value || "").toLowerCase();
  if (type === "function_entry") return "函数入口";
  if (type === "loop_guard") return "循环条件";
  if (type === "condition") return "条件分支";
  if (type === "candidate_update") return "候选相关更新";
  if (type === "variable_update") return "变量更新";
  if (type === "return") return "返回语句";
  return "结构节点";
};

const shortNodeType = (value) => {
  const type = String(value || "").toLowerCase();
  if (type === "function_entry") return "入口";
  if (type === "loop_guard") return "循环";
  if (type === "condition") return "分支";
  if (type === "candidate_update") return "候选";
  if (type === "variable_update") return "更新";
  if (type === "return") return "返回";
  return "节点";
};

const normalizeToneKey = (status) => {
  const normalized = String(status || "").toLowerCase();
  if (["support", "proof_support", "proved_support", "proved"].includes(normalized)) {
    return "support";
  }
  if (["high-risk", "risk", "not_support", "not-support", "proof_unsupport", "unsupported"].includes(normalized)) {
    return "unsupport";
  }
  if (["focus", "highlight", "pending", "unproved", "key", "critical"].includes(normalized)) {
    return "pending";
  }
  return "normal";
};

const toneRoleLabel = (toneKey) => {
  if (toneKey === "pending") return "未证明关键节点";
  if (toneKey === "support") return "证明支持节点";
  if (toneKey === "unsupport") return "不支持证明节点";
  return "普通结构节点";
};

const formatLineRange = (start, end) => {
  if (start === end) {
    return `第 ${start} 行`;
  }
  return `第 ${start}-${end} 行`;
};

const normalizedNodes = computed(() => {
  const source = Array.isArray(props.graph?.nodes) ? props.graph.nodes : [];
  return source.map((node, index) => {
    const lineStart = Math.max(1, Number(node?.lineStart ?? node?.line_start ?? 1));
    const lineEndRaw = Math.max(1, Number(node?.lineEnd ?? node?.line_end ?? lineStart));
    const lineEnd = Math.max(lineStart, lineEndRaw);
    const toneKey = normalizeToneKey(node?.status);
    return {
      id: String(node?.id || `node-${index + 1}`),
      label: String(node?.label || "结构节点"),
      type: String(node?.type || "statement"),
      lineStart,
      lineEnd,
      status: String(node?.status || "normal"),
      explanation: String(node?.explanation || "暂无补充说明"),
      toneKey
    };
  });
});

const normalizedEdges = computed(() => {
  const rows = Array.isArray(props.graph?.edges) ? props.graph.edges : [];
  const ids = new Set(normalizedNodes.value.map((item) => item.id));
  const edges = rows
    .map((edge) => ({
      source: String(edge?.source || ""),
      target: String(edge?.target || ""),
      type: String(edge?.type || "flow"),
      toneKey: normalizeToneKey(edge?.status)
    }))
    .filter((edge) => edge.source && edge.target && ids.has(edge.source) && ids.has(edge.target));

  if (edges.length > 0) {
    return edges;
  }

  if (normalizedNodes.value.length <= 1) {
    return [];
  }
  const fallback = [];
  for (let i = 0; i < normalizedNodes.value.length - 1; i += 1) {
    fallback.push({
      source: normalizedNodes.value[i].id,
      target: normalizedNodes.value[i + 1].id,
      type: "syntax_flow",
      toneKey: "normal"
    });
  }
  return fallback;
});

watch(
  () => [normalizedNodes.value.length, normalizedEdges.value.length],
  () => {
    resetView();
  }
);

const classifyTier = (node) => {
  const type = String(node.type || "").toLowerCase();
  if (type === "loop_guard" || type === "candidate_update") return "core";
  if (node.toneKey === "pending" || node.toneKey === "unsupport" || node.toneKey === "support") return "mid";
  return "outer";
};

const typeAnchorPoint = (type, centerX, centerY, radius) => {
  const t = String(type || "").toLowerCase();
  if (t === "loop_guard") return { x: centerX - radius * 0.08, y: centerY - radius * 0.08 };
  if (t === "condition") return { x: centerX + radius * 0.5, y: centerY - radius * 0.38 };
  if (t === "candidate_update") return { x: centerX + radius * 0.36, y: centerY + radius * 0.24 };
  if (t === "variable_update") return { x: centerX - radius * 0.42, y: centerY + radius * 0.2 };
  if (t === "return") return { x: centerX + radius * 0.58, y: centerY + radius * 0.46 };
  if (t === "function_entry") return { x: centerX - radius * 0.58, y: centerY - radius * 0.45 };
  return { x: centerX, y: centerY };
};

const baseLayoutMap = computed(() => {
  const nodes = normalizedNodes.value;
  if (nodes.length === 0) {
    return new Map();
  }

  const width = viewBoxWidth.value;
  const height = viewBoxHeight.value;
  const centerX = width / 2;
  const centerY = height / 2;
  const graphRadius = Math.min(width, height) / 2 - layoutPadding - nodeRadius;

  const idToIndex = new Map();
  nodes.forEach((node, index) => idToIndex.set(node.id, index));

  const simEdges = normalizedEdges.value
    .map((edge) => {
      const sourceIndex = idToIndex.get(edge.source);
      const targetIndex = idToIndex.get(edge.target);
      if (sourceIndex == null || targetIndex == null) {
        return null;
      }
      return {
        ...edge,
        sourceIndex,
        targetIndex,
        forceKind: "graph",
        forceWeight: 1
      };
    })
    .filter(Boolean);

  const forceEdges = [...simEdges];
  const edgePairSet = new Set(
    simEdges.map((edge) => {
      const a = Math.min(edge.sourceIndex, edge.targetIndex);
      const b = Math.max(edge.sourceIndex, edge.targetIndex);
      return `${a}-${b}`;
    })
  );
  for (let i = 0; i < nodes.length; i += 1) {
    for (let j = i + 1; j < nodes.length; j += 1) {
      const from = nodes[i];
      const to = nodes[j];
      const lineGap = Math.abs(from.lineStart - to.lineStart);
      const sameTier = classifyTier(from) === classifyTier(to);
      const sameType = String(from.type || "").toLowerCase() === String(to.type || "").toLowerCase();
      const shareFocus =
        ["pending", "support", "unsupport"].includes(from.toneKey)
        && ["pending", "support", "unsupport"].includes(to.toneKey);
      if (!(sameType || shareFocus || (sameTier && lineGap <= 5))) {
        continue;
      }
      const key = `${i}-${j}`;
      if (edgePairSet.has(key)) {
        continue;
      }
      edgePairSet.add(key);
      forceEdges.push({
        source: from.id,
        target: to.id,
        type: "semantic_cluster",
        toneKey: "normal",
        sourceIndex: i,
        targetIndex: j,
        forceKind: "cluster",
        forceWeight: sameType ? 0.7 : 0.56
      });
    }
  }

  const positions = nodes.map((node, index) => {
    const tier = classifyTier(node);
    const seed = hashCode(`${node.id}-${index}`);
    const angle = (seed % 360) * (Math.PI / 180);
    const jitter = ((seed % 17) - 8) * 0.9;
    let radiusScale = 0.74;
    if (tier === "core") radiusScale = 0.24;
    if (tier === "mid") radiusScale = 0.48;
    const targetRadius = graphRadius * radiusScale;
    const anchor = typeAnchorPoint(node.type, centerX, centerY, graphRadius * 0.72);
    return {
      id: node.id,
      targetRadius,
      anchorX: anchor.x,
      anchorY: anchor.y,
      x: centerX + Math.cos(angle) * targetRadius + jitter,
      y: centerY + Math.sin(angle) * targetRadius - jitter
    };
  });

  const velocities = positions.map(() => ({ vx: 0, vy: 0 }));
  const repulsion = 6800;
  const spring = 0.032;
  const centerPull = 0.018;
  const anchorPull = 0.024;
  const damping = 0.82;
  const maxStep = 11;
  const minDist = 22;
  const minNodeDistance = nodeDiameter + 36;

  const applyCollision = () => {
    for (let i = 0; i < positions.length; i += 1) {
      for (let j = i + 1; j < positions.length; j += 1) {
        const dx = positions[j].x - positions[i].x;
        const dy = positions[j].y - positions[i].y;
        const dist = Math.sqrt(dx * dx + dy * dy) || 0.001;
        if (dist >= minNodeDistance) {
          continue;
        }
        const overlap = minNodeDistance - dist;
        const ux = dx / dist;
        const uy = dy / dist;
        const shift = overlap * 0.52;
        positions[i].x -= ux * shift;
        positions[i].y -= uy * shift;
        positions[j].x += ux * shift;
        positions[j].y += uy * shift;
      }
    }
  };

  for (let iter = 0; iter < 320; iter += 1) {
    const forces = positions.map(() => ({ fx: 0, fy: 0 }));

    for (let i = 0; i < positions.length; i += 1) {
      for (let j = i + 1; j < positions.length; j += 1) {
        const dx = positions[j].x - positions[i].x;
        const dy = positions[j].y - positions[i].y;
        const distSq = dx * dx + dy * dy + 0.01;
        const dist = Math.sqrt(distSq);
        const force = repulsion / distSq;
        const ux = dx / dist;
        const uy = dy / dist;
        forces[i].fx -= ux * force;
        forces[i].fy -= uy * force;
        forces[j].fx += ux * force;
        forces[j].fy += uy * force;
      }
    }

    for (let i = 0; i < forceEdges.length; i += 1) {
      const edge = forceEdges[i];
      const from = positions[edge.sourceIndex];
      const to = positions[edge.targetIndex];
      const dx = to.x - from.x;
      const dy = to.y - from.y;
      const dist = Math.max(minDist, Math.sqrt(dx * dx + dy * dy));
      const ux = dx / dist;
      const uy = dy / dist;
      const target = edge.forceKind === "cluster"
        ? 160
        : edge.type === "control_dep" ? 170 : 210;
      const delta = dist - target;
      const f = delta * spring * (Number(edge.forceWeight) || 1);
      forces[edge.sourceIndex].fx += ux * f;
      forces[edge.sourceIndex].fy += uy * f;
      forces[edge.targetIndex].fx -= ux * f;
      forces[edge.targetIndex].fy -= uy * f;
    }

    for (let i = 0; i < positions.length; i += 1) {
      const p = positions[i];
      const dxCenter = p.x - centerX;
      const dyCenter = p.y - centerY;
      const distCenter = Math.max(minDist, Math.sqrt(dxCenter * dxCenter + dyCenter * dyCenter));
      const radialError = distCenter - p.targetRadius;
      const ux = dxCenter / distCenter;
      const uy = dyCenter / distCenter;
      forces[i].fx -= ux * radialError * centerPull;
      forces[i].fy -= uy * radialError * centerPull;
      forces[i].fx += (p.anchorX - p.x) * anchorPull;
      forces[i].fy += (p.anchorY - p.y) * anchorPull;
    }

    for (let i = 0; i < positions.length; i += 1) {
      velocities[i].vx = (velocities[i].vx + forces[i].fx) * damping;
      velocities[i].vy = (velocities[i].vy + forces[i].fy) * damping;

      velocities[i].vx = clamp(velocities[i].vx, -maxStep, maxStep);
      velocities[i].vy = clamp(velocities[i].vy, -maxStep, maxStep);

      positions[i].x += velocities[i].vx;
      positions[i].y += velocities[i].vy;

      positions[i].x = clamp(positions[i].x, layoutPadding + nodeRadius, width - layoutPadding - nodeRadius);
      positions[i].y = clamp(positions[i].y, layoutPadding + nodeRadius, height - layoutPadding - nodeRadius);
    }
    applyCollision();
  }

  for (let relax = 0; relax < 90; relax += 1) {
    applyCollision();
    for (let i = 0; i < positions.length; i += 1) {
      positions[i].x = clamp(positions[i].x, layoutPadding + nodeRadius, width - layoutPadding - nodeRadius);
      positions[i].y = clamp(positions[i].y, layoutPadding + nodeRadius, height - layoutPadding - nodeRadius);
    }
  }

  const map = new Map();
  positions.forEach((item) => {
    map.set(item.id, { x: item.x, y: item.y });
  });
  return map;
});

const resolveNodeColors = (toneKey) => TONE_COLORS[toneKey] || TONE_COLORS.normal;

const layoutNodes = computed(() => {
  const selected = Math.max(1, Number(props.selectedLine || 1));
  const activeId = String(props.activeNodeId || "");
  return normalizedNodes.value.map((node) => {
    const pos = baseLayoutMap.value.get(node.id) || { x: viewBoxWidth.value / 2, y: viewBoxHeight.value / 2 };
    const byLine = selected >= node.lineStart && selected <= node.lineEnd;
    const byId = activeId && activeId === node.id;
    return {
      ...node,
      cx: pos.x,
      cy: pos.y,
      shortType: shortNodeType(node.type),
      lineTag: node.lineStart === node.lineEnd ? `L${node.lineStart}` : `L${node.lineStart}-${node.lineEnd}`,
      typeLabel: normalizeNodeTypeLabel(node.type),
      roleLabel: toneRoleLabel(node.toneKey),
      lineRange: formatLineRange(node.lineStart, node.lineEnd),
      isActive: Boolean(byLine || byId),
      colors: resolveNodeColors(node.toneKey)
    };
  });
});

const nodeMap = computed(() => {
  const map = new Map();
  layoutNodes.value.forEach((node) => map.set(node.id, node));
  return map;
});

const buildStraightEdgePath = (from, to) => {
  const dx = to.cx - from.cx;
  const dy = to.cy - from.cy;
  const dist = Math.max(20, Math.sqrt(dx * dx + dy * dy));
  const ux = dx / dist;
  const uy = dy / dist;
  const startX = from.cx + ux * (nodeRadius + 1);
  const startY = from.cy + uy * (nodeRadius + 1);
  const endX = to.cx - ux * (nodeRadius + 1);
  const endY = to.cy - uy * (nodeRadius + 1);
  return `M ${startX} ${startY} L ${endX} ${endY}`;
};

const layoutEdges = computed(() => {
  const activeId = String(props.activeNodeId || "");
  return normalizedEdges.value
    .map((edge) => {
      const from = nodeMap.value.get(edge.source);
      const to = nodeMap.value.get(edge.target);
      if (!from || !to) {
        return null;
      }
      const isRelated = Boolean(
        from.isActive
        || to.isActive
        || (activeId && (edge.source === activeId || edge.target === activeId))
      );
      const tone = resolveNodeColors(edge.toneKey);
      return {
        source: edge.source,
        target: edge.target,
        type: edge.type,
        path: buildStraightEdgePath(from, to),
        stroke: isRelated ? ACTIVE_RING_COLOR : tone.edge,
        strokeWidth: isRelated ? 4.6 : 3.2,
        opacity: isRelated ? 1 : 0.92
      };
    })
    .filter(Boolean);
});

const placeTooltip = (event) => {
  if (!shellRef.value || !event) {
    return { x: 8, y: 8 };
  }
  const rect = shellRef.value.getBoundingClientRect();
  const rawX = event.clientX - rect.left + 12;
  const rawY = event.clientY - rect.top + 12;
  const maxX = Math.max(8, rect.width - 280);
  const maxY = Math.max(8, rect.height - 130);
  return {
    x: Math.min(maxX, Math.max(8, rawX)),
    y: Math.min(maxY, Math.max(8, rawY))
  };
};

const handleNodeEnter = (node, event) => {
  const pos = placeTooltip(event);
  tooltip.value = {
    visible: true,
    x: pos.x,
    y: pos.y,
    nodeName: node.label,
    lineRange: node.lineRange,
    typeLabel: node.typeLabel,
    roleLabel: node.roleLabel,
    explanation: node.explanation || "暂无补充说明"
  };
};

const handleNodeMove = (node, event) => {
  if (!tooltip.value.visible) {
    handleNodeEnter(node, event);
    return;
  }
  const pos = placeTooltip(event);
  tooltip.value = {
    ...tooltip.value,
    x: pos.x,
    y: pos.y
  };
};

const hideTooltip = () => {
  tooltip.value.visible = false;
};

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
  position: relative;
  width: 100%;
  min-height: 260px;
  border: 1px solid rgba(68, 128, 214, 0.16);
  border-radius: 16px;
  background: radial-gradient(circle at 52% 46%, #f7fbff 0%, #f1f7ff 58%, #eef4ff 100%);
  overflow: hidden;
}

.wb-graph-controls-layer {
  position: absolute;
  inset: 0;
  z-index: 6;
  pointer-events: none;
}

.wb-graph-zoom-group {
  position: absolute;
  top: 10px;
  left: 10px;
  display: flex;
  gap: 5px;
  padding: 5px;
  border-radius: 12px;
  border: 1px solid rgba(82, 134, 208, 0.24);
  background: rgba(249, 252, 255, 0.86);
  box-shadow: 0 8px 18px rgba(32, 84, 151, 0.13);
  backdrop-filter: blur(4px);
  pointer-events: auto;
}

.wb-graph-tool-btn {
  position: relative;
  width: 28px;
  height: 28px;
  border-radius: 10px;
  border: 1px solid rgba(73, 130, 208, 0.3);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.94) 0%, rgba(243, 249, 255, 0.96) 100%);
  color: #29598e;
  font-size: 14px;
  font-weight: 600;
  line-height: 1;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  appearance: none;
  -webkit-appearance: none;
  pointer-events: auto;
  transition: transform 0.16s ease, background 0.16s ease, border-color 0.16s ease, box-shadow 0.16s ease;
  box-shadow: 0 4px 10px rgba(34, 89, 160, 0.14);
}

.wb-graph-tool-btn:hover {
  border-color: rgba(52, 111, 196, 0.48);
  background: linear-gradient(180deg, rgba(255, 255, 255, 1) 0%, rgba(233, 244, 255, 0.98) 100%);
  box-shadow: 0 6px 14px rgba(34, 91, 165, 0.2);
  transform: translateY(-1px);
}

.wb-graph-tool-btn:active {
  transform: translateY(0) scale(0.97);
  box-shadow: 0 2px 8px rgba(34, 91, 165, 0.16);
}

.wb-graph-tool-btn :deep(svg) {
  width: 14px;
  height: 14px;
  stroke-width: 2;
}

.wb-graph-tool-btn::after {
  content: attr(data-tip);
  position: absolute;
  left: 50%;
  bottom: calc(100% + 8px);
  transform: translate(-50%, 4px);
  padding: 4px 8px;
  border-radius: 8px;
  border: 1px solid rgba(74, 132, 209, 0.24);
  background: rgba(255, 255, 255, 0.96);
  color: #2f5e92;
  font-size: 11px;
  line-height: 1;
  white-space: nowrap;
  opacity: 0;
  pointer-events: none;
  box-shadow: 0 6px 14px rgba(30, 78, 139, 0.14);
  transition: opacity 0.14s ease, transform 0.14s ease;
}

.wb-graph-tool-btn:hover::after {
  opacity: 1;
  transform: translate(-50%, 0);
}

.wb-graph-reset-btn {
  position: absolute;
  top: 10px;
  right: 10px;
  width: 30px;
  height: 30px;
}

.wb-graph-reset-btn :deep(svg) {
  width: 15px;
  height: 15px;
}

.wb-graph-nav-pad {
  position: absolute;
  left: 10px;
  bottom: 10px;
  display: grid;
  grid-template-columns: repeat(3, 28px);
  grid-template-rows: repeat(3, 28px);
  gap: 4px;
  padding: 6px;
  border-radius: 14px;
  border: 1px solid rgba(82, 134, 208, 0.24);
  background: rgba(249, 252, 255, 0.86);
  box-shadow: 0 9px 20px rgba(32, 84, 151, 0.14);
  backdrop-filter: blur(4px);
  pointer-events: auto;
}

.wb-graph-tool-btn.is-nav {
  border-radius: 9px;
}

.wb-graph-tool-btn.is-up {
  grid-column: 2;
  grid-row: 1;
}

.wb-graph-tool-btn.is-left {
  grid-column: 1;
  grid-row: 2;
}

.wb-graph-tool-btn.is-right {
  grid-column: 3;
  grid-row: 2;
}

.wb-graph-tool-btn.is-down {
  grid-column: 2;
  grid-row: 3;
}

.wb-graph-nav-center {
  grid-column: 2;
  grid-row: 2;
  width: 100%;
  height: 100%;
  border-radius: 999px;
  border: 1px solid rgba(95, 145, 213, 0.18);
  background: radial-gradient(circle at 35% 35%, rgba(246, 250, 255, 0.95) 0%, rgba(233, 243, 255, 0.8) 100%);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.44);
}

.wb-graph-svg {
  width: 100%;
  height: auto;
  display: block;
}

.wb-graph-viewport {
  transition: transform 0.22s ease;
}

.wb-graph-empty {
  min-height: 260px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #6d88ac;
  font-size: 13px;
}

.wb-graph-edge {
  fill: none;
  stroke-linecap: round;
  transition: stroke 0.16s ease, stroke-width 0.16s ease, opacity 0.16s ease;
}

.wb-graph-node {
  cursor: pointer;
}

.wb-graph-node-active-ring {
  fill: none;
  stroke-width: 2.4;
  opacity: 0.92;
}

.wb-graph-node-core {
  filter: drop-shadow(0 1px 2px rgba(36, 82, 142, 0.12));
  transition: fill 0.16s ease, stroke 0.16s ease;
}

.wb-graph-node text {
  text-anchor: middle;
  dominant-baseline: middle;
  pointer-events: none;
  font-family: "PingFang SC", "Microsoft YaHei", "Noto Sans SC", sans-serif;
}

.wb-graph-node-type-short {
  font-size: 11px;
  font-weight: 600;
}

.wb-graph-node-line-short {
  font-size: 10px;
}

.wb-graph-tooltip {
  position: absolute;
  width: min(264px, calc(100% - 16px));
  border-radius: 12px;
  border: 1px solid rgba(73, 130, 208, 0.2);
  background: rgba(252, 254, 255, 0.97);
  box-shadow: 0 10px 22px rgba(31, 73, 128, 0.14);
  backdrop-filter: blur(2px);
  padding: 10px 12px;
  z-index: 4;
  pointer-events: none;
}

.wb-graph-tooltip-title {
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 600;
  color: #214a77;
  line-height: 1.4;
}

.wb-graph-tooltip-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  margin-bottom: 3px;
  font-size: 11px;
}

.wb-graph-tooltip-row span {
  color: #6884a7;
}

.wb-graph-tooltip-row strong {
  color: #274f7b;
  font-weight: 600;
}

.wb-graph-tooltip-desc {
  margin-top: 6px;
  border-top: 1px solid rgba(73, 130, 208, 0.12);
  padding-top: 6px;
  font-size: 11px;
  line-height: 1.45;
  color: #355d88;
}
</style>
