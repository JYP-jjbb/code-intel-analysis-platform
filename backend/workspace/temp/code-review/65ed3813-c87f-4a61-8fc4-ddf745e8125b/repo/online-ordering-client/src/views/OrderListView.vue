<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getOrderList } from '@/api/order'
import { ArrowLeft, Timer, ShoppingBag, Location, ForkSpoon } from '@element-plus/icons-vue'
import type { OrderSummary } from '@/types'
import { useI18n } from 'vue-i18n'

const router = useRouter()
const orderList = ref<OrderSummary[]>([])
const loading = ref(false)

const { t } = useI18n()

onMounted(() => {
  fetchOrders()
})

const fetchOrders = async () => {
  loading.value = true
  try {
    const res = await getOrderList({ pageNum: 1, pageSize: 20 })
    orderList.value = res.data.records || []
    console.log('订单列表:', orderList.value)
  } catch (error) {
    console.error(error)
    orderList.value = []
  } finally {
    loading.value = false
  }
}

const getStatusType = (status: number) => {
  // 0: 待支付, 1: 已支付, 2: 已完成, 3: 已取消
  switch (status) {
    case 0: return 'warning'
    case 1: return 'primary'
    case 2: return 'success'
    case 3: return 'info'
    default: return ''
  }
}

const getStatusColor = (status: number) => {
  switch (status) {
    case 0: return '#FF9966'  // 待支付 - 橙色
    case 1: return '#FF6B35'  // 已支付 - 深橙
    case 2: return '#67C23A'  // 已完成 - 绿色
    case 3: return '#909399'  // 已取消 - 灰色
    default: return '#909399'
  }
}

const goToDetail = (orderId: number) => {
  router.push(`/order/${orderId}`)
}
</script>

<template>
  <div class="page-container">
    <div class="content-wrapper">
      <!-- 页面头部 -->
      <div class="page-header">
        <el-button :icon="ArrowLeft" circle @click="router.push('/categories')" class="back-btn" />
        <h1 class="page-title">
          <el-icon><ShoppingBag /></el-icon>
          {{ $t('ordersPage.title') }}
        </h1>
      </div>

      <!-- 订单列表 -->
      <div v-loading="loading" class="order-list">
        <transition-group name="order-list" tag="div">
          <el-card 
            v-for="(order, index) in orderList" 
            :key="order.id" 
            class="order-card"
            shadow="hover"
            :style="{ animationDelay: `${index * 0.1}s` }"
            @click="goToDetail(order.id)"
          >
            <!-- 订单头部 -->
            <div class="order-header">
              <div class="order-info">
                <el-icon><Timer /></el-icon>
                <span class="order-time">{{ order.createTime }}</span>
                <span class="order-no">{{ t('ordersPage.orderNo', { no: order.orderNo }) }}</span>
              </div>
              <el-tag 
                :color="getStatusColor(order.status)"
                class="status-tag"
                effect="dark"
                size="large"
              >
                {{ order.statusDesc }}
              </el-tag>
            </div>

            <!-- 订单内容 -->
            <div class="order-content">
              <div class="order-items">
                <div class="item-summary">
                  <el-icon :size="40" color="#FFE8CC"><ForkSpoon /></el-icon>
                  <div class="summary-text">
                    <div class="item-count">{{ t('ordersPage.itemCount', { count: order.itemCount || 0 }) }}</div>
                    <div class="receiver-info">{{ order.receiverName }} - {{ order.receiverPhone }}</div>
                  </div>
                </div>
        </div>

              <div class="order-footer">
                <div class="address-info">
                  <el-icon><Location /></el-icon>
                  <span>{{ order.address }}</span>
          </div>
                <div class="amount-info">
                  <span class="amount-label">{{ $t('ordersPage.orderAmount') }}</span>
                  <span class="amount-value">¥{{ order.totalAmount }}</span>
          </div>
        </div>
        </div>
      </el-card>
        </transition-group>

        <!-- 空状态 -->
        <el-empty 
          v-if="!loading && orderList.length === 0" 
          :description="$t('ordersPage.empty')"
          class="empty-state"
        >
          <template #image>
            <el-icon :size="120" color="#FFE8CC">
              <ShoppingBag />
            </el-icon>
          </template>
          <el-button type="warning" @click="router.push('/categories')" class="back-btn">
            {{ $t('ordersPage.goOrder') }}
          </el-button>
        </el-empty>
      </div>
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

.order-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-height: 400px;
}

.order-card {
  border-radius: 20px;
  overflow: hidden;
  border: 2px solid #FFE8CC;
  cursor: pointer;
  transition: all 0.3s;
  animation: slideInLeft 0.6s ease-out both;
}

.order-card:hover {
  border-color: #FF9966;
  box-shadow: 0 8px 24px rgba(255, 107, 53, 0.2);
  transform: translateX(5px);
}

/* 订单头部 */
.order-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px 20px;
  background: linear-gradient(135deg, #FFFAF5 0%, #FFF 100%);
  border-bottom: 2px dashed #FFE8CC;
}

.order-info {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #666;
  font-size: 14px;
}

.order-info .el-icon {
  color: #FF9966;
}

.order-time {
  font-weight: bold;
  color: #333;
}

.order-no {
  color: #999;
}

.status-tag {
  font-weight: bold;
  border: none;
  padding: 8px 20px;
  border-radius: 20px;
  font-size: 14px;
}

/* 订单内容 */
.order-content {
  padding: 20px;
}

.order-items {
  margin-bottom: 15px;
}

.item-summary {
  display: flex;
  align-items: center;
  gap: 15px;
  padding: 15px;
  background: linear-gradient(135deg, #FFF5E6 0%, #FFF 100%);
  border-radius: 12px;
  border: 2px solid #FFE8CC;
}

.summary-text {
  flex: 1;
}

.item-count {
  font-size: 16px;
  font-weight: bold;
  color: #333;
  margin-bottom: 5px;
}

.receiver-info {
  font-size: 14px;
  color: #666;
}

/* 订单底部 */
.order-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 15px;
  padding-top: 15px;
  border-top: 1px solid #FFE8CC;
}

.address-info {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #666;
  font-size: 14px;
  flex: 1;
  margin-right: 20px;
}

.address-info .el-icon {
  color: #FF9966;
  flex-shrink: 0;
}

.amount-info {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.amount-label {
  font-size: 12px;
  color: #999;
  margin-bottom: 5px;
}

.amount-value {
  font-size: 24px;
  font-weight: bold;
  color: #FF6B35;
}

/* 空状态 */
.empty-state {
  padding: 80px 20px;
  animation: fadeIn 0.6s ease-out;
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

/* 列表过渡动画 */
.order-list-enter-active {
  animation: slideInLeft 0.6s ease-out;
}

.order-list-leave-active {
  animation: fadeOut 0.3s ease-out;
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

@keyframes fadeOut {
  to {
    opacity: 0;
    transform: scale(0.9);
  }
}

/* 响应式设计 */
@media (max-width: 768px) {
  .order-header {
    flex-direction: column;
    align-items: flex-start;
  gap: 10px;
  }

  .order-info {
    flex-wrap: wrap;
  }

  .order-no {
    width: 100%;
    margin-left: 32px;
  }

  .order-footer {
    flex-direction: column;
    align-items: flex-start;
    gap: 15px;
  }

  .amount-info {
    align-items: flex-start;
    width: 100%;
  }
}
</style>
