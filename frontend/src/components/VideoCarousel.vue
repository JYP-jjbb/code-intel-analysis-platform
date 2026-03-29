<template>
  <div class="runtime-panel video-carousel">
    <div class="carousel-stage">
      <div
        v-for="(slide, index) in slides"
        :key="slide.title"
        class="carousel-slide"
        :class="{ active: index === activeIndex }"
      >
        <div class="carousel-surface">
          <div class="overlay-grid"></div>
          <div class="overlay-titlebar">
            <span class="title-dot"></span>
            <span class="title-dot"></span>
            <span class="title-dot"></span>
            <div class="title-block"></div>
          </div>
          <div class="overlay-panels">
            <div class="panel-card"></div>
            <div class="panel-card"></div>
            <div class="panel-card"></div>
          </div>
          <div class="overlay-flow">
            <span class="flow-node"></span>
            <span class="flow-node"></span>
            <span class="flow-node"></span>
            <span class="flow-line"></span>
          </div>
          <div class="overlay-window">
            <div class="window-bar">
              <span></span>
              <span></span>
              <span></span>
            </div>
            <div class="window-body">
              <div class="window-row"></div>
              <div class="window-row"></div>
              <div class="window-row"></div>
            </div>
          </div>
        </div>
      </div>
      <button class="carousel-nav prev" type="button" @click="goPrev" aria-label="上一项">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M15 6l-6 6 6 6"></path>
        </svg>
      </button>
      <button class="carousel-nav next" type="button" @click="goNext" aria-label="下一项">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M9 6l6 6-6 6"></path>
        </svg>
      </button>
      <div class="carousel-dots" role="tablist" aria-label="轮播指示器">
        <button
          v-for="(slide, index) in slides"
          :key="slide.title + index"
          class="carousel-dot"
          :class="{ active: index === activeIndex }"
          type="button"
          :aria-label="`切换到第 ${index + 1} 项`"
          @click="setActive(index)"
        ></button>
      </div>
    </div>

    <div class="carousel-meta">
      <div class="carousel-title">{{ slides[activeIndex].title }}</div>
      <div class="carousel-desc">{{ slides[activeIndex].desc }}</div>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from "vue";

const slides = [
  {
    title: "形式化验证演示",
    desc: "展示证明链路、约束收敛与验证证据。"
  },
  {
    title: "工程审查流程",
    desc: "覆盖风险识别、问题定位与修复建议。"
  },
  {
    title: "智能体协同分析",
    desc: "多智能体协作推理与审查策略编排。"
  },
  {
    title: "结构化报告生成",
    desc: "输出可追溯的结论与结构化工件。"
  }
];

const activeIndex = ref(0);
let timer = null;

const stopAuto = () => {
  if (timer) {
    clearInterval(timer);
    timer = null;
  }
};

const startAuto = () => {
  stopAuto();
  timer = setInterval(() => {
    next();
  }, 5000);
};

const setActive = (index) => {
  activeIndex.value = index;
  startAuto();
};

const next = () => {
  activeIndex.value = (activeIndex.value + 1) % slides.length;
};

const prev = () => {
  activeIndex.value = (activeIndex.value - 1 + slides.length) % slides.length;
};

const goNext = () => {
  next();
  startAuto();
};

const goPrev = () => {
  prev();
  startAuto();
};

onMounted(() => {
  startAuto();
});

onBeforeUnmount(() => {
  stopAuto();
});
</script>
