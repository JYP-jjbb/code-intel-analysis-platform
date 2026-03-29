import { createI18n } from 'vue-i18n'
import zhCN from './locales/zh-CN'
import en from './locales/en'

export type SupportedLocale = 'zh-CN' | 'en'

export const DEFAULT_LOCALE: SupportedLocale = 'zh-CN'
export const LOCALE_STORAGE_KEY = 'locale'

export const i18n = createI18n({
  legacy: false,
  globalInjection: true,
  locale: DEFAULT_LOCALE,
  fallbackLocale: DEFAULT_LOCALE,
  messages: {
    'zh-CN': zhCN,
    en
  }
})
