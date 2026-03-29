<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { ArrowRight } from '@element-plus/icons-vue'
import LanguageSwitcher from '@/components/LanguageSwitcher.vue'

const router = useRouter()
const userStore = useUserStore()

// 定义滚动触发的内容块
const sections = ref([
  {
    id: 1,
    titleKey: 'welcome.sections.section1.title',
    descKey: 'welcome.sections.section1.desc',
    image: '/logo.png',
    direction: 'left' // 图片从左侧滑入
  },
  {
    id: 2,
    titleKey: 'welcome.sections.section2.title',
    descKey: 'welcome.sections.section2.desc',
    image: '/images/500008.jpg',
    direction: 'right' // 图片从右侧滑入
  },
  {
    id: 3,
    titleKey: 'welcome.sections.section3.title',
    descKey: 'welcome.sections.section3.desc',
    image: '/images/500025.jpg',
    direction: 'left'
  },
  {
    id: 4,
    titleKey: 'welcome.sections.section4.title',
    descKey: 'welcome.sections.section4.desc',
    image: '/images/500035.jpg',
    direction: 'right'
  },
  {
    id: 5,
    titleKey: 'welcome.sections.section5.title',
    descKey: 'welcome.sections.section5.desc',
    image: '/images/500045.jpg',
    direction: 'left'
  },
  {
    id: 6,
    titleKey: 'welcome.sections.section6.title',
    descKey: 'welcome.sections.section6.desc',
    image: '/images/500042.jpg',
    direction: 'right'
  }
])

const visibleSections = ref<Set<number>>(new Set())
let observer: IntersectionObserver | null = null

onMounted(() => {
  // 创建 Intersection Observer
  observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        const sectionId = parseInt(entry.target.getAttribute('data-section-id') || '0')
        if (entry.isIntersecting) {
          visibleSections.value.add(sectionId)
        }
      })
    },
    {
      threshold: 0.2, // 当元素20%可见时触发
      rootMargin: '0px'
    }
  )

  // 观察所有 section
  const sectionElements = document.querySelectorAll('.content-section')
  sectionElements.forEach((el) => {
    observer?.observe(el)
  })
})

onUnmounted(() => {
  if (observer) {
    observer.disconnect()
  }
})

const isSectionVisible = (sectionId: number) => {
  return visibleSections.value.has(sectionId)
}

const enterSite = () => {
  router.push('/login')
}
</script>

<template>
  <div class="welcome-page">
    <!-- 顶部语言切换器 -->
    <div class="top-bar">
      <LanguageSwitcher />
    </div>
    
    <!-- Hero 头部区域 -->
    <section class="hero-section">
      <div class="hero-background">
        <div class="floating-shape shape-1"></div>
        <div class="floating-shape shape-2"></div>
        <div class="floating-shape shape-3"></div>
      </div>
      
      <div class="hero-content">
        <img src="/logo.png" alt="Logo" class="hero-logo" />
        <h1 class="hero-title">{{ $t('welcome.title') }}</h1>
        <p class="hero-subtitle">{{ $t('welcome.subtitle') }}</p>
        <p class="hero-desc">{{ $t('welcome.description') }}</p>
        
        <div class="hero-actions">
          <el-button type="warning" size="large" @click="enterSite" class="primary-btn">
            <span>{{ $t('welcome.startOrder') }}</span>
            <el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>

        <!-- 滚动提示 -->
        <div class="scroll-hint">
          <p>{{ $t('welcome.scrollHint') }}</p>
          <div class="scroll-arrow">↓</div>
        </div>
      </div>
    </section>

    <!-- 滚动触发的内容区域 -->
    <div class="content-sections">
      <section
        v-for="section in sections"
        :key="section.id"
        :data-section-id="section.id"
        class="content-section"
        :class="[
          section.direction === 'left' ? 'layout-left' : 'layout-right',
          { 'is-visible': isSectionVisible(section.id) }
        ]"
      >
        <div class="section-container">
          <div class="section-image" :class="`slide-from-${section.direction}`">
            <img :src="section.image" :alt="section.title" />
            <div class="image-overlay"></div>
          </div>
          <div class="section-text" :class="`slide-from-${section.direction === 'left' ? 'right' : 'left'}`">
            <h2>{{ $t(section.titleKey) }}</h2>
            <p>{{ $t(section.descKey) }}</p>
            <div class="section-decoration"></div>
          </div>
        </div>
      </section>
    </div>

    <!-- 特色服务区域 -->
    <section class="features-section">
      <h2 class="section-title">为什么选择我们</h2>
      <div class="features-grid">
        <div class="feature-item" v-for="(feature, index) in [
          { icon: '🚀', title: '极速配送', desc: '30分钟内送达' },
          { icon: '💯', title: '品质保证', desc: '新鲜食材精选' },
          { icon: '❤️', title: '用心服务', desc: '贴心周到体验' },
          { icon: '🎁', title: '优惠多多', desc: '天天有惊喜' }
        ]" :key="index" :style="{ animationDelay: `${index * 0.1}s` }">
          <div class="feature-icon">{{ feature.icon }}</div>
          <h3>{{ feature.title }}</h3>
          <p>{{ feature.desc }}</p>
        </div>
      </div>
    </section>

    <!-- CTA 区域 -->
    <section class="cta-section">
      <div class="cta-content">
        <h2>{{ $t('welcome.cta.title') }}</h2>
        <p>{{ $t('welcome.cta.subtitle') }}</p>
        <el-button type="warning" size="large" @click="enterSite" class="cta-btn">
          <span>{{ $t('welcome.cta.button') }}</span>
          <el-icon><ArrowRight /></el-icon>
        </el-button>
      </div>
    </section>

    <!-- 页脚 -->
    <footer class="footer">
      <p>{{ $t('welcome.footer') }}</p>
    </footer>
  </div>
</template>

<style scoped>
/* ==================== 基础样式 ==================== */
.welcome-page {
  min-height: 100vh;
  background: linear-gradient(to bottom, #FFF5E6 0%, #FFE8CC 50%, #FFF5E6 100%);
  overflow-x: hidden;
}

/* ==================== 顶部栏 ==================== */
.top-bar {
  position: fixed;
  top: 20px;
  right: 20px;
  z-index: 1000;
  animation: fadeInDown 1s ease-out 0.5s both;
}

@keyframes fadeInDown {
  from {
    opacity: 0;
    transform: translateY(-20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ==================== Hero 区域 ==================== */
.hero-section {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.hero-background {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 0;
}

.floating-shape {
  position: absolute;
  border-radius: 50%;
  background: linear-gradient(135deg, rgba(255, 153, 102, 0.15), rgba(255, 107, 53, 0.15));
  animation: floatAround 20s infinite ease-in-out;
}

.shape-1 {
  width: 400px;
  height: 400px;
  top: -100px;
  right: -100px;
  animation-delay: 0s;
}

.shape-2 {
  width: 300px;
  height: 300px;
  bottom: -50px;
  left: -50px;
  animation-delay: 5s;
}

.shape-3 {
  width: 250px;
  height: 250px;
  top: 40%;
  left: 10%;
  animation-delay: 10s;
}

@keyframes floatAround {
  0%, 100% {
    transform: translate(0, 0) rotate(0deg);
  }
  25% {
    transform: translate(30px, -30px) rotate(90deg);
  }
  50% {
    transform: translate(0, -60px) rotate(180deg);
  }
  75% {
    transform: translate(-30px, -30px) rotate(270deg);
  }
}

.hero-content {
  position: relative;
  z-index: 1;
  text-align: center;
  padding: 40px 20px;
  animation: fadeInUp 1s ease-out;
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(40px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.hero-logo {
  width: 150px;
  height: 150px;
  border-radius: 50%;
  border: 6px solid #FF9966;
  box-shadow: 0 12px 48px rgba(255, 107, 53, 0.3);
  margin-bottom: 30px;
  animation: logoBounce 3s infinite;
  object-fit: cover;
}

@keyframes logoBounce {
  0%, 100% {
    transform: translateY(0) scale(1);
  }
  50% {
    transform: translateY(-20px) scale(1.05);
  }
}

.hero-title {
  font-size: 56px;
  font-weight: bold;
  color: #FF6B35;
  margin: 0 0 20px 0;
  text-shadow: 2px 2px 8px rgba(0, 0, 0, 0.1);
  animation: fadeInUp 1.2s ease-out;
}

.hero-subtitle {
  font-size: 32px;
  color: #FF9966;
  margin: 0 0 15px 0;
  animation: fadeInUp 1.4s ease-out;
}

.hero-desc {
  font-size: 18px;
  color: #666;
  margin: 0 0 40px 0;
  animation: fadeInUp 1.6s ease-out;
}

.hero-actions {
  display: flex;
  gap: 20px;
  justify-content: center;
  align-items: center;
  margin-bottom: 60px;
  animation: fadeInUp 1.8s ease-out;
}

.primary-btn {
  font-size: 20px;
  padding: 20px 50px;
  border-radius: 50px;
  background: linear-gradient(135deg, #FF9966 0%, #FF6B35 100%);
  border: none;
  font-weight: bold;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  transition: all 0.3s;
}

.primary-btn:hover {
  transform: translateY(-3px);
  box-shadow: 0 12px 32px rgba(255, 107, 53, 0.4);
}

.secondary-btn {
  font-size: 18px;
  padding: 18px 40px;
  border-radius: 50px;
  background: #FFF;
  color: #FF9966;
  border: 2px solid #FF9966;
  transition: all 0.3s;
}

.secondary-btn:hover {
  background: #FF9966;
  color: #FFF;
  transform: translateY(-3px);
  box-shadow: 0 8px 24px rgba(255, 107, 53, 0.3);
}

.scroll-hint {
  animation: fadeInUp 2s ease-out;
}

.scroll-hint p {
  font-size: 14px;
  color: #999;
  margin: 0 0 10px 0;
}

.scroll-arrow {
  font-size: 24px;
  color: #FF9966;
  animation: bounceDown 2s infinite;
}

@keyframes bounceDown {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(10px);
  }
}

/* ==================== 内容区域（滚动触发动画）==================== */
.content-sections {
  position: relative;
  z-index: 1;
}

.content-section {
  min-height: 600px;
  display: flex;
  align-items: center;
  padding: 80px 20px;
}

.section-container {
  max-width: 1200px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 60px;
  align-items: center;
}

/* 左侧布局：图片在左，文字在右 */
.layout-left .section-container {
  grid-template-areas: "image text";
}

.layout-left .section-image {
  grid-area: image;
}

.layout-left .section-text {
  grid-area: text;
}

/* 右侧布局：文字在左，图片在右 */
.layout-right .section-container {
  grid-template-areas: "text image";
}

.layout-right .section-image {
  grid-area: image;
}

.layout-right .section-text {
  grid-area: text;
}

/* 图片样式 */
.section-image {
  position: relative;
  opacity: 0;
  transition: all 0.8s cubic-bezier(0.4, 0, 0.2, 1);
}

.section-image img {
  width: 100%;
  max-width: 500px;
  height: 400px;
  object-fit: cover;
  border-radius: 30px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
  transition: transform 0.5s;
}

.section-image:hover img {
  transform: scale(1.05);
}

.image-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  border-radius: 30px;
  background: linear-gradient(135deg, rgba(255, 153, 102, 0.1), rgba(255, 107, 53, 0.1));
  pointer-events: none;
}

/* 文字样式 */
.section-text {
  opacity: 0;
  transition: all 0.8s cubic-bezier(0.4, 0, 0.2, 1);
}

.section-text h2 {
  font-size: 38px;
  color: #FF6B35;
  margin: 0 0 20px 0;
  font-weight: bold;
}

.section-text p {
  font-size: 18px;
  color: #666;
  line-height: 1.8;
  margin: 0 0 30px 0;
}

.section-decoration {
  width: 60px;
  height: 4px;
  background: linear-gradient(to right, #FF9966, #FF6B35);
  border-radius: 2px;
}

/* 滚动触发动画 - 从左滑入 */
.slide-from-left {
  transform: translateX(-100px);
}

.is-visible .slide-from-left {
  opacity: 1;
  transform: translateX(0);
}

/* 滚动触发动画 - 从右滑入 */
.slide-from-right {
  transform: translateX(100px);
}

.is-visible .slide-from-right {
  opacity: 1;
  transform: translateX(0);
}

/* ==================== 特色服务区域 ==================== */
.features-section {
  padding: 100px 20px;
  text-align: center;
  background: linear-gradient(135deg, #FFE8CC 0%, #FFF5E6 100%);
}

.section-title {
  font-size: 42px;
  color: #FF6B35;
  margin: 0 0 60px 0;
  font-weight: bold;
}

.features-grid {
  max-width: 1200px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 40px;
}

.feature-item {
  background: #FFF;
  padding: 40px 30px;
  border-radius: 20px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
  transition: all 0.3s;
  animation: fadeInUp 0.8s ease-out both;
}

.feature-item:hover {
  transform: translateY(-10px);
  box-shadow: 0 16px 48px rgba(255, 107, 53, 0.2);
}

.feature-icon {
  font-size: 56px;
  margin-bottom: 20px;
  animation: bounce 2s infinite;
}

.feature-item h3 {
  font-size: 22px;
  color: #333;
  margin: 0 0 10px 0;
}

.feature-item p {
  font-size: 16px;
  color: #999;
  margin: 0;
}

/* ==================== CTA 区域 ==================== */
.cta-section {
  padding: 100px 20px;
  text-align: center;
  background: linear-gradient(135deg, #FF9966 0%, #FF6B35 100%);
  color: #FFF;
}

.cta-content h2 {
  font-size: 42px;
  margin: 0 0 20px 0;
  font-weight: bold;
}

.cta-content p {
  font-size: 20px;
  margin: 0 0 40px 0;
  opacity: 0.95;
}

.cta-btn {
  font-size: 22px;
  padding: 22px 60px;
  border-radius: 50px;
  background: #FFF;
  color: #FF6B35;
  border: none;
  font-weight: bold;
  display: inline-flex;
  align-items: center;
  gap: 12px;
  transition: all 0.3s;
}

.cta-btn:hover {
  transform: translateY(-3px) scale(1.05);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.3);
}

/* ==================== 页脚 ==================== */
.footer {
  padding: 40px 20px;
  text-align: center;
  background: #FFF5E6;
  color: #999;
  font-size: 14px;
}

/* ==================== 响应式设计 ==================== */
@media (max-width: 768px) {
  .hero-title {
    font-size: 36px;
  }

  .hero-subtitle {
    font-size: 24px;
  }

  .hero-actions {
    flex-direction: column;
  }

  .section-container {
    grid-template-columns: 1fr !important;
    gap: 30px;
  }

  .layout-left .section-container,
  .layout-right .section-container {
    grid-template-areas: "image" "text" !important;
  }

  .section-image img {
    max-width: 100%;
    height: 300px;
  }

  .section-text h2 {
    font-size: 28px;
  }

  .section-text p {
    font-size: 16px;
  }

  .features-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 20px;
  }

  .section-title {
    font-size: 32px;
  }

  .cta-content h2 {
    font-size: 28px;
  }
}
</style>
