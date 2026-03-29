<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useCartStore } from '@/store/cart'
import { useUserStore } from '@/store/user'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { 
  ShoppingCart, 
  Delete, 
  Close,
  ArrowRight
} from '@element-plus/icons-vue'

const router = useRouter()
const cartStore = useCartStore()
const userStore = useUserStore()

const { t } = useI18n()

// 控制购物车抽屉显示
const showDrawer = defineModel<boolean>('visible', { default: false })

// 计算总价
const totalPrice = computed(() => {
  return cartStore.items.reduce((sum, item) => sum + item.subtotal, 0)
})

// 计算总数量
const totalCount = computed(() => {
  return cartStore.items.reduce((sum, item) => sum + item.quantity, 0)
})

// 增加数量
const handleIncrease = async (itemId: number) => {
  await cartStore.updateQuantity(itemId, 1)
}

// 减少数量
const handleDecrease = async (itemId: number) => {
  await cartStore.updateQuantity(itemId, -1)
}

// 删除商品
const handleDelete = async (itemId: number) => {
  await cartStore.removeItem(itemId)
}

// 清空购物车
const handleClear = async () => {
  await cartStore.clearCart()
}

// 去结算
const handleCheckout = () => {
  if (cartStore.items.length === 0) {
    ElMessage.warning(t('cart.emptyTip'))
    return
  }
  showDrawer.value = false
  router.push('/order/confirm')
}
</script>

<template>
  <el-drawer
    v-model="showDrawer"
    :title="$t('cart.title')"
    direction="ltr"
    size="400px"
    class="cart-drawer"
  >
    <template #header>
      <div class="drawer-header">
        <div class="header-left">
          <el-icon :size="24" color="#FF6B35"><ShoppingCart /></el-icon>
          <span class="header-title">{{ $t('cart.title') }}</span>
        </div>
        <el-button 
          v-if="cartStore.items.length > 0"
          type="danger" 
          link 
          :icon="Delete"
          @click="handleClear"
        >
          {{ $t('cart.clear') }}
        </el-button>
      </div>
    </template>

    <div class="cart-content">
      <!-- 购物车列表 -->
      <div v-if="cartStore.items.length > 0" class="cart-list">
        <div 
          v-for="item in cartStore.items" 
          :key="item.id"
          class="cart-item"
        >
          <el-image 
            :src="item.imageUrl" 
            fit="cover"
            class="item-image"
          >
            <template #error>
              <div class="image-error">
                <el-icon><ShoppingCart /></el-icon>
              </div>
            </template>
          </el-image>

          <div class="item-info">
            <div class="item-name">{{ item.dishName }}</div>
            <div class="item-price">¥{{ item.price }}</div>
          </div>

          <div class="item-actions">
            <div class="quantity-control">
              <el-button 
                circle 
                size="small"
                @click="handleDecrease(item.id)"
                :disabled="item.quantity <= 1"
              >
                -
              </el-button>
              <span class="quantity">{{ item.quantity }}</span>
              <el-button 
                circle 
                size="small"
                @click="handleIncrease(item.id)"
                :disabled="item.quantity >= item.stock"
              >
                +
              </el-button>
            </div>
            <div class="item-subtotal">¥{{ item.subtotal.toFixed(2) }}</div>
            <el-button 
              type="danger" 
              :icon="Close" 
              circle 
              size="small"
              @click="handleDelete(item.id)"
              class="delete-btn"
            />
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <el-empty 
        v-else 
        :description="$t('cart.empty')"
        :image-size="120"
      >
        <template #image>
          <el-icon :size="80" color="#FFE8CC">
            <ShoppingCart />
          </el-icon>
        </template>
        <el-button type="warning" @click="showDrawer = false">
          {{ $t('cart.goShop') }}
        </el-button>
      </el-empty>
    </div>

    <!-- 底部结算 -->
    <template #footer>
      <div class="cart-footer">
        <div class="footer-info">
          <div class="total-label">{{ $t('cart.total') }}</div>
          <div class="total-price">¥{{ totalPrice.toFixed(2) }}</div>
        </div>
        <el-button 
          type="warning" 
          size="large"
          :icon="ArrowRight"
          @click="handleCheckout"
          :disabled="cartStore.items.length === 0"
          class="checkout-btn"
        >
          {{ t('cart.checkoutCount', { count: totalCount }) }}
        </el-button>
      </div>
    </template>
  </el-drawer>
</template>

<style scoped>
.cart-drawer :deep(.el-drawer__header) {
  margin-bottom: 0;
  padding: 20px;
  background: linear-gradient(135deg, #FF9966 0%, #FF6B35 100%);
}

.cart-drawer :deep(.el-drawer__title) {
  color: #FFF;
}

.cart-drawer :deep(.el-drawer__close-btn) {
  color: #FFF;
  font-size: 24px;
}

.drawer-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-title {
  font-size: 20px;
  font-weight: bold;
  color: #FFF;
}

.cart-content {
  height: calc(100vh - 200px);
  overflow-y: auto;
}

.cart-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 16px;
}

.cart-item {
  display: flex;
  gap: 12px;
  padding: 16px;
  background: #FFF5E6;
  border-radius: 12px;
  transition: all 0.3s;
}

.cart-item:hover {
  box-shadow: 0 4px 12px rgba(255, 107, 53, 0.2);
  transform: translateX(4px);
}

.item-image {
  width: 80px;
  height: 80px;
  border-radius: 8px;
  flex-shrink: 0;
}

.image-error {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  background: #f5f7fa;
  color: #909399;
}

.item-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
}

.item-name {
  font-size: 16px;
  font-weight: bold;
  color: #333;
}

.item-price {
  font-size: 14px;
  color: #FF6B35;
  font-weight: bold;
}

.item-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: space-between;
}

.quantity-control {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #FFF;
  padding: 4px;
  border-radius: 20px;
}

.quantity {
  min-width: 30px;
  text-align: center;
  font-weight: bold;
  color: #333;
}

.item-subtotal {
  font-size: 18px;
  font-weight: bold;
  color: #FF6B35;
}

.delete-btn {
  margin-top: 4px;
}

.cart-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
  background: #FFF;
  border-top: 2px solid #FFE8CC;
}

.footer-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.total-label {
  font-size: 14px;
  color: #999;
}

.total-price {
  font-size: 28px;
  font-weight: bold;
  color: #FF6B35;
}

.checkout-btn {
  height: 50px;
  padding: 0 32px;
  font-size: 16px;
  font-weight: bold;
  border-radius: 25px;
  background: linear-gradient(135deg, #FF9966 0%, #FF6B35 100%);
  border: none;
}

.checkout-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(255, 107, 53, 0.4);
}

/* 滚动条样式 */
.cart-content::-webkit-scrollbar {
  width: 6px;
}

.cart-content::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 3px;
}

.cart-content::-webkit-scrollbar-thumb {
  background: #FF9966;
  border-radius: 3px;
}

.cart-content::-webkit-scrollbar-thumb:hover {
  background: #FF6B35;
}
</style>


