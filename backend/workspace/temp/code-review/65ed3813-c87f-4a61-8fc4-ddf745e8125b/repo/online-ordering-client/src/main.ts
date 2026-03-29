import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import { watch } from 'vue'

import App from './App.vue'
import router from './router'
import './style/theme.css'
import { i18n } from './i18n'
import { useLocaleStore } from './store/locale'

const app = createApp(App)

const pinia = createPinia()
app.use(pinia)
app.use(router)
app.use(ElementPlus)
app.use(i18n)

const localeStore = useLocaleStore(pinia)
i18n.global.locale.value = localeStore.locale

const updateTitle = () => {
  const to = router.currentRoute.value
  const titleKey = to.meta.titleKey as string | undefined
  const title = titleKey ? i18n.global.t(titleKey) : (to.meta.title as string | undefined) || ''
  const appName = i18n.global.t('app.name')
  document.title = title ? `${title} - ${appName}` : `${appName}`
}

watch(
  () => localeStore.locale,
  () => {
    i18n.global.locale.value = localeStore.locale
    updateTitle()
  }
)

router.afterEach(() => {
  updateTitle()
})

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.mount('#app')




