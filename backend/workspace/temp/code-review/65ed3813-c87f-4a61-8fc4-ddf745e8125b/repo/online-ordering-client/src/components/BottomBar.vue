<script setup lang="ts">
import { computed } from 'vue'
import { useCartStore } from '@/store/cart'
import { useUserStore } from '@/store/user'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { 
  ShoppingCart,
  ArrowRight
} from '@element-plus/icons-vue'

interface Props {
  showDrawer: boolean
}

interface Emits {
  (e: 'update:showDrawer', value: boolean): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const cartStore = useCartStore()
const userStore = useUserStore()
const router = useRouter()

const { t } = useI18n()

// 配送费
const deliveryFee = computed(() => {
  return totalPrice.value > 0 ? 6 : 0
})

// 计算总价（餐品小计）
const subtotal = computed(() => {
  return cartStore.items.reduce((sum, item) => sum + item.subtotal, 0)
})

// 计算总价（含配送费）
const totalPrice = computed(() => {
  return subtotal.value + deliveryFee.value
})

// 计算总数量
const totalCount = computed(() => {
  return cartStore.items.reduce((sum, item) => sum + item.quantity, 0)
})

// 打开购物车
const handleOpenCart = () => {
  if (!userStore.token) {
    ElMessage.warning(t('login.messages.loginRequired'))
    router.push('/login')
    return
  }
  emit('update:showDrawer', true)
}

// 去结算
const handleCheckout = () => {
  if (!userStore.token) {
    ElMessage.warning(t('login.messages.loginRequired'))
    router.push('/login')
    return
  }
  if (cartStore.items.length === 0) {
    ElMessage.warning(t('cart.emptyTip'))
    return
  }
  router.push('/order/confirm')
}
</script>

<template>
  <div class="bottom-bar">
    <div class="bottom-bar-content">
      <!-- 左侧购物车和信息 -->
      <div class="left-section">
        <div class="cart-section" @click="handleOpenCart">
          <div class="cart-icon-wrapper">
            <el-badge 
              :value="totalCount" 
              :max="99"
              :hidden="totalCount === 0"
              class="cart-badge"
            >
              <div class="cart-icon">
                <el-icon :size="24"><ShoppingCart /></el-icon>
              </div>
            </el-badge>
          </div>
        </div>
        <div class="price-info">
          <div class="price-item">
            <span class="price-label">{{ $t('cart.subtotal') }}:</span>
            <span class="price-value">¥{{ subtotal.toFixed(2) }}</span>
          </div>
          <div class="price-item">
            <span class="price-label">{{ $t('cart.deliveryFee') }}:</span>
            <span class="price-value">¥{{ deliveryFee.toFixed(2) }}</span>
          </div>
          <div class="price-item total-item">
            <span class="price-label">{{ $t('cart.total') }}:</span>
            <span class="price-value total-price">¥{{ totalPrice.toFixed(2) }}</span>
          </div>
        </div>
      </div>

      <!-- 右侧结算按钮 -->
      <el-button 
        type="warning" 
        size="large"
        :icon="ArrowRight"
        @click="handleCheckout"
        :disabled="!userStore.token || cartStore.items.length === 0"
        class="checkout-button"
      >
        {{ $t('cart.checkout') }}
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: #FFF;
  box-shadow: 0 -4px 16px rgba(0, 0, 0, 0.1);
  z-index: 999;
  animation: slideUp 0.3s ease-out;
}

@keyframes slideUp {
  from {
    transform: translateY(100%);
  }
  to {
    transform: translateY(0);
  }
}

.bottom-bar-content {
  max-width: 1400px;
  margin: 0 auto;
  padding: 12px 30px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
}

.left-section {
  display: flex;
  align-items: center;
  gap: 16px;
}

.cart-section {
  cursor: pointer;
  transition: all 0.3s;
  padding: 4px;
  border-radius: 12px;
}

.cart-section:hover {
  background: #FFF5E6;
}

.cart-icon-wrapper {
  position: relative;
}

.cart-icon {
  width: 50px;
  height: 50px;
  background: linear-gradient(135deg, #FF9966 0%, #FF6B35 100%);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #FFF;
  box-shadow: 0 4px 12px rgba(255, 107, 53, 0.3);
  transition: all 0.3s;
}

.cart-section:hover .cart-icon {
  transform: scale(1.1);
  box-shadow: 0 6px 20px rgba(255, 107, 53, 0.4);
}

.cart-badge :deep(.el-badge__content) {
  background: #F56C6C;
  border: 2px solid #FFF;
  font-weight: bold;
  font-size: 12px;
}

.price-info {
  display: flex;
  align-items: center;
  gap: 20px;
  min-width: 300px;
}

.price-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.price-label {
  color: #666;
  white-space: nowrap;
}

.price-value {
  color: #333;
  font-weight: 500;
  white-space: nowrap;
}

.total-item {
  margin-left: 8px;
  padding-left: 12px;
  border-left: 1px solid #E0E0E0;
}

.total-price {
  color: #FF6B35;
  font-size: 16px;
  font-weight: bold;
}

.checkout-button {
  height: 50px;
  padding: 0 40px;
  font-size: 16px;
  font-weight: bold;
  border-radius: 25px;
  background: linear-gradient(135deg, #FF9966 0%, #FF6B35 100%);
  border: none;
  box-shadow: 0 4px 12px rgba(255, 107, 53, 0.3);
  transition: all 0.3s;
}

.checkout-button:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(255, 107, 53, 0.4);
}

.checkout-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

</style>


