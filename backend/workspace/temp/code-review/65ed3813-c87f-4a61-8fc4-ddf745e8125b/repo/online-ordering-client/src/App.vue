<script setup lang="ts">
import { RouterView } from 'vue-router'
import { computed } from 'vue'
import { useLocaleStore } from '@/store/locale'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import en from 'element-plus/es/locale/lang/en'

const localeStore = useLocaleStore()
const elementLocale = computed(() => (localeStore.locale === 'zh-CN' ? zhCn : en))
</script>

<template>
  <el-config-provider :locale="elementLocale">
    <RouterView />
  </el-config-provider>
</template>

<style>
html, body {
  margin: 0;
  padding: 0;
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB',
  'Microsoft YaHei', '微软雅黑', Arial, sans-serif;
  background-color: #f5f7fa;
  color: #303133;
}

#app {
  min-height: 100vh;
}
</style>












