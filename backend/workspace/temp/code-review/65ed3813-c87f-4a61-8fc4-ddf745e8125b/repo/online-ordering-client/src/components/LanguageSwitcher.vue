<script setup lang="ts">
import { watchEffect, computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useLocaleStore } from '@/store/locale'
import { i18n, type SupportedLocale } from '@/i18n'

const localeStore = useLocaleStore()
const { locale } = storeToRefs(localeStore)

watchEffect(() => {
  i18n.global.locale.value = locale.value
})

const handleCommand = (nextLocale: SupportedLocale) => {
  localeStore.setLocale(nextLocale)
}

// 当前语言显示文本
const currentLanguageText = computed(() => {
  return locale.value === 'zh-CN' ? '中文(zh)' : '英文(en)'
})

const currentFlag = computed(() => {
  return locale.value === 'zh-CN' ? '🇨🇳' : '🇺🇸'
})
</script>

<template>
  <div class="language-switcher">
    <el-dropdown trigger="click" @command="handleCommand" placement="bottom-end">
      <div class="language-button">
        <span class="language-flag">{{ currentFlag }}</span>
        <span class="language-text">{{ currentLanguageText }}</span>
      </div>
      
      <template #dropdown>
        <el-dropdown-menu class="language-menu">
          <el-dropdown-item
            command="zh-CN"
            :class="{ 'is-active': locale === 'zh-CN' }"
          >
            <span class="menu-flag">🇨🇳</span>
            <span class="menu-text">中文(zh)</span>
            <span v-if="locale === 'zh-CN'" class="check-icon">✓</span>
          </el-dropdown-item>
          <el-dropdown-item
            command="en"
            :class="{ 'is-active': locale === 'en' }"
          >
            <span class="menu-flag">🇺🇸</span>
            <span class="menu-text">英文(en)</span>
            <span v-if="locale === 'en'" class="check-icon">✓</span>
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<style scoped>
.language-switcher {
  display: inline-block;
}

.language-button {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: white;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s ease;
  user-select: none;
}

.language-button:hover {
  border-color: #ff4757;
  background: #fff5f6;
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(255, 71, 87, 0.15);
}

.language-flag {
  font-size: 20px;
  line-height: 1;
}

.language-text {
  font-size: 14px;
  font-weight: 500;
  color: #606266;
  white-space: nowrap;
}

.language-button:hover .language-text {
  color: #ff4757;
}

/* 下拉菜单样式 */
.language-menu {
  min-width: 160px !important;
  padding: 8px !important;
}

:deep(.el-dropdown-menu__item) {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 6px;
  transition: all 0.2s ease;
}

:deep(.el-dropdown-menu__item:hover) {
  background: #fff5f6;
  color: #ff4757;
}

:deep(.el-dropdown-menu__item.is-active) {
  background: #ff4757;
  color: white;
}

.menu-flag {
  font-size: 18px;
  line-height: 1;
}

.menu-text {
  flex: 1;
  font-size: 14px;
  font-weight: 500;
}

.check-icon {
  font-size: 16px;
  font-weight: bold;
  color: #67c23a;
}

:deep(.el-dropdown-menu__item.is-active) .check-icon {
  color: white;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .language-button {
    padding: 6px 12px;
  }
  
  .language-flag {
    font-size: 18px;
  }
  
  .language-text {
    font-size: 13px;
  }
}
</style>