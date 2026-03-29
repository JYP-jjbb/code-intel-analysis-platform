<template>
  <Teleport to="body">
    <div v-show="isVisible" ref="overlayRef" class="page-transition-overlay" aria-hidden="true">
      <div ref="coreRef" class="transition-core">
        <div class="transition-title">
          <span>{{ typedText }}</span>
          <span class="title-cursor" :class="{ 'is-blinking': cursorBlinking }">_</span>
        </div>
        <div class="transition-progress-row">
          <div class="transition-progress-track">
            <div class="transition-progress-fill" :style="progressStyle"></div>
          </div>
          <div class="transition-percent">{{ percentText }}</div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref } from "vue";
import gsap from "gsap";

const isVisible = ref(false);
const typedText = ref("C");
const cursorBlinking = ref(true);
const progress = ref(0);

const overlayRef = ref(null);
const coreRef = ref(null);

const progressStyle = computed(() => ({
  width: `${progress.value}%`
}));
const percentText = computed(() => `${progress.value}%`);

const stageConfig = [
  { text: "CODE INTELLI", progress: 20, transition: 0.2, hold: 0.4 },
  { text: "CODE INTELLIGENCE ANALYSIS AND VERIFICATI", progress: 66, transition: 0.4, hold: 0.4 },
  { text: "CODE INTELLIGENCE ANALYSIS AND VERIFICATION PLATFORM. R", progress: 95, transition: 0.2, hold: 0.4 },
  { text: "CODE INTELLIGENCE ANALYSIS AND VERIFICATION PLATFORM. RUN!", progress: 100, transition: 0.2, hold: 0.4 }
];
const OVERLAY_FADE_DURATION = 0.2;
const OVERLAY_FADE_EASE = "power1.inOut";

let transitionTimeline = null;
let playPromise = null;

const resetState = () => {
  typedText.value = "C";
  cursorBlinking.value = true;
  progress.value = 0;
};

const appendStage = (timeline, stage, state) => {
  const typeProxy = { value: state.length };
  const progressProxy = { value: state.progress };

  timeline.to(typeProxy, {
    value: stage.text.length,
    duration: stage.transition,
    ease: "none",
    onUpdate: () => {
      typedText.value = stage.text.slice(0, Math.round(typeProxy.value));
    }
  });

  timeline.to(progressProxy, {
    value: stage.progress,
    duration: stage.transition,
    ease: "power2.out",
    onUpdate: () => {
      progress.value = Math.round(progressProxy.value);
    }
  }, "<");

  if (stage.hold > 0) {
    timeline.to({}, { duration: stage.hold });
  }
  state.length = stage.text.length;
  state.progress = stage.progress;
};

const play = async (options = {}) => {
  if (playPromise) return playPromise;
  const onBeforeFadeOut = options?.onBeforeFadeOut;

  resetState();
  isVisible.value = true;
  await nextTick();

  playPromise = new Promise((resolve) => {
    let resolved = false;
    const finalize = () => {
      if (resolved) return;
      resolved = true;
      cursorBlinking.value = false;
      isVisible.value = false;
      playPromise = null;
      resolve();
    };

    transitionTimeline?.kill();
    transitionTimeline = gsap.timeline({
      defaults: { ease: "power2.out" },
      onComplete: finalize,
      onInterrupt: finalize
    });

    const state = { length: 1, progress: 0 };
    transitionTimeline.set(overlayRef.value, { autoAlpha: 0 });
    transitionTimeline.set(coreRef.value, { autoAlpha: 0, y: 14, scale: 0.986 });

    transitionTimeline
      .to(overlayRef.value, { autoAlpha: 1, duration: OVERLAY_FADE_DURATION, ease: OVERLAY_FADE_EASE })
      .to(coreRef.value, { autoAlpha: 1, y: 0, scale: 1, duration: OVERLAY_FADE_DURATION, ease: OVERLAY_FADE_EASE }, "<")
      .to({}, { duration: 0.2 });

    stageConfig.forEach((stage) => appendStage(transitionTimeline, stage, state));

    transitionTimeline.call(() => {
      typedText.value = stageConfig[stageConfig.length - 1].text;
      progress.value = 100;
      cursorBlinking.value = false;
    });

    transitionTimeline.addPause(">", () => {
      Promise.resolve()
        .then(() => (typeof onBeforeFadeOut === "function" ? onBeforeFadeOut() : null))
        .finally(() => {
          transitionTimeline?.resume();
        });
    });

    transitionTimeline
      .to(coreRef.value, {
        autoAlpha: 0,
        y: -5,
        scale: 0.995,
        duration: OVERLAY_FADE_DURATION,
        ease: OVERLAY_FADE_EASE
      }, ">")
      .to(overlayRef.value, {
        autoAlpha: 0,
        duration: OVERLAY_FADE_DURATION,
        ease: OVERLAY_FADE_EASE
      }, "<");
  });

  return playPromise;
};

defineExpose({
  play
});

onBeforeUnmount(() => {
  transitionTimeline?.kill();
});
</script>

<style scoped>
.page-transition-overlay {
  position: fixed;
  inset: 0;
  z-index: 1200;
  display: grid;
  place-items: center;
  background: linear-gradient(150deg, rgba(244, 250, 255, 0.92), rgba(235, 245, 255, 0.88));
  backdrop-filter: blur(4px);
  pointer-events: auto;
}

.transition-core {
  width: min(840px, calc(100vw - 64px));
  display: grid;
  gap: 14px;
  padding: 22px 24px;
  border-radius: 18px;
  border: 1px solid rgba(61, 122, 210, 0.24);
  background:
    linear-gradient(140deg, rgba(255, 255, 255, 0.84), rgba(236, 246, 255, 0.78)),
    linear-gradient(90deg, rgba(53, 121, 213, 0.04), rgba(31, 89, 172, 0.08));
  box-shadow:
    inset 0 0 0 1px rgba(255, 255, 255, 0.58),
    0 20px 48px rgba(32, 82, 154, 0.16);
}

.transition-title {
  font-family: "JetBrains Mono", "Fira Code", monospace;
  font-size: clamp(12px, 1.35vw, 15px);
  letter-spacing: 0.03em;
  color: #194b88;
  text-align: center;
  line-height: 1.4;
}

.title-cursor {
  display: inline-block;
  color: rgba(37, 106, 198, 0.88);
  width: 10px;
  text-align: left;
}

.title-cursor.is-blinking {
  animation: title-cursor-blink 0.85s steps(1, end) infinite;
}

.transition-progress-row {
  display: grid;
  grid-template-columns: minmax(0, 92fr) minmax(64px, 8fr);
  align-items: center;
  gap: 14px;
}

.transition-progress-track {
  width: 100%;
  height: 16px;
  border-radius: 999px;
  padding: 2px;
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid rgba(55, 118, 205, 0.22);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.6), 0 10px 24px rgba(36, 93, 173, 0.12);
  overflow: hidden;
}

.transition-progress-fill {
  height: 100%;
  min-width: 0;
  border-radius: 999px;
  background-image: repeating-linear-gradient(
    -45deg,
    rgba(58, 130, 232, 0.92) 0 10px,
    rgba(255, 255, 255, 0.9) 10px 18px
  );
  background-size: 26px 26px;
  box-shadow: inset 0 0 10px rgba(255, 255, 255, 0.35);
  animation: progress-stripes 1.15s linear infinite;
}

.transition-percent {
  font-family: "JetBrains Mono", "Fira Code", monospace;
  font-size: clamp(15px, 1.8vw, 20px);
  font-weight: 600;
  letter-spacing: 0.02em;
  color: #265b9d;
  text-align: right;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

@media (max-width: 720px) {
  .transition-core {
    width: min(92vw, 760px);
    padding: 18px 16px;
  }

  .transition-progress-row {
    grid-template-columns: minmax(0, 72fr) minmax(54px, 28fr);
    gap: 10px;
  }

  .transition-progress-track {
    height: 14px;
  }
}

@keyframes title-cursor-blink {
  0%, 46% {
    opacity: 1;
  }

  47%, 100% {
    opacity: 0.1;
  }
}

@keyframes progress-stripes {
  from {
    background-position: 0 0;
  }

  to {
    background-position: 26px 0;
  }
}
</style>
