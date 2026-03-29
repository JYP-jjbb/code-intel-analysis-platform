import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { DEFAULT_LOCALE, LOCALE_STORAGE_KEY, type SupportedLocale } from '@/i18n'

export const useLocaleStore = defineStore('locale', () => {
  const locale = ref<SupportedLocale>((localStorage.getItem(LOCALE_STORAGE_KEY) as SupportedLocale) || DEFAULT_LOCALE)

  const isZh = computed(() => locale.value === 'zh-CN')

  function setLocale(next: SupportedLocale) {
    locale.value = next
    localStorage.setItem(LOCALE_STORAGE_KEY, next)
  }

  return {
    locale,
    isZh,
    setLocale
  }
})
