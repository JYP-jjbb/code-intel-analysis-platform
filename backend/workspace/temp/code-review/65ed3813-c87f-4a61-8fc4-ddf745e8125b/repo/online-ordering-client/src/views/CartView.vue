<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useCartStore } from '@/store/cart'
import { Delete, ShoppingCartFull, ArrowLeft } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useDataTranslation } from '@/composables/useDataTranslation'

const router = useRouter()
const cartStore = useCartStore()
const { t } = useI18n()
const { translateDishByLocale } = useDataTranslation()

onMounted(() => {
  cartStore.fetchCart()
})

const goCheckout = () => {
  if (cartStore.cartItems.length === 0) return
  router.push('/order/confirm')
}

const handleClearCart = () => {
  ElMessageBox.confirm(t('cart.clearConfirm'), t('common.tip'), {
    confirmButtonText: t('common.confirm'),
    cancelButtonText: t('common.cancel'),
    type: 'warning',
  }).then(() => {
    cartStore.clear()
  }).catch(() => {})
}
</script>

<template>
  <div class="page-container">
    <div class="content-wrapper">
      <!-- 页面头部 -->
      <div class="page-header">
        <el-button :icon="ArrowLeft" circle @click="router.back()" class="back-btn" />
        <h1 class="page-title">
          <el-icon><ShoppingCartFull /></el-icon>
          我的购物车
        </h1>
      </div>

      <!-- 购物车内容 -->
      <el-card class="cart-card" v-if="cartStore.cartItems.length > 0">
        <div class="cart-list">
          <div 
            v-for="(item, index) in cartStore.cartItems" 
            :key="item.id" 
            class="cart-item"
            :style="{ animationDelay: `${index * 0.1}s` }"
          >
            <div class="item-image">
              <img v-if="item.imageUrl" :src="item.imageUrl" />
              <div v-else class="no-image">
                <el-icon :size="40"><Picture /></el-icon>
              </div>
            </div>

            <div class="item-info">
              <div class="item-name">{{ translateDishByLocale({ name: item.dishName }).name }}</div>
              <div class="item-price">¥{{ item.price }}</div>
            </div>

            <div class="item-actions">
              <el-input-number 
                v-model="item.quantity" 
                :min="1" 
                :max="item.stock || 999" 
                size="default"
                @change="(val: number) => cartStore.updateQuantity(item.id, val)"
                class="quantity-input"
              />
            </div>

            <div class="item-subtotal">
              <span class="subtotal-label">小计</span>
              <span class="subtotal-price">¥{{ item.subtotal }}</span>
            </div>

            <div class="item-delete">
              <el-button 
                :icon="Delete" 
                circle 
                @click="cartStore.removeItem(item.id)"
                class="delete-btn"
              />
            </div>
          </div>
        </div>

        <!-- 购物车底部 -->
        <div class="cart-footer">
          <div class="footer-left">
            <el-button @click="handleClearCart" class="clear-btn">
              <el-icon><Delete /></el-icon>
              清空购物车
            </el-button>
          </div>
          <div class="footer-right">
            <div class="total-info">
              <span class="total-count">已选 {{ cartStore.totalCount }} 件</span>
              <span class="total-text">合计：</span>
              <span class="total-price">¥{{ cartStore.totalAmount }}</span>
            </div>
            <el-button 
              type="warning" 
              size="large" 
              @click="goCheckout"
              class="checkout-btn"
            >
              去结算
            </el-button>
          </div>
        </div>
      </el-card>

      <!-- 空购物车 -->
      <el-card class="empty-cart" v-else>
        <el-empty description="购物车是空的">
          <template #image>
            <el-icon :size="120" color="#FFE8CC">
              <ShoppingCartFull />
            </el-icon>
          </template>
          <el-button type="warning" @click="router.push('/categories')" class="go-home-btn">
            去逛逛
          </el-button>
        </el-empty>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  gap: 15px;
  margin-bottom: 25px;
  animation: slideInDown 0.6s ease-out;
}

.back-btn {
  background: #FFF;
  border: 2px solid #FFE8CC;
  color: #FF9966;
  transition: all 0.3s;
}

.back-btn:hover {
  background: #FF9966;
  border-color: #FF9966;
  color: #FFF;
  transform: translateX(-3px);
}

.page-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0;
}

.cart-card {
  border-radius: 20px;
  overflow: hidden;
  box-shadow: 0 8px 24px rgba(255, 107, 53, 0.15);
  animation: fadeIn 0.6s ease-out;
}

.cart-list {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.cart-item {
  display: flex;
  align-items: center;
  padding: 20px;
  background: linear-gradient(135deg, #FFFAF5 0%, #FFF 100%);
  border-radius: 15px;
  border: 2px solid #FFE8CC;
  transition: all 0.3s;
  animation: slideInLeft 0.6s ease-out both;
}

.cart-item:hover {
  border-color: #FF9966;
  box-shadow: 0 4px 16px rgba(255, 107, 53, 0.2);
  transform: translateX(5px);
}

.item-image {
  width: 100px;
  height: 100px;
  border-radius: 12px;
  overflow: hidden;
  flex-shrink: 0;
  border: 3px solid #FFE8CC;
}

.item-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.no-image {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #FFE8CC 0%, #FFD9B3 100%);
  color: #FF9966;
}

.item-info {
  flex: 1;
  padding: 0 20px;
}

.item-name {
  font-size: 18px;
  font-weight: bold;
  color: #333;
  margin-bottom: 8px;
}

.item-price {
  font-size: 14px;
  color: #FF6B35;
  font-weight: bold;
}

.item-actions {
  padding: 0 15px;
}

.quantity-input :deep(.el-input-number__decrease),
.quantity-input :deep(.el-input-number__increase) {
  background: #FF9966;
  border-color: #FF9966;
  color: #FFF;
}

.quantity-input :deep(.el-input__inner) {
  font-weight: bold;
  color: #FF6B35;
}

.item-subtotal {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0 20px;
}

.subtotal-label {
  font-size: 12px;
  color: #999;
  margin-bottom: 5px;
}

.subtotal-price {
  font-size: 20px;
  font-weight: bold;
  color: #FF6B35;
}

.item-delete {
  padding: 0 10px;
}

.delete-btn {
  background: #FFE8CC;
  border-color: #FFE8CC;
  color: #FF6B35;
  transition: all 0.3s;
}

.delete-btn:hover {
  background: #FF6B35;
  border-color: #FF6B35;
  color: #FFF;
  transform: rotate(90deg);
}

/* 购物车底部 */
.cart-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 30px;
  padding-top: 20px;
  border-top: 2px dashed #FFE8CC;
}

.footer-left .clear-btn {
  color: #999;
  border-color: #FFE8CC;
  transition: all 0.3s;
}

.footer-left .clear-btn:hover {
  color: #FF6B35;
  border-color: #FF9966;
}

.footer-right {
  display: flex;
  align-items: center;
  gap: 30px;
}

.total-info {
  display: flex;
  align-items: center;
  gap: 15px;
  font-size: 16px;
}

.total-count {
  color: #666;
}

.total-text {
  color: #666;
  font-weight: bold;
}

.total-price {
  font-size: 28px;
  font-weight: bold;
  color: #FF6B35;
}

.checkout-btn {
  background: linear-gradient(135deg, #FF9966 0%, #FF6B35 100%);
  border: none;
  font-size: 18px;
  font-weight: bold;
  border-radius: 25px;
  padding: 15px 40px;
  transition: all 0.3s;
}

.checkout-btn:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 24px rgba(255, 107, 53, 0.4);
}

/* 空购物车 */
.empty-cart {
  border-radius: 20px;
  padding: 60px 20px;
  text-align: center;
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

.go-home-btn {
  background: linear-gradient(135deg, #FF9966 0%, #FF6B35 100%);
  border: none;
  color: #FFF;
  font-weight: bold;
  border-radius: 25px;
  padding: 12px 35px;
  margin-top: 20px;
  transition: all 0.3s;
}

.go-home-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(255, 107, 53, 0.4);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .cart-item {
    flex-direction: column;
    gap: 15px;
  }

  .item-info {
    padding: 0;
    text-align: center;
  }

  .item-actions,
  .item-subtotal {
    padding: 0;
  }

  .cart-footer {
    flex-direction: column;
    gap: 20px;
  }

  .footer-right {
    flex-direction: column;
    gap: 15px;
    width: 100%;
  }

  .checkout-btn {
    width: 100%;
  }
}
</style>
