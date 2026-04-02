<template>
  <div ref="root" class="landing">
    <div class="landing-bg">
      <div class="orb orb-a float"></div>
      <div class="orb orb-b float"></div>
      <div class="landing-grid"></div>
      <div class="landing-wave"></div>
    </div>
    <div class="background-decorations" aria-hidden="true">
      <img class="code-deco deco-1" :src="codeCSharp" alt="" />
      <img class="code-deco deco-2" :src="codeC" alt="" />
      <img class="code-deco deco-3" :src="codeGo" alt="" />
      <img class="code-deco deco-4" :src="codeJava" alt="" />
      <img class="code-deco deco-5" :src="codePython" alt="" />
    </div>
    <div class="mid-decorations" aria-hidden="true">
      <div class="deco-refining refining-deco">
        <img class="refining-image" :src="refiningLoop" alt="" />
        <div class="refining-label">
          <span>循环</span>
          <span>优化</span>
        </div>
      </div>
    </div>

    <section class="hero">
      <div class="hero-grid">
        <div class="hero-left">
          <div class="eyebrow" data-hero>CODE INTELLIGENCE ANALYSIS AND VERIFICATION PLATFORM</div>
          <h1 class="hero-title" data-hero>代码智能分析与验证平台</h1>
          <p class="hero-subtitle" data-hero>
            以形式化验证与工程级代码审查为核心，统一任务中心串联 Agent 推理、Checker 约束与结构化报告。
          </p>
          <div class="cta-row" data-hero>
            <a
              href="/workbench/nutera"
              class="btn primary"
              :aria-disabled="isPageTransitioning"
              @click.prevent="handlePageTransition('/workbench/nutera')"
            >
              开始体验平台
            </a>
            <a href="#workspaces" class="btn ghost" @click.prevent="scrollToWorkspaces">查看系统能力全景</a>
          </div>
          <div class="hero-badges" data-hero>
            <div class="badge">局部到工程覆盖</div>
            <div class="badge">形式化验证驱动</div>
            <div class="badge">智能体协同分析</div>
          </div>
        </div>

        <div class="hero-right" data-hero>
          <VideoCarousel />
        </div>
      </div>
    </section>

    <section id="workspaces" class="section reveal-section" ref="workspacesSectionRef">
      <div class="section-header" data-reveal ref="workspacesHeaderRef">
        <h2>双工作区协同</h2>
        <p>面向局部问题定位与工程级审查，支持从单点分析到全局研判的统一处理。</p>
      </div>
      <div class="workspace-decoration">
        <img class="workspace-deco-left" src="../assets/media/illustration1.png" alt="" />
        <img class="workspace-deco-right" src="../assets/media/illustration2.png" alt="" />
      </div>
      <div class="workspace-grid">
        <article class="workspace-card reveal-item" data-reveal>
          <div class="workspace-top">
            <div class="workspace-icon" data-icon="code-analysis">
              <img :src="iconLocal" alt="局部代码分析图标" />
            </div>
            <div>
              <div class="workspace-title">局部代码分析</div>
              <div class="workspace-subtitle">聚焦函数、片段与关键逻辑，快速定位风险点、异常路径与修改影响。</div>
            </div>
          </div>
          <div class="tag-row">
            <span class="tag">片段分析</span>
            <span class="tag">逻辑定位</span>
            <span class="tag">风险提示</span>
            <span class="tag">结果说明</span>
          </div>
          <router-link to="/workbench/nutera" class="btn subtle">进入分析工作区</router-link>
        </article>

        <article class="workspace-card reveal-item" data-reveal>
          <div class="workspace-top">
            <div class="workspace-icon" data-icon="system-review">
              <img :src="iconEngineering" alt="工程级代码审查图标" />
            </div>
            <div>
              <div class="workspace-title">工程级代码审查</div>
              <div class="workspace-subtitle">面向仓库与模块级代码关系，综合评估结构风险、变更影响与交付质量。</div>
            </div>
          </div>
          <div class="tag-row">
            <span class="tag">工程扫描</span>
            <span class="tag">关联影响</span>
            <span class="tag">修改建议</span>
            <span class="tag">审查报告</span>
          </div>
          <router-link to="/workbench/code-review" class="btn subtle">进入审查工作区</router-link>
        </article>
      </div>
    </section>

    <section id="analysis-chain" class="section reveal-section chain-section">
      <div class="section-header" data-reveal>
        <h2>核心分析链路</h2>
        <p>代码输入 → 智能推理 → 规则校验 → 结构化报告，形成串联分析闭环。</p>
      </div>
      <div class="chain-board" data-reveal>
        <div class="chain-flow">
          <div class="chain-top-line" aria-hidden="true">
            <svg class="chain-main-svg" viewBox="0 0 1000 36" preserveAspectRatio="none">
              <path class="chain-main-path" d="M10 18 L970 18"></path>
              <path class="chain-main-arrow" d="M970 12 L990 18 L970 24"></path>
            </svg>
          </div>
          <svg class="chain-mid-arrows" viewBox="0 0 240 80" preserveAspectRatio="none" aria-hidden="true">
            <defs>
              <marker id="chain-mid-arrowhead" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto">
                <path d="M0 0 L10 5 L0 10 Z" fill="currentColor"></path>
              </marker>
            </defs>
            <path class="chain-mid-path" d="M20 40 C80 14 160 14 220 40" marker-end="url(#chain-mid-arrowhead)"></path>
            <path class="chain-mid-path" d="M220 60 C160 86 80 86 20 60" marker-end="url(#chain-mid-arrowhead)"></path>
          </svg>
          <div class="chain-nodes">
            <div class="chain-column" data-chain>
              <div class="chain-node-circle" aria-hidden="true"></div>
              <div class="chain-branch" aria-hidden="true"></div>
              <div class="chain-node">
                <div class="chain-icon">
                  <img :src="inputCodesIcon" alt="代码输入图标" />
                </div>
                <div class="chain-title">代码输入</div>
                <div class="chain-desc">片段 · 文件 · 仓库</div>
              </div>
            </div>
            <div class="chain-column" data-chain>
              <div class="chain-node-circle" aria-hidden="true"></div>
              <div class="chain-branch" aria-hidden="true"></div>
              <div
                class="chain-node is-clickable"
                :class="{ 'is-active': triggerActive }"
                ref="analysisNodeRef"
                role="button"
                tabindex="0"
                :aria-pressed="triggerActive"
                @click="handleCoreLinkClick"
                @keydown.enter.prevent="handleCoreLinkClick"
                @keydown.space.prevent="handleCoreLinkClick"
              >
                <div class="chain-icon">
                  <img :src="analysisDecisionIcon" alt="分析推理图标" />
                </div>
                <div class="chain-title">分析推理</div>
                <div class="chain-desc">启动 · 反馈 · 优化</div>
              </div>
            </div>
            <div class="chain-column" data-chain>
              <div class="chain-node-circle" aria-hidden="true"></div>
              <div class="chain-branch" aria-hidden="true"></div>
              <div
                class="chain-node is-clickable"
                :class="{ 'is-active': triggerActive }"
                ref="ruleNodeRef"
                role="button"
                tabindex="0"
                :aria-pressed="triggerActive"
                @click="handleCoreLinkClick"
                @keydown.enter.prevent="handleCoreLinkClick"
                @keydown.space.prevent="handleCoreLinkClick"
              >
                <div class="chain-icon">
                  <img :src="ruleValidateIcon" alt="规则校验图标" />
                </div>
                <div class="chain-title">规则校验</div>
                <div class="chain-desc">变量 · 约束 · 证明</div>
              </div>
            </div>
            <div class="chain-column" data-chain>
              <div class="chain-node-circle" aria-hidden="true"></div>
              <div class="chain-branch" aria-hidden="true"></div>
              <div class="chain-node">
                <div class="chain-icon">
                  <img :src="resultReportIcon" alt="结果报告图标" />
                </div>
                <div class="chain-title">结果报告</div>
                <div class="chain-desc">证据 · 结论 · 报告</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section
      id="runtime-demo"
      class="section reveal-section"
      :class="{ 'is-triggered': triggerActive }"
      ref="runtimeSectionRef"
    >
      <SimulationPanel
        ref="simulationPanelRef"
        :activated="simulationActivated"
      />
    </section>

    <section id="scenarios" class="section reveal-section">
      <div class="section-header scenario-section-header" data-reveal>
        <h2>应用场景</h2>
        <p>覆盖代码学习、教学演示与工程审查三类场景，统一输出可理解、可追踪、可验证的分析结果。</p>
      </div>
      <div class="scenario-grid">
        <article class="scenario-card reveal-item" data-reveal>
          <div class="scenario-visual scenario-visual--learning">
            <img class="scenario-visual-image" :src="scenarioCodeLearning" alt="代码学习场景展示图" loading="lazy" />
          </div>
          <div class="scenario-title">代码学习</div>
          <p>辅助理解程序逻辑、执行路径与关键规则，提升代码阅读与学习效率。</p>
        </article>
        <article class="scenario-card reveal-item" data-reveal>
          <div class="scenario-visual scenario-visual--teaching">
            <img class="scenario-visual-image" :src="scenarioTeachingShow" alt="教学演示场景展示图" loading="lazy" />
          </div>
          <div class="scenario-title">教学演示</div>
          <p>直观呈现智能体分析、推理与校验过程，增强课堂展示与实验讲解效果。</p>
        </article>
        <article class="scenario-card reveal-item" data-reveal>
          <div class="scenario-visual scenario-visual--review">
            <img class="scenario-visual-image" :src="scenarioProjectReview" alt="工程项目审查场景展示图" loading="lazy" />
          </div>
          <div class="scenario-title">工程项目审查</div>
          <p>针对真实代码仓库输出风险摘要、问题定位与审查辅助建议。</p>
        </article>
      </div>
    </section>

    <section class="cta-section reveal-section">
      <div class="cta-content" data-reveal>
        <div>
          <h2>进入工作台，开始验证与审查</h2>
          <p>上传代码与工程资产，形成可追溯的验证链路与审查结论。</p>
        </div>
        <div class="cta-actions">
          <a
            href="/workbench/nutera"
            class="btn primary"
            :aria-disabled="isPageTransitioning"
            @click.prevent="handlePageTransition('/workbench/nutera')"
          >
            进入工作台
          </a>
          <a href="#workspaces" class="btn ghost">查看平台架构</a>
        </div>
      </div>
    </section>

    <svg
      ref="guideOverlayRef"
      class="guide-overlay"
      :width="guideSize.width"
      :height="guideSize.height"
      :viewBox="`0 0 ${guideSize.width} ${guideSize.height}`"
      v-show="triggerActive"
      aria-hidden="true"
    >
      <defs>
        <mask id="guide-reveal-mask" maskUnits="userSpaceOnUse" maskContentUnits="userSpaceOnUse">
          <rect width="100%" height="100%" fill="black"></rect>
          <line
            ref="guideMaskLeftRef"
            class="guide-mask-path"
            :x1="guideLeftLine.x1"
            :y1="guideLeftLine.y1"
            :x2="guideLeftLine.x2"
            :y2="guideLeftLine.y2"
          ></line>
          <line
            ref="guideMaskRightRef"
            class="guide-mask-path"
            :x1="guideRightLine.x1"
            :y1="guideRightLine.y1"
            :x2="guideRightLine.x2"
            :y2="guideRightLine.y2"
          ></line>
        </mask>
      </defs>
      <line
        ref="guideLeftPathRef"
        class="guide-path"
        :x1="guideLeftLine.x1"
        :y1="guideLeftLine.y1"
        :x2="guideLeftLine.x2"
        :y2="guideLeftLine.y2"
        v-show="guideLeftLine.visible"
        mask="url(#guide-reveal-mask)"
      ></line>
      <line
        ref="guideRightPathRef"
        class="guide-path"
        :x1="guideRightLine.x1"
        :y1="guideRightLine.y1"
        :x2="guideRightLine.x2"
        :y2="guideRightLine.y2"
        v-show="guideRightLine.visible"
        mask="url(#guide-reveal-mask)"
      ></line>
      <line
        ref="guideTargetRef"
        class="guide-target-highlight"
        :x1="guideTarget.x1"
        :y1="guideTarget.y"
        :x2="guideTarget.x2"
        :y2="guideTarget.y"
        v-show="guideTarget.visible"
      ></line>
    </svg>

  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, nextTick } from "vue";
import gsap from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";
import { useRouter } from "vue-router";
import VideoCarousel from "../components/VideoCarousel.vue";
import SimulationPanel from "../components/SimulationPanel.vue";
import { playPageTransition } from "../services/pageTransitionService";
import codeCSharp from "../assets/media/csharp-code.png";
import codeC from "../assets/media/c-code.png";
import codeGo from "../assets/media/go-code.png";
import codeJava from "../assets/media/java-code.png";
import codePython from "../assets/media/python-code.png";
import iconLocal from "../assets/media/space-icon1.png";
import iconEngineering from "../assets/media/space-icon2.png";
import inputCodesIcon from "../assets/media/input-codes.png";
import analysisDecisionIcon from "../assets/media/analysis-decision.png";
import ruleValidateIcon from "../assets/media/rule-validate.png";
import resultReportIcon from "../assets/media/result-report.png";
import refiningLoop from "../assets/media/Refining-Loop.png";
import scenarioCodeLearning from "../assets/media/code-learning.png";
import scenarioTeachingShow from "../assets/media/teaching-show.png";
import scenarioProjectReview from "../assets/media/project-review.png";

gsap.registerPlugin(ScrollTrigger);
const router = useRouter();

const root = ref(null);
const workspacesSectionRef = ref(null);
const workspacesHeaderRef = ref(null);
const analysisNodeRef = ref(null);
const ruleNodeRef = ref(null);
const runtimeSectionRef = ref(null);
const simulationPanelRef = ref(null);
const guideOverlayRef = ref(null);
const guideLeftPathRef = ref(null);
const guideRightPathRef = ref(null);
const guideMaskLeftRef = ref(null);
const guideMaskRightRef = ref(null);
const guideTargetRef = ref(null);

const triggerActive = ref(false);
const isPageTransitioning = ref(false);
const simulationActivated = ref(false);
const guideLeftLine = ref({ x1: 0, y1: 0, x2: 0, y2: 0, visible: false });
const guideRightLine = ref({ x1: 0, y1: 0, x2: 0, y2: 0, visible: false });
const guideTarget = ref({ x1: 0, x2: 0, y: 0, visible: false });
const guideSize = ref({ width: 0, height: 0 });

let guideTween = null;
let guidePulseTween = null;
let guideTargetPulseTween = null;
let guideResizeHandler = null;
let ctx;
let mm;
let workspaceSection = null;
let workspaceDecoration = null;
let leftDeco = null;
let rightDeco = null;
let positionRefiningDeco = null;

const getReducedMotion = () =>
  typeof window !== "undefined" &&
  window.matchMedia &&
  window.matchMedia("(prefers-reduced-motion: reduce)").matches;

const clamp = (value, min, max) => Math.min(Math.max(value, min), max);
const round = (value) => Number(value.toFixed(1));

const updateGuideCanvas = () => {
  if (!root.value) return;
  const rect = root.value.getBoundingClientRect();
  const height = Math.max(root.value.scrollHeight, rect.height);
  guideSize.value = { width: rect.width, height };
};

const buildGuideGeometry = () => {
  if (!root.value || !runtimeSectionRef.value || !analysisNodeRef.value || !ruleNodeRef.value) return null;
  const endTarget = simulationPanelRef.value?.$el ?? runtimeSectionRef.value;
  const rootRect = root.value.getBoundingClientRect();
  const analysisRect = analysisNodeRef.value.getBoundingClientRect();
  const ruleRect = ruleNodeRef.value.getBoundingClientRect();
  const endRect = endTarget.getBoundingClientRect();

  const [startLeftX, startRightX] = [
    analysisRect.left + analysisRect.width / 2 - rootRect.left,
    ruleRect.left + ruleRect.width / 2 - rootRect.left
  ].sort((a, b) => a - b);
  const startY = Math.max(analysisRect.bottom, ruleRect.bottom) - rootRect.top + 8;
  const endYRaw = endRect.top + 12 - rootRect.top;
  const endY = Math.max(startY + 72, endYRaw);
  const panelLeftEdge = endRect.left - rootRect.left;
  const panelRightEdge = endRect.right - rootRect.left;
  const endLeftX = clamp(panelLeftEdge + 2, 2, guideSize.value.width - 2);
  const endRightX = clamp(panelRightEdge - 2, 2, guideSize.value.width - 2);

  return {
    left: {
      x1: round(startLeftX),
      y1: round(startY),
      x2: round(endLeftX),
      y2: round(endY)
    },
    right: {
      x1: round(startRightX),
      y1: round(startY),
      x2: round(endRightX),
      y2: round(endY)
    },
    target: {
      x1: round(endLeftX),
      x2: round(endRightX),
      y: round(endY + 3)
    }
  };
};

const startGuidePulse = () => {
  if (getReducedMotion()) return;
  guidePulseTween?.kill();
  guideTargetPulseTween?.kill();

  const pathEls = [guideLeftPathRef.value, guideRightPathRef.value].filter(Boolean);
  if (pathEls.length) {
    guidePulseTween = gsap.to(pathEls, {
      strokeOpacity: 0.58,
      duration: 1.45,
      repeat: -1,
      yoyo: true,
      ease: "sine.inOut"
    });
  }

  if (guideTargetRef.value) {
    guideTargetPulseTween = gsap.to(guideTargetRef.value, {
      autoAlpha: 0.45,
      duration: 1.45,
      repeat: -1,
      yoyo: true,
      ease: "sine.inOut"
    });
  }
};

const drawGuideLines = async (animate = true) => {
  updateGuideCanvas();
  const geometry = buildGuideGeometry();
  if (!geometry) {
    guideLeftLine.value = { x1: 0, y1: 0, x2: 0, y2: 0, visible: false };
    guideRightLine.value = { x1: 0, y1: 0, x2: 0, y2: 0, visible: false };
    guideTarget.value = { x1: 0, x2: 0, y: 0, visible: false };
    return;
  }

  guideLeftLine.value = { ...geometry.left, visible: true };
  guideRightLine.value = { ...geometry.right, visible: true };
  guideTarget.value = { ...geometry.target, visible: true };
  await nextTick();
  const maskPaths = [guideMaskLeftRef.value, guideMaskRightRef.value].filter(Boolean);
  const pathEls = [guideLeftPathRef.value, guideRightPathRef.value].filter(Boolean);
  const targetEl = guideTargetRef.value;
  if (!maskPaths.length || !pathEls.length || !guideLeftLine.value.visible || !guideRightLine.value.visible) return;

  guideTween?.kill();
  guidePulseTween?.kill();
  guideTargetPulseTween?.kill();

  maskPaths.forEach((maskPath) => {
    const length = maskPath.getTotalLength();
    gsap.set(maskPath, { strokeDasharray: length, strokeDashoffset: length, autoAlpha: 1 });
  });
  gsap.set(pathEls, { autoAlpha: 1, strokeOpacity: 0.88 });
  if (targetEl) {
    gsap.set(targetEl, { autoAlpha: 0, strokeDashoffset: 14 });
  }

  if (!animate || getReducedMotion()) {
    gsap.set(maskPaths, { strokeDashoffset: 0 });
    if (targetEl) {
      gsap.set(targetEl, { autoAlpha: 0.62, strokeDashoffset: 0 });
    }
    simulationActivated.value = true;
    startGuidePulse();
    return;
  }

  guideTween = gsap.timeline({
    defaults: { ease: "power2.out" },
    onComplete: () => {
      simulationActivated.value = true;
      startGuidePulse();
    }
  });

  maskPaths.forEach((maskPath, index) => {
    guideTween.to(
      maskPath,
      {
        strokeDashoffset: 0,
        duration: 0.95
      },
      index * 0.06
    );
  });
  if (targetEl) {
    guideTween.to(
      targetEl,
      {
        autoAlpha: 0.62,
        strokeDashoffset: 0,
        duration: 0.34
      },
      0.38
    );
  }
};

const scrollToRuntime = () => {
  if (!runtimeSectionRef.value || typeof window === "undefined") return;
  const rect = runtimeSectionRef.value.getBoundingClientRect();
  const targetTop = rect.top + window.scrollY - 80;
  window.scrollTo({
    top: Math.max(0, targetTop),
    behavior: "smooth"
  });
};

const scrollToWorkspaces = () => {
  if (typeof window === "undefined") return;
  const targetEl = workspacesHeaderRef.value ?? workspacesSectionRef.value;
  if (!targetEl) return;

  const rect = targetEl.getBoundingClientRect();
  const currentTop = window.scrollY || window.pageYOffset;
  const targetTop = Math.max(0, rect.top + currentTop - 64);
  window.scrollTo({
    top: targetTop,
    behavior: "smooth"
  });
};

const handlePageTransition = async (targetPath) => {
  if (isPageTransitioning.value) return;
  isPageTransitioning.value = true;

  try {
    await playPageTransition({
      onBeforeFadeOut: () => router.push(targetPath)
    });
  } finally {
    isPageTransitioning.value = false;
  }
};

const handleCoreLinkClick = async () => {
  triggerActive.value = true;
  simulationActivated.value = false;
  await drawGuideLines(true);
  gsap.delayedCall(0.1, scrollToRuntime);
};

onMounted(() => {
  document.body.classList.add("landing-mode");
  mm = gsap.matchMedia();

  ctx = gsap.context((self) => {
    const q = self.selector;

    const heroItems = q("[data-hero]");
    gsap.set(heroItems, { autoAlpha: 0, y: 24 });

    const heroTimeline = gsap.timeline({
      defaults: { duration: 0.9, ease: "power3.out" }
    });
    heroTimeline.to(heroItems, { autoAlpha: 1, y: 0, stagger: 0.12 });

    gsap.from(q(".log-line"), {
      autoAlpha: 0,
      y: 10,
      duration: 0.6,
      stagger: 0.08,
      delay: 0.35
    });

    const revealItems = q("[data-reveal]");
    gsap.set(revealItems, { autoAlpha: 0, y: 30 });
    revealItems.forEach((item) => {
      gsap.to(item, {
        autoAlpha: 1,
        y: 0,
        duration: 0.85,
        ease: "power2.out",
        scrollTrigger: {
          trigger: item,
          start: "top 85%",
          once: true
        }
      });
    });

    const chainSection = q("#analysis-chain")[0];
    const chainMainPath = q(".chain-main-path")[0];
    const chainMainArrow = q(".chain-main-arrow")[0];
    const chainBranches = q(".chain-branch");
    const chainNodes = q(".chain-node");
    const chainNodeCircles = q(".chain-node-circle");
    const chainMidPaths = q(".chain-mid-path");
    if (chainSection && chainMainPath) {
      const length = chainMainPath.getTotalLength();
      gsap.set(chainMainPath, { strokeDasharray: length, strokeDashoffset: length });
      gsap.set(chainMainArrow, { autoAlpha: 0 });
      gsap.set(chainBranches, { scaleY: 0, transformOrigin: "top center" });
      gsap.set(chainNodes, { autoAlpha: 0, y: 16, scale: 0.96 });
      gsap.set(chainNodeCircles, { autoAlpha: 0, scale: 0 });
      gsap.set(chainMidPaths, { autoAlpha: 0, strokeDashoffset: 18 });

      const chainTimeline = gsap.timeline({
        scrollTrigger: {
          trigger: chainSection,
          start: "top 75%",
          once: true
        }
      });

      chainTimeline
        .to(chainMainPath, {
          strokeDashoffset: 0,
          duration: 1.05,
          ease: "power2.out"
        })
        .to(
          chainMainArrow,
          {
            autoAlpha: 1,
            duration: 0.2,
            ease: "power2.out"
          },
          "-=0.15"
        );

      chainBranches.forEach((branch, index) => {
        chainTimeline
          .to(chainNodeCircles[index], {
            autoAlpha: 1,
            scale: 1,
            duration: 0.3,
            ease: "back.out(1.7)"
          }, index === 0 ? "+=0.05" : ">-0.04")
          .to(branch, {
            scaleY: 1,
            duration: 0.28,
            ease: "power2.out"
          }, "<")
          .to(chainNodes[index], {
            autoAlpha: 1,
            y: 0,
            scale: 1,
            duration: 0.36,
            ease: "power2.out",
            clearProps: "transform"
          }, ">-0.08");
      });

      if (chainMidPaths.length) {
        chainTimeline.to(
          chainMidPaths,
          {
            autoAlpha: 1,
            strokeDashoffset: 0,
            duration: 0.4,
            ease: "power2.out"
          },
          ">+0.05"
        );
      }
    }

    const refiningDeco = q(".refining-deco")[0];
    const chainBoard = q(".chain-board")[0];
    const chainHeader = q("#analysis-chain .section-header")[0];
    const firstChainColumn = q(".chain-column")[0];
    if (refiningDeco && chainSection) {
      const updateRefiningPosition = () => {
        const scrollY = window.scrollY || window.pageYOffset;
        const scrollX = window.scrollX || window.pageXOffset;
        const sectionRect = chainSection.getBoundingClientRect();
        const boardRect = chainBoard?.getBoundingClientRect() ?? sectionRect;
        const headerRect = chainHeader?.getBoundingClientRect();
        const firstRect = firstChainColumn?.getBoundingClientRect() ?? boardRect;
        const decoRect = refiningDeco.getBoundingClientRect();
        const decoWidth = decoRect.width || refiningDeco.offsetWidth;
        const decoHeight = decoRect.height || refiningDeco.offsetHeight;

        const targetLeft = boardRect.left + scrollX - decoWidth * 0.62 - 120;
        const targetTopBase = firstRect.top + scrollY + firstRect.height * 0.45 - decoHeight * 0.5;
        const downShift = 50;
        const targetTop = targetTopBase - 110 + downShift;
        const minTop = headerRect
          ? headerRect.bottom + scrollY + 16 + downShift
          : sectionRect.top + scrollY + 120 + downShift;
        const maxTop = sectionRect.bottom + scrollY - decoHeight - 24;
        const clampedTop = Math.min(Math.max(targetTop, minTop), maxTop);
        const clampedLeft = Math.max(8, targetLeft);

        refiningDeco.style.top = `${Math.max(0, clampedTop)}px`;
        refiningDeco.style.left = `${clampedLeft}px`;
      };

      positionRefiningDeco = updateRefiningPosition;
      nextTick(() => {
        updateRefiningPosition();
        window.addEventListener("resize", updateRefiningPosition);
      });

      const refiningImage = q(".refining-image")[0];
      refiningImage?.addEventListener("load", updateRefiningPosition, { once: true });
    }

    gsap.to(q(".runtime-panel"), {
      y: -12,
      ease: "none",
      scrollTrigger: {
        trigger: q(".hero")[0],
        start: "top top",
        end: "bottom top",
        scrub: 1
      }
    });

    const decoElements = q(".code-deco");
    decoElements.forEach((element) => {
      const baseOpacity = Number.parseFloat(getComputedStyle(element).opacity) || 0.14;
      const targetOpacity = Math.max(0.03, Math.min(0.06, baseOpacity * 0.35));
      gsap.to(element, {
        opacity: targetOpacity,
        ease: "none",
        scrollTrigger: {
          trigger: q(".hero")[0],
          start: "top top",
          end: "bottom top",
          scrub: true
        }
      });
    });

    mm.add(
      {
        reduceMotion: "(prefers-reduced-motion: reduce)",
        allowMotion: "(prefers-reduced-motion: no-preference)"
      },
      (context) => {
        if (context.conditions.reduceMotion) {
          gsap.set(q(".float, .chip, .refining-image"), { clearProps: "all" });
          return;
        }

        gsap.to(q(".float"), {
          y: -16,
          repeat: -1,
          yoyo: true,
          duration: 6,
          ease: "sine.inOut",
          stagger: 0.4
        });

        gsap.to(q(".chip"), {
          y: -6,
          repeat: -1,
          yoyo: true,
          duration: 2.8,
          ease: "sine.inOut",
          stagger: 0.18
        });

        gsap.to(q(".refining-image"), {
          rotation: -360,
          duration: 48,
          repeat: -1,
          ease: "none",
          transformOrigin: "50% 50%"
        });

        const floatY = [-36, 28, -42, 34, -30];
        const floatX = [32, -36, 28, -30, 40];
        gsap.to(q(".code-deco"), {
          y: (index) => floatY[index % floatY.length],
          x: (index) => floatX[index % floatX.length],
          repeat: -1,
          yoyo: true,
          duration: (index) => 4.5 + index,
          ease: "sine.inOut",
          stagger: 0.6
        });
      }
    );

    const workspaceSectionEl = q("#workspaces")[0];
    if (workspaceSectionEl) {
      const leftDecoEl = q(".workspace-deco-left")[0];
      const rightDecoEl = q(".workspace-deco-right")[0];

      if (leftDecoEl && rightDecoEl) {
        // 滚动透明度控制
        gsap.to({}, {
          scrollTrigger: {
            trigger: workspaceSectionEl,
            start: "top bottom",
            end: "bottom top",
            scrub: 1,
            onUpdate: (self) => {
              const p = self.progress;
              let opacity = 0.05;

              if (p < 0.5) {
                opacity = 0.05 + (p / 0.5) * 0.75;
              } else {
                opacity = 1 - ((p - 0.5) / 0.5) * 1;
              }

              gsap.set([leftDecoEl, rightDecoEl], {
                opacity: Math.max(0, Math.min(1, opacity))
              });
            }
          }
        });

        // 呼吸效果动画
        const breathingTimeline = gsap.timeline({
          repeat: -1,
          yoyo: true,
          repeatDelay: 1
        });

        breathingTimeline.to([leftDecoEl, rightDecoEl], {
          scale: 1.05,
          duration: 3,
          ease: "sine.inOut"
        });
      }
    }
  }, root.value);

  nextTick(() => {
    updateGuideCanvas();
    guideResizeHandler = () => {
      updateGuideCanvas();
      if (triggerActive.value) {
        drawGuideLines(false);
      }
    };
    window.addEventListener("resize", guideResizeHandler);
  });
});

onBeforeUnmount(() => {
  document.body.classList.remove("landing-mode");
  mm?.revert();
  ctx?.revert();
  ScrollTrigger.getAll().forEach((trigger) => trigger.kill());
  if (positionRefiningDeco) {
    window.removeEventListener("resize", positionRefiningDeco);
  }
  if (guideResizeHandler) {
    window.removeEventListener("resize", guideResizeHandler);
  }
  guideTween?.kill();
  guidePulseTween?.kill();
  guideTargetPulseTween?.kill();
});
</script>
