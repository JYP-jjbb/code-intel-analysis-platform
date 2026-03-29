<script setup lang="ts">
import { ref, onMounted, computed, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useCartStore } from '@/store/cart'
import { useUserStore } from '@/store/user'
import { useDeliveryStore } from '@/store/delivery'
import { submitOrder } from '@/api/order'
import { createPayment, checkPaymentStatus, simulatePayment } from '@/api/payment'
import { getAddressList, type Address } from '@/api/address'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { 
  ArrowLeft, 
  Location, 
  User, 
  Phone, 
  Edit, 
  ShoppingBag,
  Wallet,
  Check,
  Close,
  Loading,
  Timer,
  ForkSpoon,
  Clock,
  Star,
  Menu,
  QuestionFilled,
  ArrowDown,
  SwitchButton,
  Setting,
  Plus
} from '@element-plus/icons-vue'
import type { PaymentInfo } from '@/api/payment'

const router = useRouter()
const cartStore = useCartStore()
const userStore = useUserStore()
const deliveryStore = useDeliveryStore()

const { t } = useI18n()

// 表单数据
const form = ref({
  address: '',
  receiverName: '',
  receiverPhone: '',
  remark: '',
  paymentMethod: 'wechat' as 'wechat' | 'alipay',
  deliveryTime: '',
  saveAsDefault: false
})

// 使用delivery store的配送方式
const deliveryMode = computed({
  get: () => deliveryStore.deliveryMode,
  set: (value) => deliveryStore.setDeliveryMode(value)
})

// 支付相关状态
const showPayment = ref(false)
const paymentInfo = ref<PaymentInfo | null>(null)
const currentOrderId = ref<number | null>(null)
const paymentTimer = ref<number | null>(null)
const countdown = ref(300) // 5分钟倒计时

// 地址管理
const addressList = ref<Address[]>([])
const showAddressSelector = ref(false)
const selectedAddressId = ref<number | null>(null)

// 格式化倒计时
const formattedCountdown = computed(() => {
  const minutes = Math.floor(countdown.value / 60)
  const seconds = countdown.value % 60
  return `${minutes}:${seconds.toString().padStart(2, '0')}`
})

// 餐段选项
const mealPeriodOptions = computed(() => [
  { label: t('orderConfirm.delivery.lunch'), value: 'lunch' },
  { label: t('orderConfirm.delivery.dinner'), value: 'dinner' }
])

// 获取当前餐段的时间段选项
const deliveryTimeOptions = computed(() => {
  if (deliveryStore.mealPeriod) {
    return deliveryStore.getTimeSlotsByMealPeriod(deliveryStore.mealPeriod)
  }
  return []
})

// 同步form.deliveryTime到deliveryStore
watch(() => deliveryStore.selectedTime, (newTime) => {
  form.value.deliveryTime = newTime || ''
})

// 同步deliveryStore到form.deliveryTime
watch(() => form.value.deliveryTime, (newTime) => {
  if (newTime) {
    deliveryStore.setSelectedTime(newTime)
  }
})

// 计算预计送达时间
const estimatedDeliveryTime = computed(() => {
  if (deliveryStore.deliveryMode === 'immediate') {
    return t('orderConfirm.delivery.estimatedImmediate')
  }
  
  if (!deliveryStore.selectedTime) return ''
  
  // 解析选择的时间段（格式：HH:MM - HH:MM）
  const timeRange = deliveryStore.selectedTime.split(' - ')
  if (timeRange.length !== 2) return ''
  
  const endTime = timeRange[1]
  return t('orderConfirm.delivery.estimatedAt', { time: endTime })
})

// 切换配送方式
const handleDeliveryModeChange = () => {
  if (deliveryStore.deliveryMode === 'immediate') {
    form.value.deliveryTime = ''
    deliveryStore.setSelectedTime('')
    deliveryStore.setMealPeriod('')
  }
}

// 使用默认地址
const useDefaultAddress = () => {
  if (userStore.userInfo?.defaultAddress) {
    form.value.address = userStore.userInfo.defaultAddress
    form.value.receiverName = userStore.userInfo.defaultReceiverName || ''
    form.value.receiverPhone = userStore.userInfo.defaultReceiverPhone || ''
    ElMessage.success(t('orderConfirm.messages.fillDefault'))
  } else {
    ElMessage.info(t('orderConfirm.messages.noDefault'))
  }
}

// 将时间段转换为ISO格式的DateTime字符串
const convertTimeRangeToDateTime = (timeRange: string): string => {
  // 解析时间段（格式：HH:MM - HH:MM）
  const timeRangeParts = timeRange.split(' - ')
  if (timeRangeParts.length !== 2) return ''
  
  const startTime = timeRangeParts[0]
  const [hours, minutes] = startTime.split(':').map(Number)
  
  // 创建今天的日期，设置为选择的时间
  const deliveryDate = new Date()
  deliveryDate.setHours(hours, minutes, 0, 0)
  
  // 如果选择的时间已经过了，则设置为明天
  if (deliveryDate < new Date()) {
    deliveryDate.setDate(deliveryDate.getDate() + 1)
  }
  
  // 返回ISO格式字符串
  return deliveryDate.toISOString()
}

onMounted(async () => {
  if (cartStore.cartItems.length === 0) {
    cartStore.fetchCart()
  }
  
  // 同步deliveryStore的selectedTime到form
  if (deliveryStore.selectedTime) {
    form.value.deliveryTime = deliveryStore.selectedTime
  }
  
  // 获取地址列表
  await fetchAddresses()
  
  // 如果有默认地址，自动填充
  if (userStore.userInfo?.defaultAddress) {
    useDefaultAddress()
  }
})

// 获取地址列表
const fetchAddresses = async () => {
  try {
    const res = await getAddressList()
    if (res.code === 200) {
      addressList.value = res.data
      // 自动选择默认地址
      const defaultAddr = addressList.value.find(addr => addr.isDefault)
      if (defaultAddr) {
        selectAddress(defaultAddr)
      }
    }
  } catch (error) {
    console.error('获取地址列表失败:', error)
  }
}

// 选择地址
const selectAddress = (address: Address) => {
  form.value.receiverName = address.receiverName
  form.value.receiverPhone = address.receiverPhone
  form.value.address = address.address
  selectedAddressId.value = address.id || null
  showAddressSelector.value = false
}

// 打开地址选择器
const openAddressSelector = () => {
  showAddressSelector.value = true
}

// 跳转到地址管理页面
const goToAddressManage = () => {
  router.push({ path: '/profile', query: { tab: 'addresses' } })
}

onUnmounted(() => {
  if (paymentTimer.value) {
    clearInterval(paymentTimer.value)
  }
})

// 提交订单
const handleSubmit = async () => {
  // 验证表单
  if (!form.value.receiverName) {
    ElMessage.warning(t('orderConfirm.validation.receiverNameRequired'))
    return
  }
  if (!form.value.receiverPhone) {
    ElMessage.warning(t('orderConfirm.validation.phoneRequired'))
    return
  }
  if (!/^1[3-9]\d{9}$/.test(form.value.receiverPhone)) {
    ElMessage.warning(t('orderConfirm.validation.phoneInvalid'))
    return
  }
  if (!form.value.address) {
    ElMessage.warning(t('orderConfirm.validation.addressRequired'))
    return
  }
  
  // 验证预约配送时间
  if (deliveryStore.deliveryMode === 'scheduled') {
    if (!deliveryStore.mealPeriod) {
      ElMessage.warning(t('orderConfirm.validation.mealPeriodRequired'))
      return
    }
    if (!form.value.deliveryTime) {
      ElMessage.warning(t('orderConfirm.validation.deliveryTimeRequired'))
      return
    }
  }

  try {
    // 准备订单数据
    const orderData: any = {
      address: form.value.address,
      receiverName: form.value.receiverName,
      receiverPhone: form.value.receiverPhone,
      remark: form.value.remark || '',
      paymentMethod: form.value.paymentMethod,
      saveAsDefault: form.value.saveAsDefault || false
    }
    
    // 只在预约配送时添加 deliveryTime
    if (deliveryStore.deliveryMode === 'scheduled' && form.value.deliveryTime) {
      orderData.deliveryTime = convertTimeRangeToDateTime(form.value.deliveryTime)
    }
    
    console.log('提交订单数据：', orderData)
    
    // 1. 提交订单
    const res = await submitOrder(orderData)
    currentOrderId.value = res.data.id
    
    ElMessage.success(t('orderConfirm.messages.createSuccess'))
    
    // 2. 创建支付订单（生成二维码）
    const paymentRes = await createPayment({
      orderId: res.data.id,
      paymentType: form.value.paymentMethod
    })
    
    paymentInfo.value = paymentRes.data
    showPayment.value = true
    
    // 3. 开始轮询支付状态
    startPaymentCheck()
    
  } catch (error: any) {
    console.error('提交订单失败:', error)
    console.error('错误详情:', error.response?.data)
    const errorMsg = error.response?.data?.message || error.message || t('orderConfirm.messages.submitFail')
    ElMessage.error(errorMsg)
  }
}

// 开始检查支付状态
const startPaymentCheck = () => {
  // 倒计时
  paymentTimer.value = window.setInterval(() => {
    countdown.value--
    
    if (countdown.value <= 0) {
      handlePaymentTimeout()
      return
    }
    
    // 每3秒检查一次支付状态
    if (countdown.value % 3 === 0) {
      checkPayment()
    }
  }, 1000)
}

// 检查支付状态
const checkPayment = async () => {
  if (!currentOrderId.value) return
  
  try {
    const res = await checkPaymentStatus(currentOrderId.value)
    if (res.data.isPaid) {
      handlePaymentSuccess()
    }
  } catch (error) {
    console.error('检查支付状态失败:', error)
  }
}

// 支付成功
const handlePaymentSuccess = () => {
  if (paymentTimer.value) {
    clearInterval(paymentTimer.value)
  }
  
  ElMessageBox.alert(t('orderConfirm.paymentDialog.successMessage'), t('common.tip'), {
    confirmButtonText: t('orderConfirm.paymentDialog.viewOrder'),
    type: 'success',
    callback: () => {
      cartStore.clearCart()
      router.push(`/order/${currentOrderId.value}`)
    }
  })
}

// 支付超时
const handlePaymentTimeout = () => {
  if (paymentTimer.value) {
    clearInterval(paymentTimer.value)
  }
  
  ElMessageBox.confirm(t('orderConfirm.paymentDialog.timeoutMessage'), t('common.tip'), {
    confirmButtonText: t('orderConfirm.paymentDialog.continuePay'),
    cancelButtonText: t('orderConfirm.paymentDialog.payLater'),
    type: 'warning'
  }).then(() => {
    // 重新生成二维码
    countdown.value = 300
    if (currentOrderId.value) {
      createPayment({
        orderId: currentOrderId.value,
        paymentType: form.value.paymentMethod
      }).then(res => {
        paymentInfo.value = res.data
        startPaymentCheck()
      })
    }
  }).catch(() => {
    showPayment.value = false
    // 清空购物车
    cartStore.clearCart()
    ElMessage.info(t('orderConfirm.messages.saveForLater'))
    // 跳转到订单列表
    router.push('/orders')
  })
}

// 取消支付
const cancelPayment = () => {
  if (paymentTimer.value) {
    clearInterval(paymentTimer.value)
  }
  showPayment.value = false
  countdown.value = 300
  
  ElMessageBox.confirm(t('orderConfirm.paymentDialog.cancelPayMessage'), t('common.tip'), {
    confirmButtonText: t('orderConfirm.paymentDialog.viewOrder'),
    cancelButtonText: t('orderConfirm.paymentDialog.continueShopping'),
    type: 'info'
  }).then(() => {
    // 清空购物车
    cartStore.clearCart()
    // 跳转到订单列表
    router.push('/orders')
  }).catch(() => {
    // 清空购物车
    cartStore.clearCart()
    // 继续购物，跳转到分类页面
    router.push('/categories')
  })
}

// 模拟支付（测试用）
const handleSimulatePayment = async () => {
  if (!currentOrderId.value) return
  
  try {
    await simulatePayment(currentOrderId.value)
    ElMessage.success(t('orderConfirm.messages.simulateSuccess'))
    await checkPayment()
  } catch (error) {
    ElMessage.error(t('orderConfirm.messages.simulateFail'))
  }
}

// 支付方式名称
const paymentMethodName = computed(() => {
  return form.value.paymentMethod === 'wechat'
    ? t('orderConfirm.payment.wechat')
    : t('orderConfirm.payment.alipay')
})

const paymentDialogTitle = computed(() => {
  return t('orderConfirm.paymentDialog.title', { method: paymentMethodName.value })
})

// 支付方式图标
const paymentIcon = computed(() => {
  return form.value.paymentMethod === 'wechat' ? '💚' : '💙'
})

// 退出登录
const handleLogout = async () => {
  await userStore.logout()
  ElMessage.success(t('orderConfirm.messages.logoutSuccess'))
  router.push('/login')
}
</script>

<template>
  <div class="confirm-container">
    <!-- 顶部导航栏 -->
    <div class="top-nav-bar">
      <div class="nav-left">
        <img src="/logo.png" alt="Logo" class="nav-logo" />
        <span class="nav-brand">{{ $t('app.name') }}</span>
        
        <!-- 导航菜单项（移到左侧） -->
        <div class="nav-menu">
          <div 
            class="nav-link" 
            @click="router.push('/categories')"
          >
            <el-icon><Menu /></el-icon>
            <span>{{ $t('nav.menu') }}</span>
          </div>
          <div 
            class="nav-link"
            @click="router.push('/profile')"
          >
            <el-icon><User /></el-icon>
            <span>{{ $t('nav.profile') }}</span>
          </div>
          <div 
            class="nav-link"
            @click="ElMessage.info(t('home.helpDeveloping'))"
          >
            <el-icon><QuestionFilled /></el-icon>
            <span>{{ $t('nav.help') }}</span>
          </div>
        </div>
      </div>
      
      <div class="nav-right">
        <template v-if="userStore.userInfo">
          <div class="user-info-display">
            <el-avatar :size="36" class="user-avatar-small">
              <el-icon><User /></el-icon>
            </el-avatar>
            <div class="user-details">
              <div class="user-name">{{ userStore.userInfo.username }}</div>
              <div class="user-role">{{ userStore.userInfo.role === 'admin' ? $t('home.roleAdmin') : $t('home.roleMember') }}</div>
            </div>
          </div>
          <el-dropdown trigger="click">
            <el-button circle class="user-menu-btn">
              <el-icon><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="router.push('/profile')">
                  <el-icon><User /></el-icon>
                  {{ $t('nav.profile') }}
                </el-dropdown-item>
                <el-dropdown-item @click="router.push('/orders')">
                  <el-icon><ShoppingBag /></el-icon>
                  {{ $t('nav.orders') }}
                </el-dropdown-item>
                <el-dropdown-item v-if="userStore.userInfo.role === 'admin'" divided @click="router.push('/admin/dishes')">
                  <el-icon><Setting /></el-icon>
                  {{ $t('nav.adminPanel') }}
                </el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">
                  <el-icon><SwitchButton /></el-icon>
                  {{ $t('nav.logout') }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <template v-else>
          <el-button type="warning" @click="router.push('/login')" class="login-btn">
            {{ $t('nav.loginRegister') }}
          </el-button>
        </template>
      </div>
    </div>

    <!-- 页面标题 -->
    <div class="page-header">
      <h1 class="page-title">
        <el-icon><ShoppingBag /></el-icon>
        {{ $t('orderConfirm.title') }}
      </h1>
    </div>

    <!-- 主内容区 -->
    <div class="content-wrapper">
      <!-- 左侧：订单信息 -->
      <div class="left-section">
        <!-- 收货信息卡片 -->
        <el-card class="info-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <el-icon><Location /></el-icon>
              <span>{{ $t('orderConfirm.section.address') }}</span>
              <div class="header-actions">
                <el-button 
                  type="primary" 
                  link 
                  @click="openAddressSelector"
                  class="select-addr-btn"
                >
                  <el-icon><Location /></el-icon>
                  {{ $t('orderConfirm.addressSelector.select') }}
                </el-button>
                <el-button 
                  type="warning" 
                  link 
                  @click="goToAddressManage"
                  class="manage-addr-btn"
                >
                  <el-icon><Setting /></el-icon>
                  {{ $t('orderConfirm.addressSelector.manage') }}
                </el-button>
              </div>
            </div>
          </template>
          
          <el-form :model="form" label-width="90px" class="order-form">
            <el-form-item :label="$t('orderConfirm.form.receiverName')" required>
              <el-input 
                v-model="form.receiverName" 
                :placeholder="$t('orderConfirm.placeholders.receiverName')"
                :prefix-icon="User"
                maxlength="20"
                show-word-limit
              />
            </el-form-item>
            
            <el-form-item :label="$t('orderConfirm.form.receiverPhone')" required>
              <el-input 
                v-model="form.receiverPhone" 
                :placeholder="$t('orderConfirm.placeholders.receiverPhone')"
                :prefix-icon="Phone"
                maxlength="11"
              />
            </el-form-item>
            
            <el-form-item :label="$t('orderConfirm.form.address')" required>
              <el-input 
                v-model="form.address" 
                type="textarea" 
                :rows="3"
                :placeholder="$t('orderConfirm.placeholders.address')"
                maxlength="200"
                show-word-limit
              />
            </el-form-item>
            
            <el-form-item :label="$t('orderConfirm.form.remark')">
              <el-input 
                v-model="form.remark" 
                :placeholder="$t('orderConfirm.placeholders.remark')"
                :prefix-icon="Edit"
                maxlength="100"
                show-word-limit
              />
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 配送时间卡片 -->
        <el-card class="info-card delivery-time-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <el-icon><Clock /></el-icon>
              <span>{{ $t('orderConfirm.delivery.title') }}</span>
            </div>
          </template>
          
          <el-form :model="form" label-width="90px" class="order-form">
            <el-form-item :label="$t('orderConfirm.delivery.mode')">
              <el-radio-group v-model="deliveryStore.deliveryMode" @change="handleDeliveryModeChange" class="delivery-mode-group">
                <el-radio label="immediate" class="delivery-mode-radio">
                  <div class="radio-content">
                    <div class="radio-title">
                      <el-icon><Timer /></el-icon>
                      {{ $t('orderConfirm.delivery.immediate') }}
                    </div>
                    <div class="radio-desc">{{ $t('orderConfirm.delivery.immediateDesc') }}</div>
                  </div>
                </el-radio>
                <el-radio label="scheduled" class="delivery-mode-radio">
                  <div class="radio-content">
                    <div class="radio-title">
                      <el-icon><Clock /></el-icon>
                      {{ $t('orderConfirm.delivery.scheduled') }}
                    </div>
                    <div class="radio-desc">{{ $t('orderConfirm.delivery.scheduledDesc') }}</div>
                  </div>
                </el-radio>
              </el-radio-group>
            </el-form-item>
            
            <el-form-item :label="$t('orderConfirm.delivery.mealPeriod')" v-if="deliveryStore.deliveryMode === 'scheduled'">
              <el-radio-group v-model="deliveryStore.mealPeriod" class="meal-period-group">
                <el-radio 
                  v-for="option in mealPeriodOptions" 
                  :key="option.value"
                  :label="option.value"
                  class="meal-period-radio"
                >
                  {{ option.label }}
                </el-radio>
              </el-radio-group>
            </el-form-item>
            
            <el-form-item :label="$t('orderConfirm.delivery.time')" v-if="deliveryStore.deliveryMode === 'scheduled' && deliveryStore.mealPeriod">
              <el-select 
                v-model="form.deliveryTime" 
                :placeholder="$t('orderConfirm.delivery.timePlaceholder')"
                class="delivery-time-select"
                @change="(val) => deliveryStore.setSelectedTime(val)"
              >
                <el-option
                  v-for="time in deliveryTimeOptions"
                  :key="time"
                  :label="time"
                  :value="time"
                />
              </el-select>
            </el-form-item>
            
            <el-alert 
              :title="estimatedDeliveryTime"
              :type="deliveryStore.deliveryMode === 'immediate' ? 'info' : 'success'"
              :closable="false"
              show-icon
              class="delivery-tip"
            >
              <template #default>
                <p class="tip-text">
                  <el-icon><Timer /></el-icon>
                  {{ deliveryStore.deliveryMode === 'immediate' ? $t('orderConfirm.delivery.tipImmediate') : $t('orderConfirm.delivery.tipScheduled') }}
                </p>
              </template>
            </el-alert>
          </el-form>
        </el-card>

        <!-- 支付方式卡片 -->
        <el-card class="info-card payment-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <el-icon><Wallet /></el-icon>
              <span>{{ $t('orderConfirm.payment.title') }}</span>
            </div>
          </template>
          
          <div class="payment-methods">
            <div 
              class="payment-item"
              :class="{ active: form.paymentMethod === 'wechat' }"
              @click="form.paymentMethod = 'wechat'"
            >
              <div class="payment-icon wechat">💚</div>
              <div class="payment-info">
                <div class="payment-name">{{ $t('orderConfirm.payment.wechat') }}</div>
                <div class="payment-desc">{{ $t('orderConfirm.payment.wechatDesc') }}</div>
              </div>
              <el-icon v-if="form.paymentMethod === 'wechat'" class="check-icon"><Check /></el-icon>
            </div>
            
            <div 
              class="payment-item"
              :class="{ active: form.paymentMethod === 'alipay' }"
              @click="form.paymentMethod = 'alipay'"
            >
              <div class="payment-icon alipay">💙</div>
              <div class="payment-info">
                <div class="payment-name">{{ $t('orderConfirm.payment.alipay') }}</div>
                <div class="payment-desc">{{ $t('orderConfirm.payment.alipayDesc') }}</div>
              </div>
              <el-icon v-if="form.paymentMethod === 'alipay'" class="check-icon"><Check /></el-icon>
            </div>
          </div>
        </el-card>
      </div>

      <!-- 右侧：订单明细 -->
      <div class="right-section">
        <el-card class="summary-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <el-icon><ShoppingBag /></el-icon>
              <span>{{ $t('orderConfirm.summary.title') }}</span>
            </div>
          </template>

          <!-- 菜品列表 -->
          <div class="dish-list">
            <div v-for="item in cartStore.cartItems" :key="item.id" class="dish-item">
              <div class="dish-info">
                <span class="dish-name">{{ item.dishName }}</span>
                <span class="dish-count">× {{ item.quantity }}</span>
              </div>
              <span class="dish-price">¥{{ item.subtotal.toFixed(2) }}</span>
            </div>
          </div>

          <!-- 费用明细 -->
          <div class="fee-detail">
            <div class="fee-row">
              <span class="fee-label">{{ $t('orderConfirm.summary.dishTotal') }}</span>
              <span class="fee-value">¥{{ cartStore.totalAmount.toFixed(2) }}</span>
            </div>
            <div class="fee-row">
              <span class="fee-label">{{ $t('orderConfirm.summary.deliveryFee') }}</span>
              <span class="fee-value free">{{ $t('orderConfirm.summary.free') }}</span>
            </div>
            <div class="fee-row">
              <span class="fee-label">{{ $t('orderConfirm.summary.coupon') }}</span>
              <span class="fee-value discount">-¥0.00</span>
            </div>
          </div>

          <!-- 总计 -->
          <div class="total-section">
            <div class="total-row">
              <span class="total-label">{{ $t('orderConfirm.summary.payAmount') }}</span>
              <span class="total-price">¥{{ cartStore.totalAmount.toFixed(2) }}</span>
            </div>
          </div>

          <!-- 提交按钮 -->
          <el-button 
            type="warning" 
            size="large" 
            class="submit-btn"
            @click="handleSubmit"
            :disabled="cartStore.cartItems.length === 0"
          >
            {{ $t('orderConfirm.actions.submitOrder') }}
          </el-button>
        </el-card>
      </div>
    </div>

    <!-- 支付弹窗 -->
    <el-dialog 
      v-model="showPayment" 
      :title="paymentDialogTitle"
      width="500px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      class="payment-dialog"
    >
      <div class="payment-content">
        <!-- 支付信息 -->
        <div class="payment-header">
          <div class="payment-method-icon">{{ paymentIcon }}</div>
          <div class="payment-amount">
            <div class="amount-label">{{ $t('orderConfirm.paymentDialog.amountLabel') }}</div>
            <div class="amount-value">¥{{ cartStore.totalAmount.toFixed(2) }}</div>
          </div>
        </div>

        <!-- 二维码 -->
        <div class="qrcode-section">
          <div class="qrcode-wrapper">
            <img v-if="paymentInfo?.qrCode" :src="paymentInfo.qrCode" :alt="$t('orderConfirm.paymentDialog.qrAlt')" class="qrcode-img" />
            <div v-else class="qrcode-loading">
              <el-icon class="is-loading"><Loading /></el-icon>
              <p>{{ $t('orderConfirm.paymentDialog.generating') }}</p>
            </div>
          </div>
          <div class="qrcode-tip">
            <p class="tip-text">{{ t('orderConfirm.paymentDialog.scanTip', { method: paymentMethodName }) }}</p>
            <p class="countdown-text">
              <el-icon><Timer /></el-icon>
              {{ $t('orderConfirm.paymentDialog.remaining') }}<span class="countdown">{{ formattedCountdown }}</span>
            </p>
          </div>
        </div>

        <!-- 操作按钮 -->
        <div class="payment-actions">
          <el-button @click="cancelPayment" class="cancel-btn">
            <el-icon><Close /></el-icon>
            {{ $t('orderConfirm.paymentDialog.cancel') }}
          </el-button>
          <el-button type="success" @click="handleSimulatePayment" class="simulate-btn">
            <el-icon><Check /></el-icon>
            {{ $t('orderConfirm.paymentDialog.simulate') }}
          </el-button>
        </div>

        <!-- 提示信息 -->
        <div class="payment-notice">
          <p>{{ $t('orderConfirm.paymentDialog.noticeTitle') }}</p>
          <ul>
            <li>{{ t('orderConfirm.paymentDialog.notice1', { time: formattedCountdown }) }}</li>
            <li>{{ $t('orderConfirm.paymentDialog.notice2') }}</li>
            <li>{{ $t('orderConfirm.paymentDialog.notice3') }}</li>
          </ul>
        </div>
      </div>
    </el-dialog>

    <!-- 地址选择对话框 -->
    <el-dialog
      v-model="showAddressSelector"
      :title="$t('orderConfirm.addressSelector.title')"
      width="600px"
      class="address-selector-dialog"
    >
      <div v-if="addressList.length === 0" class="no-address-tip">
        <el-icon :size="60" color="#D0D0D0"><Location /></el-icon>
        <p>{{ $t('orderConfirm.addressSelector.empty') }}</p>
        <el-button type="primary" @click="goToAddressManage">
          {{ $t('orderConfirm.addressSelector.goAdd') }}
        </el-button>
      </div>
      <div v-else class="address-selector-list">
        <div 
          v-for="addr in addressList" 
          :key="addr.id"
          class="selector-address-card"
          :class="{ 'is-selected': selectedAddressId === addr.id }"
          @click="selectAddress(addr)"
        >
          <div class="selector-address-header">
            <div class="selector-address-name">
              <span class="name">{{ addr.receiverName }}</span>
              <span class="phone">{{ addr.receiverPhone }}</span>
            </div>
            <el-tag v-if="addr.isDefault" type="warning" size="small">
              <el-icon><Star /></el-icon>
              {{ $t('orderConfirm.addressSelector.default') }}
            </el-tag>
          </div>
          <div class="selector-address-detail">
            <el-icon><Location /></el-icon>
            <span>{{ addr.address }}</span>
          </div>
          <div v-if="selectedAddressId === addr.id" class="selected-icon">
            <el-icon color="#67C23A"><Check /></el-icon>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="showAddressSelector = false">{{ $t('orderConfirm.addressSelector.cancel') }}</el-button>
        <el-button type="primary" @click="goToAddressManage">
          <el-icon><Plus /></el-icon>
          {{ $t('orderConfirm.addressSelector.addNew') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.confirm-container {
  min-height: 100vh;
  background: linear-gradient(135deg, #FFF5E6 0%, #FFE8CC 100%);
  padding: 20px;
}

/* 顶部导航栏 */
.top-nav-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #FFF;
  padding: 12px 30px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  position: sticky;
  top: 0;
  z-index: 1000;
  border-bottom: 1px solid #F0F0F0;
  margin-bottom: 20px;
}

.nav-left {
  display: flex;
  align-items: center;
  gap: 30px;
  flex: 1;
}

.nav-logo {
  height: 40px;
  width: 40px;
  object-fit: cover;
  border-radius: 50%;
}

.nav-brand {
  font-size: 20px;
  font-weight: bold;
  color: #333;
  margin-right: 20px;
}

.nav-menu {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-left: 30px;
  border-left: 2px solid #F0F0F0;
}

.nav-link {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  cursor: pointer;
  color: #666;
  font-size: 14px;
  border-radius: 8px;
  transition: all 0.3s;
  white-space: nowrap;
}

.nav-link:hover {
  background: #FFF5E6;
  color: #FF6B35;
}

.nav-link.active {
  color: #FF6B35;
  background: #FFF5E6;
  font-weight: bold;
}

.nav-link .el-icon {
  font-size: 16px;
}

.nav-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-info-display {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 12px;
  background: #F9F9F9;
  border-radius: 10px;
  border: 1px solid #F0F0F0;
}

.user-avatar-small {
  background: linear-gradient(135deg, #FF9966 0%, #FF6B35 100%);
}

.user-details {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.user-name {
  font-size: 14px;
  font-weight: 600;
  color: #333;
  line-height: 1;
}

.user-role {
  font-size: 12px;
  color: #999;
  line-height: 1;
}

.user-menu-btn {
  border: 1px solid #E0E0E0;
  background: #FFF;
  transition: all 0.3s;
}

.user-menu-btn:hover {
  border-color: #FF9966;
  color: #FF6B35;
}

.login-btn {
  height: 36px;
  border-radius: 18px;
  padding: 0 24px;
  font-weight: 600;
}

/* 页面头部 */
.page-header {
  text-align: center;
  margin-bottom: 30px;
  animation: fadeInDown 0.6s ease-out;
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

.page-title {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-size: 28px;
  font-weight: bold;
  color: #FF6B35;
  margin: 0;
}

/* 内容区域 */
.content-wrapper {
  display: grid;
  grid-template-columns: 1fr 400px;
  gap: 20px;
  max-width: 1400px;
  margin: 0 auto;
}

/* 左侧区域 */
.left-section {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.info-card {
  border-radius: 20px;
  overflow: hidden;
  animation: slideInLeft 0.6s ease-out;
}

@keyframes slideInLeft {
  from {
    opacity: 0;
    transform: translateX(-30px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.info-card :deep(.el-card__header) {
  background: linear-gradient(135deg, #FF9966 0%, #FF6B35 100%);
  border-bottom: none;
  padding: 20px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #FFF;
  font-size: 18px;
  font-weight: bold;
}

.header-actions {
  display: flex;
  gap: 12px;
  margin-left: auto;
}

.select-addr-btn,
.manage-addr-btn {
  font-size: 14px;
}

.use-default-btn {
  margin-left: auto;
  color: #FFF !important;
  font-size: 14px;
}

.use-default-btn:hover {
  opacity: 0.8;
}

/* 地址选择对话框 */
.address-selector-dialog :deep(.el-dialog__body) {
  padding: 20px;
}

.no-address-tip {
  text-align: center;
  padding: 60px 20px;
}

.no-address-tip p {
  font-size: 16px;
  color: #999;
  margin: 20px 0;
}

.address-selector-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 400px;
  overflow-y: auto;
}

.selector-address-card {
  position: relative;
  padding: 16px;
  background: #F9F9F9;
  border: 2px solid #E0E0E0;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.3s;
}

.selector-address-card:hover {
  border-color: #FF9966;
  background: #FFF5E6;
}

.selector-address-card.is-selected {
  border-color: #67C23A;
  background: #F0F9FF;
}

.selector-address-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.selector-address-name {
  display: flex;
  align-items: center;
  gap: 12px;
}

.selector-address-name .name {
  font-size: 16px;
  font-weight: bold;
  color: #333;
}

.selector-address-name .phone {
  font-size: 14px;
  color: #666;
}

.selector-address-detail {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 14px;
  color: #666;
  line-height: 1.6;
}

.selector-address-detail .el-icon {
  color: #FF6B35;
  margin-top: 2px;
  flex-shrink: 0;
}

.selected-icon {
  position: absolute;
  bottom: 12px;
  right: 12px;
  font-size: 24px;
}

.order-form {
  padding: 10px 0;
}

.order-form :deep(.el-form-item__label) {
  font-weight: bold;
  color: #666;
}

.order-form :deep(.el-input__wrapper) {
  border-radius: 10px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  transition: all 0.3s;
}

.order-form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 2px 12px rgba(255, 107, 53, 0.2);
}

.order-form :deep(.el-textarea__inner) {
  border-radius: 10px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  transition: all 0.3s;
}

.order-form :deep(.el-textarea__inner:hover) {
  box-shadow: 0 2px 12px rgba(255, 107, 53, 0.2);
}

.save-default-text {
  display: flex;
  align-items: center;
  gap: 5px;
  color: #FF6B35;
  font-weight: 500;
}

/* 配送时间卡片 */
.delivery-time-card {
  margin-top: 20px;
}

.delivery-mode-group {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.delivery-mode-radio {
  width: 100%;
  height: auto;
  margin: 0;
  padding: 15px;
  border: 2px solid #FFE8CC;
  border-radius: 12px;
  transition: all 0.3s;
}

.delivery-mode-radio:hover {
  border-color: #FF9966;
  background: #FFF5E6;
}

.delivery-mode-radio.is-checked {
  border-color: #FF6B35;
  background: linear-gradient(135deg, #FFF5E6 0%, #FFE8CC 100%);
}

.delivery-mode-radio :deep(.el-radio__label) {
  width: 100%;
  padding-left: 10px;
}

.radio-content {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.radio-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.radio-desc {
  font-size: 13px;
  color: #999;
  padding-left: 24px;
}

.meal-period-group {
  display: flex;
  gap: 15px;
}

.meal-period-radio {
  padding: 10px 20px;
  border: 2px solid #E0E0E0;
  border-radius: 8px;
  transition: all 0.3s;
}

.meal-period-radio:hover {
  border-color: #FF9966;
}

.meal-period-radio.is-checked {
  border-color: #FF6B35;
  background: #FFF5E6;
  color: #FF6B35;
}

.delivery-time-select {
  width: 100%;
}

.delivery-tip {
  margin-top: 15px;
  border-radius: 10px;
}

.delivery-tip .tip-text {
  display: flex;
  align-items: center;
  gap: 5px;
  margin: 0;
  font-size: 14px;
}

/* 支付方式 */
.payment-methods {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.payment-item {
  display: flex;
  align-items: center;
  gap: 15px;
  padding: 20px;
  border: 2px solid #FFE8CC;
  border-radius: 15px;
  cursor: pointer;
  transition: all 0.3s;
  position: relative;
}

.payment-item:hover {
  border-color: #FF9966;
  background: #FFF5E6;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(255, 107, 53, 0.15);
}

.payment-item.active {
  border-color: #FF6B35;
  background: linear-gradient(135deg, #FFF5E6 0%, #FFE8CC 100%);
  box-shadow: 0 6px 20px rgba(255, 107, 53, 0.2);
}

.payment-icon {
  font-size: 48px;
  line-height: 1;
}

.payment-info {
  flex: 1;
}

.payment-name {
  font-size: 18px;
  font-weight: bold;
  color: #333;
  margin-bottom: 5px;
}

.payment-desc {
  font-size: 14px;
  color: #999;
}

.check-icon {
  font-size: 24px;
  color: #FF6B35;
}

/* 右侧区域 */
.right-section {
  animation: slideInRight 0.6s ease-out;
}

@keyframes slideInRight {
  from {
    opacity: 0;
    transform: translateX(30px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.summary-card {
  border-radius: 20px;
  overflow: hidden;
  position: sticky;
  top: 20px;
}

.summary-card :deep(.el-card__header) {
  background: linear-gradient(135deg, #FF9966 0%, #FF6B35 100%);
  border-bottom: none;
  padding: 20px;
}

/* 菜品列表 */
.dish-list {
  max-height: 300px;
  overflow-y: auto;
  margin-bottom: 20px;
}

.dish-list::-webkit-scrollbar {
  width: 6px;
}

.dish-list::-webkit-scrollbar-thumb {
  background: #FFE8CC;
  border-radius: 3px;
}

.dish-list::-webkit-scrollbar-thumb:hover {
  background: #FF9966;
}

.dish-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px dashed #FFE8CC;
}

.dish-item:last-child {
  border-bottom: none;
}

.dish-info {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
}

.dish-name {
  font-size: 15px;
  color: #333;
}

.dish-count {
  font-size: 14px;
  color: #999;
}

.dish-price {
  font-size: 16px;
  font-weight: bold;
  color: #FF6B35;
}

/* 费用明细 */
.fee-detail {
  padding: 15px 0;
  border-top: 2px solid #FFE8CC;
  border-bottom: 2px solid #FFE8CC;
  margin-bottom: 15px;
}

.fee-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.fee-row:last-child {
  margin-bottom: 0;
}

.fee-label {
  font-size: 14px;
  color: #666;
}

.fee-value {
  font-size: 15px;
  color: #333;
}

.fee-value.free {
  color: #67C23A;
}

.fee-value.discount {
  color: #FF6B35;
}

/* 总计 */
.total-section {
  padding: 15px 0;
}

.total-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.total-label {
  font-size: 16px;
  font-weight: bold;
  color: #333;
}

.total-price {
  font-size: 28px;
  font-weight: bold;
  color: #FF6B35;
}

/* 提交按钮 */
.submit-btn {
  width: 100%;
  height: 50px;
  font-size: 18px;
  font-weight: bold;
  border-radius: 25px;
  background: linear-gradient(135deg, #FF9966 0%, #FF6B35 100%);
  border: none;
  margin-top: 20px;
  transition: all 0.3s;
}

.submit-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(255, 107, 53, 0.4);
}

.submit-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 支付弹窗 */
.payment-dialog :deep(.el-dialog__header) {
  background: linear-gradient(135deg, #FF9966 0%, #FF6B35 100%);
  padding: 20px;
}

.payment-dialog :deep(.el-dialog__title) {
  color: #FFF;
  font-size: 20px;
  font-weight: bold;
}

.payment-dialog :deep(.el-dialog__headerbtn .el-dialog__close) {
  color: #FFF;
  font-size: 20px;
}

.payment-content {
  padding: 20px 0;
}

.payment-header {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 20px;
  background: linear-gradient(135deg, #FFF5E6 0%, #FFE8CC 100%);
  border-radius: 15px;
  margin-bottom: 30px;
}

.payment-method-icon {
  font-size: 60px;
}

.payment-amount {
  flex: 1;
}

.amount-label {
  font-size: 14px;
  color: #999;
  margin-bottom: 5px;
}

.amount-value {
  font-size: 32px;
  font-weight: bold;
  color: #FF6B35;
}

/* 二维码区域 */
.qrcode-section {
  text-align: center;
}

.qrcode-wrapper {
  width: 280px;
  height: 280px;
  margin: 0 auto 20px;
  padding: 20px;
  background: #FFF;
  border: 3px solid #FFE8CC;
  border-radius: 20px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
}

.qrcode-img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.qrcode-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #999;
}

.qrcode-loading .el-icon {
  font-size: 48px;
  margin-bottom: 10px;
}

.qrcode-tip {
  margin-bottom: 20px;
}

.tip-text {
  font-size: 16px;
  color: #666;
  margin: 0 0 10px 0;
}

.countdown-text {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  font-size: 14px;
  color: #999;
  margin: 0;
}

.countdown {
  font-size: 18px;
  font-weight: bold;
  color: #FF6B35;
}

/* 支付操作按钮 */
.payment-actions {
  display: flex;
  gap: 15px;
  margin-bottom: 20px;
}

.cancel-btn,
.simulate-btn {
  flex: 1;
  height: 45px;
  border-radius: 22.5px;
  font-size: 16px;
  font-weight: bold;
}

.cancel-btn {
  border: 2px solid #FFE8CC;
  color: #666;
}

.cancel-btn:hover {
  border-color: #FF9966;
  color: #FF9966;
}

.simulate-btn {
  background: linear-gradient(135deg, #67C23A 0%, #85CE61 100%);
  border: none;
  color: #FFF;
}

/* 支付提示 */
.payment-notice {
  padding: 20px;
  background: #FFF5E6;
  border-radius: 10px;
  border-left: 4px solid #FF9966;
}

.payment-notice p {
  font-size: 14px;
  font-weight: bold;
  color: #FF6B35;
  margin: 0 0 10px 0;
}

.payment-notice ul {
  margin: 0;
  padding-left: 20px;
}

.payment-notice li {
  font-size: 13px;
  color: #666;
  line-height: 1.8;
}

/* 响应式设计 */
@media (max-width: 1200px) {
  .content-wrapper {
    grid-template-columns: 1fr;
  }
  
  .right-section {
    order: -1;
  }
  
  .summary-card {
    position: static;
  }
}

@media (max-width: 768px) {
  .page-title {
    font-size: 22px;
  }
  
  .payment-dialog {
    width: 90% !important;
  }
  
  .qrcode-wrapper {
    width: 220px;
    height: 220px;
  }
}
</style>
