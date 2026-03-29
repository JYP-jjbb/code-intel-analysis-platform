<template>
  <div class="simulation-panel runtime-panel" :class="{ 'is-active': activated }">
    <div class="simulation-header">
      <div class="simulation-title">Agent决策闭环</div>
      <div class="simulation-subtitle">
        围绕 Observe、Plan、Act、Verify 与 Reflect 展示智能体的感知、规划、执行、校验与反馈修正过程。
      </div>
    </div>

    <div class="simulation-media">
      <img class="media-preview" :src="agentFlowImage" alt="Agent Flow" />
    </div>
  </div>
</template>

<script setup>
import agentFlowImage from "../assets/media/Agent-Flow.png";

defineProps({
  activated: {
    type: Boolean,
    default: false
  }
});
</script>

<style scoped>
.simulation-panel {
  display: grid;
  gap: 16px;
  opacity: 0.92;
  transform: translateY(0);
  transition: transform 0.3s ease, box-shadow 0.3s ease, border-color 0.3s ease, opacity 0.3s ease;
}

.simulation-panel.is-active {
  opacity: 1;
  transform: translateY(-6px);
  border-color: rgba(44, 107, 255, 0.45);
  box-shadow: 0 24px 44px rgba(44, 107, 255, 0.2);
}

.simulation-header {
  display: grid;
  gap: 6px;
}

.simulation-title {
  font-size: 18px;
  font-weight: 600;
  color: #204d8e;
}

.simulation-subtitle {
  font-size: 13px;
  line-height: 1.6;
  color: var(--landing-muted);
}

.simulation-media {
  border-radius: 16px;
  border: 1px dashed rgba(44, 107, 255, 0.3);
  background: linear-gradient(135deg, rgba(242, 247, 255, 0.9), rgba(231, 243, 255, 0.6));
  width: 100%;
  aspect-ratio: 16 / 9;
  display: grid;
  place-items: center;
  overflow: hidden;
}

.media-preview {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
}

@supports not (aspect-ratio: 16 / 9) {
  .simulation-media {
    height: 0;
    padding-top: 56.25%;
    position: relative;
  }

  .media-preview {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
  }
}
</style>
