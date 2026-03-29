<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'
import { login, register, getCaptcha, sendEmailCode } from '@/api/auth'
import { ElMessage } from 'element-plus'
import { User, Lock, Phone, Message } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { useI18n } from 'vue-i18n'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const { t } = useI18n()

const isLogin = ref(true)
const loginType = ref<'user' | 'admin'>('user') // 登录类型：用户或管理员
const captchaUrl = ref('')
const captchaId = ref('')

const loginFormRef = ref<FormInstance>()
const registerFormRef = ref<FormInstance>()

const loginForm = reactive({
  username: '',
  password: '',
  captcha: ''
})

const registerForm = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  phone: '',
  email: '',
  emailCode: '',
  captcha: ''
})

// 邮箱验证码相关状态
const emailCodeSending = ref(false)
const emailCodeCountdown = ref(0)
let countdownTimer: number | null = null

const refreshCaptcha = async () => {
  try {
    const res = await getCaptcha()
    captchaUrl.value = res.data.captchaImage
    captchaId.value = res.data.captchaId
  } catch (error) {
    console.error(error)
  }
}

// 发送邮箱验证码
const handleSendEmailCode = async () => {
  // 1. 验证邮箱格式
  if (!registerForm.email) {
    ElMessage.warning('请先输入邮箱地址')
    return
  }
  
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  if (!emailRegex.test(registerForm.email)) {
    ElMessage.warning('邮箱格式不正确')
    return
  }
  
  // 2. 验证图形验证码
  if (!registerForm.captcha) {
    ElMessage.warning('请先输入图形验证码')
    return
  }
  
  // 3. 检查是否在倒计时中
  if (emailCodeCountdown.value > 0) {
    return
  }
  
  try {
    emailCodeSending.value = true
    
    // 4. 调用发送接口
    await sendEmailCode({
      email: registerForm.email,
      scene: 'register',
      captcha: registerForm.captcha,
      captchaId: captchaId.value
    })
    
    ElMessage.success('验证码已发送，请查收邮件')
    
    // 5. 开始倒计时
    startCountdown()
    
    // 6. 刷新图形验证码
    refreshCaptcha()
    registerForm.captcha = ''
    
  } catch (error: any) {
    // 处理各种错误情况
    const errorMsg = error?.message || '发送失败'
    
    if (errorMsg.includes('过于频繁')) {
      ElMessage.warning(errorMsg)
    } else if (errorMsg.includes('图形验证码')) {
      ElMessage.error('图形验证码错误或已过期')
      refreshCaptcha()
      registerForm.captcha = ''
    } else if (errorMsg.includes('锁定')) {
      ElMessage.error(errorMsg)
    } else if (errorMsg.includes('上限')) {
      ElMessage.error(errorMsg)
    } else {
      ElMessage.error('验证码发送失败，请稍后重试')
    }
  } finally {
    emailCodeSending.value = false
  }
}

// 开始倒计时
const startCountdown = () => {
  emailCodeCountdown.value = 60
  
  if (countdownTimer) {
    clearInterval(countdownTimer)
  }
  
  countdownTimer = window.setInterval(() => {
    emailCodeCountdown.value--
    
    if (emailCodeCountdown.value <= 0) {
      clearInterval(countdownTimer!)
      countdownTimer = null
    }
  }, 1000)
}

// 计算邮箱验证码按钮文本
const emailCodeButtonText = computed(() => {
  if (emailCodeCountdown.value > 0) {
    return `${emailCodeCountdown.value}秒后重试`
  }
  return '获取验证码'
})

// 计算邮箱验证码按钮是否禁用
const emailCodeButtonDisabled = computed(() => {
  return emailCodeSending.value || emailCodeCountdown.value > 0
})

// 初始化验证码
refreshCaptcha()

const handleLogin = async (formEl: FormInstance | undefined) => {
  if (!formEl) return
  await formEl.validate(async (valid) => {
    if (valid) {
      try {
        const res = await login({
          ...loginForm,
          captchaId: captchaId.value
        })
        
        const userRole = res.data.user.role
        
        // 验证登录类型是否匹配
        if (loginType.value === 'admin' && userRole !== 'admin') {
          ElMessage.error(t('login.messages.notAdmin'))
          refreshCaptcha()
          return
        }
        
        if (loginType.value === 'user' && userRole === 'admin') {
          ElMessage.warning(t('login.messages.adminDetected'))
          refreshCaptcha()
          return
        }
        
        userStore.setToken(res.data.token)
        userStore.setUserInfo(res.data.user)
        ElMessage.success(t('login.messages.success'))
        
        // 根据角色跳转到不同页面
        if (userRole === 'admin') {
          router.push('/admin/dishes')
        } else {
          const redirect = route.query.redirect as string || '/categories'
          router.push(redirect)
        }
      } catch (error) {
        refreshCaptcha()
      }
    }
  })
}

const handleRegister = async (formEl: FormInstance | undefined) => {
  if (!formEl) return
  await formEl.validate(async (valid) => {
    if (valid) {
      // 1. 验证两次密码
      if (registerForm.password !== registerForm.confirmPassword) {
        ElMessage.error(t('login.messages.passwordMismatch'))
        return
      }
      
      // 2. 验证邮箱验证码
      if (!registerForm.emailCode) {
        ElMessage.warning('请输入邮箱验证码')
        return
      }
      
      if (registerForm.emailCode.length !== 6) {
        ElMessage.warning('邮箱验证码必须为6位')
        return
      }
      
      try {
        await register({
          ...registerForm,
          captchaId: captchaId.value
        })
        
        ElMessage.success(t('login.messages.registerSuccess'))
        isLogin.value = true
        loginType.value = 'user'
        
        // 清空表单
        Object.keys(registerForm).forEach(key => {
          registerForm[key as keyof typeof registerForm] = ''
        })
        
        refreshCaptcha()
      } catch (error: any) {
        const errorMsg = error?.message || '注册失败'
        
        // 处理各种错误情况
        if (errorMsg.includes('验证码')) {
          if (errorMsg.includes('过期')) {
            ElMessage.error('邮箱验证码已过期，请重新获取')
          } else if (errorMsg.includes('错误')) {
            ElMessage.error(errorMsg)
          } else if (errorMsg.includes('锁定')) {
            ElMessage.error(errorMsg)
          } else {
            ElMessage.error('邮箱验证码验证失败')
          }
        } else if (errorMsg.includes('邮箱已被注册')) {
          ElMessage.error('该邮箱已被注册')
        } else if (errorMsg.includes('用户名已存在')) {
          ElMessage.error('该用户名已存在')
        } else {
          ElMessage.error(errorMsg)
        }
        
        refreshCaptcha()
      }
    }
  })
}

const rules = computed<FormRules>(() => ({
  username: [{ required: true, message: t('login.rules.usernameRequired'), trigger: 'blur' }],
  password: [{ required: true, message: t('login.rules.passwordRequired'), trigger: 'blur' }],
  captcha: [{ required: true, message: t('login.rules.captchaRequired'), trigger: 'blur' }]
}))

const headerTitle = computed(() => {
  if (!isLogin.value) return t('login.register')
  return loginType.value === 'admin' ? t('login.adminLogin') : t('login.userLogin')
})

const switchText = computed(() => (isLogin.value ? t('login.toRegister') : t('login.toLogin')))

const loginTypeOptions = computed(() => [
  { label: t('login.userLogin'), value: 'user' },
  { label: t('login.adminLogin'), value: 'admin' }
])

// 切换登录类型时刷新验证码
const switchLoginType = (type: 'user' | 'admin') => {
  loginType.value = type
  refreshCaptcha()
}
</script>

<template>
  <div class="login-container">
    <div class="login-banner">
      <img src="/logo.png" alt="Logo" class="banner-logo" />
      <h1 class="banner-title">🍜 {{ $t('app.name') }}</h1>
      <p class="banner-subtitle">{{ $t('login.subtitle') }}</p>
    </div>

    <el-card class="box-card" shadow="always">
      <template #header>
        <div class="header">
          <span class="header-title">{{ headerTitle }}</span>
          <el-button link class="switch-btn" @click="isLogin = !isLogin; loginType = 'user'; refreshCaptcha()">
            {{ switchText }}
          </el-button>
        </div>
      </template>

      <div v-if="isLogin" class="form-wrapper">
        <!-- 登录类型切换 -->
        <div class="login-type-switch">
          <el-segmented 
            v-model="loginType" 
            :options="loginTypeOptions"
            size="large"
            @change="switchLoginType"
          />
        </div>

        <el-form ref="loginFormRef" :model="loginForm" :rules="rules" label-width="80px">
          <el-form-item :label="$t('login.username')" prop="username">
            <el-input 
              v-model="loginForm.username" 
              :placeholder="$t('login.placeholders.username')"
              :prefix-icon="User"
            />
          </el-form-item>
          <el-form-item :label="$t('login.password')" prop="password">
            <el-input 
              v-model="loginForm.password" 
              type="password" 
              :placeholder="$t('login.placeholders.password')" 
              show-password
              :prefix-icon="Lock"
            />
          </el-form-item>
          <el-form-item :label="$t('login.captcha')" prop="captcha">
            <div class="captcha-box">
              <el-input v-model="loginForm.captcha" :placeholder="$t('login.placeholders.captcha')" />
              <img :src="captchaUrl" @click="refreshCaptcha" class="captcha-img" :title="$t('login.refreshCaptcha')" />
            </div>
          </el-form-item>
          <el-form-item>
            <el-button 
              type="warning" 
              @click="handleLogin(loginFormRef)" 
              style="width: 100%"
              size="large"
              class="submit-btn"
            >
              {{ $t('login.submitLogin') }}
            </el-button>
          </el-form-item>
          <div class="hint-text">
            <p v-if="loginType === 'admin'">{{ $t('login.hints.admin') }}</p>
            <p v-else>{{ $t('login.hints.user') }}</p>
          </div>
        </el-form>
      </div>

      <div v-else class="form-wrapper">
        <el-form ref="registerFormRef" :model="registerForm" :rules="rules" label-width="80px">
          <el-form-item :label="$t('login.username')" prop="username">
            <el-input 
              v-model="registerForm.username"
              :placeholder="$t('login.placeholders.username')"
              :prefix-icon="User"
            />
          </el-form-item>
          <el-form-item :label="$t('login.password')" prop="password">
            <el-input 
              v-model="registerForm.password" 
              type="password" 
              show-password
              :placeholder="$t('login.placeholders.password')"
              :prefix-icon="Lock"
            />
          </el-form-item>
          <el-form-item :label="$t('login.confirmPassword')" prop="confirmPassword">
            <el-input 
              v-model="registerForm.confirmPassword" 
              type="password" 
              show-password
              :placeholder="$t('login.placeholders.confirmPassword')"
              :prefix-icon="Lock"
            />
          </el-form-item>
          <el-form-item :label="$t('login.phone')">
            <el-input 
              v-model="registerForm.phone"
              :placeholder="$t('login.placeholders.phone')"
              :prefix-icon="Phone"
            />
          </el-form-item>
          <el-form-item :label="$t('login.email')">
            <el-input 
              v-model="registerForm.email"
              :placeholder="$t('login.placeholders.email')"
              :prefix-icon="Message"
            />
          </el-form-item>
          <el-form-item label="邮箱验证码" prop="emailCode">
            <div class="email-code-box">
              <el-input 
                v-model="registerForm.emailCode"
                placeholder="请输入6位邮箱验证码"
                maxlength="6"
              />
              <el-button 
                type="primary" 
                :disabled="emailCodeButtonDisabled"
                :loading="emailCodeSending"
                @click="handleSendEmailCode"
                class="email-code-btn"
              >
                {{ emailCodeButtonText }}
              </el-button>
            </div>
          </el-form-item>
          <el-form-item label="图形验证码" prop="captcha">
            <div class="captcha-box">
              <el-input v-model="registerForm.captcha" placeholder="请输入图形验证码" />
              <img :src="captchaUrl" @click="refreshCaptcha" class="captcha-img" :title="$t('login.refreshCaptcha')" />
            </div>
          </el-form-item>
          <el-form-item>
            <el-button 
              type="warning" 
              @click="handleRegister(registerFormRef)" 
              style="width: 100%"
              size="large"
              class="submit-btn"
            >
              {{ $t('login.submitRegister') }}
            </el-button>
          </el-form-item>
        </el-form>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.login-container {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #FFF5E6 0%, #FFE8CC 100%);
  padding: 20px;
  animation: fadeIn 0.6s ease-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.login-banner {
  text-align: center;
  margin-bottom: 30px;
  animation: slideInDown 0.8s ease-out;
}

@keyframes slideInDown {
  from {
    opacity: 0;
    transform: translateY(-30px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.banner-logo {
  height: 80px;
  width: 80px;
  object-fit: cover;
  border-radius: 50%;
  border: 4px solid #FF9966;
  box-shadow: 0 4px 16px rgba(255, 107, 53, 0.3);
  margin-bottom: 20px;
  animation: bounceIn 1s ease-out;
}

@keyframes bounceIn {
  0% {
    opacity: 0;
    transform: scale(0.3);
  }
  50% {
    opacity: 1;
    transform: scale(1.05);
  }
  70% {
    transform: scale(0.9);
  }
  100% {
    transform: scale(1);
  }
}

.banner-title {
  font-size: 32px;
  font-weight: bold;
  color: #FF6B35;
  margin: 15px 0;
  text-shadow: 2px 2px 4px rgba(0, 0, 0, 0.1);
}

.banner-subtitle {
  font-size: 16px;
  color: #FF9966;
  margin: 0;
}

.box-card {
  width: 100%;
  max-width: 450px;
  border-radius: 20px;
  overflow: hidden;
  box-shadow: 0 8px 32px rgba(255, 107, 53, 0.2);
  animation: zoomIn 0.6s ease-out;
}

@keyframes zoomIn {
  from {
    opacity: 0;
    transform: scale(0.9);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

.box-card :deep(.el-card__header) {
  background: linear-gradient(135deg, #FF9966 0%, #FF6B35 100%);
  border-bottom: none;
  padding: 20px;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-title {
  font-size: 20px;
  font-weight: bold;
  color: #FFF;
}

.switch-btn {
  color: #FFF !important;
  font-weight: bold;
  transition: all 0.3s;
}

.switch-btn:hover {
  transform: translateX(5px);
}

.form-wrapper {
  padding: 10px;
}

.login-type-switch {
  margin-bottom: 20px;
  display: flex;
  justify-content: center;
}

.login-type-switch :deep(.el-segmented) {
  width: 100%;
  background: #FFF5E6;
  padding: 4px;
  border-radius: 12px;
}

.login-type-switch :deep(.el-segmented__item) {
  font-weight: bold;
  color: #FF9966;
  border-radius: 8px;
}

.login-type-switch :deep(.el-segmented__item.is-selected) {
  background: linear-gradient(135deg, #FF9966 0%, #FF6B35 100%);
  color: #FFF;
  box-shadow: 0 2px 8px rgba(255, 107, 53, 0.3);
}

.captcha-box {
  display: flex;
  gap: 10px;
  width: 100%;
}

.captcha-img {
  height: 40px;
  border-radius: 8px;
  cursor: pointer;
  border: 2px solid #FFE8CC;
  transition: all 0.3s;
}

.captcha-img:hover {
  border-color: #FF9966;
  transform: scale(1.05);
}

.email-code-box {
  display: flex;
  gap: 10px;
  width: 100%;
}

.email-code-btn {
  min-width: 120px;
  white-space: nowrap;
  border-radius: 8px;
  font-weight: bold;
  background: linear-gradient(135deg, #4CAF50 0%, #45a049 100%);
  border: none;
  transition: all 0.3s;
}

.email-code-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(76, 175, 80, 0.4);
}

.email-code-btn:disabled {
  background: #e0e0e0;
  color: #999;
  cursor: not-allowed;
}

.submit-btn {
  font-weight: bold;
  border-radius: 25px;
  background: linear-gradient(135deg, #FF9966 0%, #FF6B35 100%);
  border: none;
  transition: all 0.3s;
}

.submit-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(255, 107, 53, 0.4);
}

.hint-text {
  text-align: center;
  margin-top: 10px;
}

.hint-text p {
  color: #999;
  font-size: 13px;
  margin: 5px 0;
}

/* Element Plus 组件样式覆盖 */
:deep(.el-input__wrapper) {
  border-radius: 10px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

:deep(.el-input__wrapper:hover) {
  box-shadow: 0 2px 12px rgba(255, 107, 53, 0.2);
}

:deep(.el-form-item__label) {
  font-weight: bold;
  color: #666;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .banner-title {
    font-size: 24px;
  }

  .box-card {
    max-width: 100%;
  }
}
</style>
