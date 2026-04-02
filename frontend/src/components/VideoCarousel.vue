<template>
  <div class="runtime-panel video-carousel">
    <div class="carousel-stage">
      <div
        v-for="(slide, index) in slides"
        :key="slide.title"
        class="carousel-slide"
        :class="{ active: index === activeIndex }"
      >
        <div class="carousel-surface" :style="{ '--video-object-position': slide.objectPosition || '50% 50%' }">
          <video
            :ref="(el) => setVideoRef(el, index)"
            class="carousel-video"
            :src="slide.video"
            autoplay
            muted
            loop
            playsinline
            webkit-playsinline="true"
            preload="metadata"
            disablepictureinpicture
          ></video>
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
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";

const videoLocalCode = new URL("../assets/media/5 局部代码.mp4", import.meta.url).href;
const videoProjectReview = new URL("../assets/media/5 工程审查.mp4", import.meta.url).href;
const videoAgent = new URL("../assets/media/5 Agent.mp4", import.meta.url).href;
const videoReport = new URL("../assets/media/5 报告结果.mp4", import.meta.url).href;

const slides = [
  {
    title: "局部代码分析",
    desc: "聚焦关键片段，解析变量演化与终止行为。",
    video: videoLocalCode,
    objectPosition: "50% 46%"
  },
  {
    title: "工程代码审查",
    desc: "覆盖项目风险识别、定位与修复建议。",
    video: videoProjectReview,
    objectPosition: "50% 42%"
  },
  {
    title: "Agent 协同分析",
    desc: "展示 Observe-Plan-Act-Verify-Reflect 的协同流程。",
    video: videoAgent,
    objectPosition: "50% 44%"
  },
  {
    title: "报告结果展示",
    desc: "输出结构化、可追踪、可复用的分析报告。",
    video: videoReport,
    objectPosition: "50% 48%"
  }
];

const activeIndex = ref(0);
const videoRefs = ref([]);
let timer = null;

const setVideoRef = (el, index) => {
  if (!el) return;
  videoRefs.value[index] = el;
};

const stopAuto = () => {
  if (timer) {
    clearInterval(timer);
    timer = null;
  }
};

const next = () => {
  activeIndex.value = (activeIndex.value + 1) % slides.length;
};

const prev = () => {
  activeIndex.value = (activeIndex.value - 1 + slides.length) % slides.length;
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

const goNext = () => {
  next();
  startAuto();
};

const goPrev = () => {
  prev();
  startAuto();
};

const syncVideoPlayback = async () => {
  await nextTick();
  videoRefs.value.forEach((video, index) => {
    if (!video) return;
    if (index === activeIndex.value) {
      const playTask = video.play();
      if (playTask && typeof playTask.catch === "function") playTask.catch(() => {});
    } else {
      video.pause();
    }
  });
};

watch(activeIndex, () => {
  void syncVideoPlayback();
});

onMounted(() => {
  startAuto();
  void syncVideoPlayback();
});

onBeforeUnmount(() => {
  stopAuto();
  videoRefs.value.forEach((video) => video?.pause());
});
</script>
