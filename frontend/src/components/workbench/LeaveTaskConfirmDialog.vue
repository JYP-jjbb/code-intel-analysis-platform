<template>
  <Teleport to="body">
    <div
      v-if="modelValue"
      ref="maskRef"
      class="leave-task-mask"
      role="presentation"
      @click.self="handleCancel"
    >
      <section
        ref="panelRef"
        class="leave-task-panel"
        role="dialog"
        aria-modal="true"
        aria-labelledby="leave-task-dialog-title"
      >
        <header class="leave-task-header">
          <h3 id="leave-task-dialog-title" class="leave-task-title">
            {{ title }}
          </h3>
          <p class="leave-task-message">
            {{ message }}
          </p>
        </header>
        <footer class="leave-task-actions">
          <button type="button" class="leave-task-btn is-secondary" @click="handleCancel">
            {{ cancelText }}
          </button>
          <button type="button" class="leave-task-btn is-primary" @click="handleConfirm">
            {{ confirmText }}
          </button>
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<script setup>
import { nextTick, onBeforeUnmount, ref, watch } from "vue";
import gsap from "gsap";

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: "确认离开当前任务？" },
  message: {
    type: String,
    default: "当前批量验证仍在后台执行。离开页面后，任务不会中断；返回页面时可继续查看实时进度与结果。"
  },
  cancelText: { type: String, default: "留在本页" },
  confirmText: { type: String, default: "继续离开" }
});

const emit = defineEmits(["update:modelValue", "cancel", "confirm"]);

const maskRef = ref(null);
const panelRef = ref(null);
let animationContext = null;

const runOpenAnimation = () => {
  if (!maskRef.value || !panelRef.value) {
    return;
  }
  animationContext?.revert();
  animationContext = gsap.context(() => {
    gsap.set(panelRef.value, { autoAlpha: 0, y: 14, scale: 0.988 });
    gsap.fromTo(maskRef.value, { autoAlpha: 0 }, { autoAlpha: 1, duration: 0.2, ease: "power1.out" });
    gsap.to(panelRef.value, {
      autoAlpha: 1,
      y: 0,
      scale: 1,
      duration: 0.24,
      ease: "power2.out"
    });
  }, maskRef.value);
};

const clearAnimationContext = () => {
  animationContext?.revert();
  animationContext = null;
};

const handleCancel = () => {
  emit("update:modelValue", false);
  emit("cancel");
};

const handleConfirm = () => {
  emit("update:modelValue", false);
  emit("confirm");
};

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) {
      clearAnimationContext();
      return;
    }
    nextTick(() => {
      runOpenAnimation();
    });
  }
);

onBeforeUnmount(() => {
  clearAnimationContext();
});
</script>

<style scoped>
.leave-task-mask {
  position: fixed;
  inset: 0;
  z-index: 2200;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(13, 38, 77, 0.24);
  backdrop-filter: blur(2px);
}

.leave-task-panel {
  width: min(560px, calc(100vw - 40px));
  border-radius: 28px;
  border: 1px solid rgba(64, 137, 220, 0.36);
  background:
    linear-gradient(180deg, rgba(245, 251, 255, 0.96) 0%, rgba(237, 247, 255, 0.98) 100%);
  box-shadow: 0 18px 38px rgba(25, 68, 128, 0.16);
  padding: 28px 30px 24px;
}

.leave-task-header {
  display: grid;
  gap: 12px;
}

.leave-task-title {
  margin: 0;
  font-size: 23px;
  line-height: 1.35;
  font-weight: 700;
  color: #1b3f73;
  letter-spacing: 0.01em;
}

.leave-task-message {
  margin: 0;
  color: #647e9f;
  font-size: 15px;
  line-height: 1.7;
}

.leave-task-actions {
  margin-top: 22px;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.leave-task-btn {
  min-width: 112px;
  height: 42px;
  border-radius: 16px;
  border: 1px solid transparent;
  font-size: 14px;
  font-weight: 600;
  line-height: 1;
  letter-spacing: 0.01em;
  cursor: pointer;
  transition: box-shadow 0.22s ease, transform 0.22s ease, background 0.22s ease, border-color 0.22s ease;
}

.leave-task-btn:active {
  transform: translateY(1px);
}

.leave-task-btn.is-secondary {
  color: #3c5f87;
  border-color: rgba(97, 146, 207, 0.42);
  background: rgba(243, 249, 255, 0.96);
}

.leave-task-btn.is-secondary:hover {
  border-color: rgba(81, 139, 208, 0.58);
  background: rgba(234, 245, 255, 0.98);
}

.leave-task-btn.is-primary {
  color: #ffffff;
  background: linear-gradient(135deg, #4f8dff 0%, #2a67e8 100%);
  box-shadow: 0 8px 16px rgba(44, 107, 255, 0.22);
}

.leave-task-btn.is-primary:hover {
  box-shadow: 0 0 0 1px rgba(88, 149, 255, 0.5), 0 12px 22px rgba(44, 107, 255, 0.3);
}

@media (max-width: 640px) {
  .leave-task-panel {
    border-radius: 24px;
    padding: 22px 18px 18px;
  }

  .leave-task-title {
    font-size: 20px;
  }

  .leave-task-actions {
    justify-content: stretch;
  }

  .leave-task-btn {
    flex: 1;
    min-width: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .leave-task-btn {
    transition: none;
  }
}
</style>
