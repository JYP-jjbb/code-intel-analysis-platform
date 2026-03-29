<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getOrderDetail, payOrder, cancelOrder } from '@/api/order'
import { createPayment, checkPaymentStatus, simulatePayment } from '@/api/payment'
import { ElMessage, ElMessageBox } from 'element-plus'
import { 
  ArrowLeft, 
  ShoppingBag, 
  Location, 
  User, 
  Phone, 
  Timer,
  Check,
  Close,
  Loading,
  Wallet,
  Picture,
  Edit,
  Clock
} from '@element-plus/icons-vue'
import type { OrderDetail } from '@/types'
import type { PaymentInfo } from '@/api/payment'

const route = useRoute()
const router = useRouter()
const order = ref<OrderDetail | null>(null)
const loading = ref(false)

// 支付相关
const showPayment = ref(false)
const paymentInfo = ref<PaymentInfo | null>(null)
const paymentTimer = ref<number | null>(null)
const countdown = ref(300)

onMounted(() => {
  fetchDetail()
})

const fetchDetail = async () => {
  const id = Number(route.params.id)
  if (!id) return
  loading.value = true
  try {
    const res = await getOrderDetail(id)
    order.value = res.data
  } catch (error) {
    console.error(error)
  } finally {
    loading.value = false
  }
}

const formatDeliveryTime = (timeStr: string) => {
  if (!timeStr) return ''
  try {
    const date = new Date(timeStr)
    const hours = date.getHours().toString().padStart(2, '0')
    const minutes = date.getMinutes().toString().padStart(2, '0')
    return `${hours}:${minutes}`
  } catch (e) {
    return timeStr
  }
}

const formattedCountdown = () => {
  const minutes = Math.floor(countdown.value / 60)
  const seconds = countdown.value % 60
  return `${minutes}:${seconds.toString().padStart(2, '0')}`
}

const handlePay = async () => {
  if (!order.value) return
  
  try {
    // 创建支付订单（生成二维码）
    const paymentRes = await createPayment({
      orderId: order.value.id,
      paymentType: order.value.paymentMethod || 'wechat'
    })
    
    paymentInfo.value = paymentRes.data
    showPayment.value = true
    
    // 开始检查支付状态
    startPaymentCheck()
    
  } catch (error: any) {
    console.error(error)
    ElMessage.error(error.message || '创建支付失败')
  }
}

const startPaymentCheck = () => {
  countdown.value = 300
  paymentTimer.value = window.setInterval(() => {
    countdown.value--
    
    if (countdown.value <= 0) {
      handlePaymentTimeout()
      return
    }
    
    if (countdown.value % 3 === 0) {
      checkPayment()
    }
  }, 1000)
}

const checkPayment = async () => {
  if (!order.value) return
  
  try {
    const res = await checkPaymentStatus(order.value.id)
    if (res.data.isPaid) {
      handlePaymentSuccess()
    }
  } catch (error) {
    console.error('检查支付状态失败:', error)
  }
}

const handlePaymentSuccess = () => {
  if (paymentTimer.value) {
    clearInterval(paymentTimer.value)
  }
  
  showPayment.value = false
  
  ElMessageBox.alert('支付成功！', '提示', {
    confirmButtonText: '查看订单',
    type: 'success',
    callback: () => {
      fetchDetail()
    }
  })
}

const handlePaymentTimeout = () => {
  if (paymentTimer.value) {
    clearInterval(paymentTimer.value)
  }
  
  ElMessageBox.confirm('支付已超时，是否继续支付？', '提示', {
    confirmButtonText: '继续支付',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    handlePay()
  }).catch(() => {
    showPayment.value = false
  })
}

const cancelPayment = () => {
  if (paymentTimer.value) {
    clearInterval(paymentTimer.value)
  }
  showPayment.value = false
  countdown.value = 300
}

const handleSimulatePayment = async () => {
  if (!order.value) return
  
  try {
    await simulatePayment(order.value.id)
    ElMessage.success('模拟支付成功')
    await checkPayment()
  } catch (error) {
    ElMessage.error('模拟支付失败')
  }
}

const handleCancel = async () => {
  if (!order.value) return
  try {
    await ElMessageBox.confirm('确定要取消订单吗？', '提示', { type: 'warning' })
    await cancelOrder(order.value.id)
    ElMessage.success('订单已取消')
    fetchDetail()
  } catch (error) {
    // cancel
  }
}

const getStatusColor = (status: number) => {
  switch (status) {
    case 0: return '#FF9966'
    case 1: return '#FF6B35'
    case 2: return '#67C23A'
    case 3: return '#909399'
    default: return '#909399'
  }
}

const paymentMethodName = () => {
  return order.value?.paymentMethod === 'wechat' ? '微信支付' : '支付宝'
}

const paymentIcon = () => {
  return order.value?.paymentMethod === 'wechat' ? '💚' : '💙'
}

// 计算商品总价（根据订单项）
const calculateItemsTotal = () => {
  if (!order.value || !order.value.items || order.value.items.length === 0) {
    return 0
  }
  return order.value.items.reduce((sum, item) => sum + (item.subtotal || 0), 0)
}
</script>

<template>
  <div class="detail-container">
    <!-- 页面头部 -->
    <div class="page-header">
      <el-button :icon="ArrowLeft" circle @click="router.push('/orders')" class="back-btn" />
      <h1 class="page-title">
        <el-icon><ShoppingBag /></el-icon>
        订单详情
      </h1>
    </div>

    <div v-if="order" v-loading="loading" class="content-wrapper">
      <!-- 订单状态卡片 -->
      <el-card class="status-card" shadow="hover">
        <div class="status-content">
          <div class="status-icon" :style="{ background: getStatusColor(order.status) }">
            <el-icon :size="40"><ShoppingBag /></el-icon>
          </div>
          <div class="status-info">
            <div class="status-text" :style="{ color: getStatusColor(order.status) }">
              {{ order.statusDesc }}
            </div>
            <div class="order-no">订单号：{{ order.orderNo }}</div>
          </div>
        </div>
        
        <div class="actions" v-if="order.status === 0">
          <el-button size="large" @click="handleCancel" class="cancel-order-btn">
            <el-icon><Close /></el-icon>
            取消订单
          </el-button>
          <el-button type="warning" size="large" @click="handlePay" class="pay-btn">
            <el-icon><Wallet /></el-icon>
            立即支付
          </el-button>
        </div>
      </el-card>

      <!-- 配送信息卡片 -->
      <el-card class="info-card" shadow="hover">
        <template #header>
          <div class="card-header">
            <el-icon><Location /></el-icon>
            <span>配送信息</span>
          </div>
        </template>
        
        <div class="delivery-info">
          <div class="info-row">
            <el-icon class="info-icon"><User /></el-icon>
            <span class="info-label">收货人：</span>
            <span class="info-value">{{ order.receiverName }}</span>
          </div>
          <div class="info-row">
            <el-icon class="info-icon"><Phone /></el-icon>
            <span class="info-label">手机号：</span>
            <span class="info-value">{{ order.receiverPhone }}</span>
          </div>
          <div class="info-row">
            <el-icon class="info-icon"><Location /></el-icon>
            <span class="info-label">收货地址：</span>
            <span class="info-value">{{ order.address }}</span>
          </div>
          <div class="info-row" v-if="order.deliveryTime">
            <el-icon class="info-icon"><Clock /></el-icon>
            <span class="info-label">预约配送：</span>
            <span class="info-value highlight">{{ formatDeliveryTime(order.deliveryTime) }}</span>
          </div>
          <div class="info-row" v-if="order.estimatedDeliveryTime">
            <el-icon class="info-icon"><Timer /></el-icon>
            <span class="info-label">预计送达：</span>
            <span class="info-value highlight">{{ formatDeliveryTime(order.estimatedDeliveryTime) }}</span>
          </div>
          <div class="info-row" v-if="order.remark">
            <el-icon class="info-icon"><Edit /></el-icon>
            <span class="info-label">备注：</span>
            <span class="info-value">{{ order.remark }}</span>
          </div>
        </div>
      </el-card>

      <!-- 商品明细卡片 -->
      <el-card class="items-card" shadow="hover">
        <template #header>
          <div class="card-header">
            <el-icon><ShoppingBag /></el-icon>
            <span>商品明细</span>
          </div>
        </template>

        <div class="items-list">
          <div v-if="order.items && order.items.length > 0">
            <div v-for="(item, index) in order.items" :key="index" class="item-row">
              <div class="item-image">
                <img v-if="item.imageUrl" :src="item.imageUrl" />
                <div v-else class="no-image">
                  <el-icon :size="30"><Picture /></el-icon>
                </div>
              </div>
              <div class="item-info">
                <div class="item-name">{{ item.dishName }}</div>
                <div class="item-price">¥{{ item.price }}</div>
              </div>
              <div class="item-quantity">× {{ item.quantity }}</div>
              <div class="item-subtotal">¥{{ item.subtotal.toFixed(2) }}</div>
            </div>
          </div>
          <el-empty v-else description="暂无商品信息" :image-size="80" />
        </div>

        <!-- 费用汇总 -->
        <div class="summary">
          <div class="summary-row">
            <span class="summary-label">商品总价</span>
            <span class="summary-value">¥{{ calculateItemsTotal().toFixed(2) }}</span>
          </div>
          <div class="summary-row">
            <span class="summary-label">配送费</span>
            <span class="summary-value free">免费</span>
          </div>
          <div class="total-row">
            <span class="total-label">实付金额</span>
            <span class="total-price">¥{{ order.totalAmount.toFixed(2) }}</span>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 支付弹窗 -->
    <el-dialog 
      v-model="showPayment" 
      :title="`${paymentMethodName()}支付`"
      width="500px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      class="payment-dialog"
    >
      <div class="payment-content">
        <div class="payment-header">
          <div class="payment-method-icon">{{ paymentIcon() }}</div>
          <div class="payment-amount">
            <div class="amount-label">支付金额</div>
            <div class="amount-value">¥{{ order?.totalAmount.toFixed(2) }}</div>
          </div>
        </div>

        <div class="qrcode-section">
          <div class="qrcode-wrapper">
            <img v-if="paymentInfo?.qrCode" :src="paymentInfo.qrCode" alt="支付二维码" class="qrcode-img" />
            <div v-else class="qrcode-loading">
              <el-icon class="is-loading" :size="48"><Loading /></el-icon>
              <p>正在生成二维码...</p>
            </div>
          </div>
          <div class="qrcode-tip">
            <p class="tip-text">请使用{{ paymentMethodName() }}扫描二维码完成支付</p>
            <p class="countdown-text">
              <el-icon><Timer /></el-icon>
              剩余时间：<span class="countdown">{{ formattedCountdown() }}</span>
            </p>
          </div>
        </div>

        <div class="payment-actions">
          <el-button @click="cancelPayment" class="cancel-btn">
            <el-icon><Close /></el-icon>
            取消支付
          </el-button>
          <el-button type="success" @click="handleSimulatePayment" class="simulate-btn">
            <el-icon><Check /></el-icon>
            模拟支付成功（测试）
          </el-button>
        </div>

        <div class="payment-notice">
          <p>💡 温馨提示：</p>
          <ul>
            <li>请在 {{ formattedCountdown() }} 内完成支付</li>
            <li>支付成功后会自动更新订单状态</li>
            <li>如遇到问题请联系客服：400-123-4567</li>
          </ul>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.detail-container {
  min-height: 100vh;
  background: linear-gradient(135deg, #FFF5E6 0%, #FFE8CC 100%);
  padding: 20px;
}

/* 页面头部 */
.page-header {
  display: flex;
  align-items: center;
  gap: 20px;
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

.back-btn {
  background: #FFF;
  border: 2px solid #FF9966;
  color: #FF9966;
  transition: all 0.3s;
}

.back-btn:hover {
  background: #FF9966;
  color: #FFF;
  transform: translateX(-3px);
}

.page-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 28px;
  font-weight: bold;
  color: #FF6B35;
  margin: 0;
}

/* 内容区域 */
.content-wrapper {
  max-width: 900px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 卡片通用样式 */
.status-card,
.info-card,
.items-card {
  border-radius: 20px;
  overflow: hidden;
  animation: fadeInUp 0.6s ease-out;
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.card-header {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #FFF;
  font-size: 18px;
  font-weight: bold;
}

.info-card :deep(.el-card__header),
.items-card :deep(.el-card__header) {
  background: linear-gradient(135deg, #FF9966 0%, #FF6B35 100%);
  border-bottom: none;
  padding: 20px;
}

/* 订单状态卡片 */
.status-card {
  background: #FFF;
}

.status-content {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 20px;
}

.status-icon {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #FFF;
}

.status-info {
  flex: 1;
}

.status-text {
  font-size: 28px;
  font-weight: bold;
  margin-bottom: 8px;
}

.order-no {
  font-size: 14px;
  color: #999;
}

.actions {
  display: flex;
  gap: 15px;
  justify-content: flex-end;
}

.cancel-order-btn,
.pay-btn {
  border-radius: 25px;
  font-size: 16px;
  font-weight: bold;
  padding: 12px 30px;
}

.cancel-order-btn {
  border: 2px solid #FFE8CC;
  color: #666;
}

.pay-btn {
  background: linear-gradient(135deg, #FF9966 0%, #FF6B35 100%);
  border: none;
}

/* 配送信息 */
.delivery-info {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.info-row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
}

.info-icon {
  color: #FF9966;
  font-size: 18px;
}

.info-label {
  color: #666;
  font-weight: bold;
  min-width: 80px;
}

.info-value {
  color: #333;
  flex: 1;
}

.info-value.highlight {
  color: #FF6B35;
  font-weight: 600;
}

/* 商品明细 */
.items-list {
  display: flex;
  flex-direction: column;
  gap: 15px;
  margin-bottom: 20px;
}

.item-row {
  display: flex;
  align-items: center;
  gap: 15px;
  padding: 15px;
  background: #FFF5E6;
  border-radius: 12px;
  transition: all 0.3s;
}

.item-row:hover {
  background: #FFE8CC;
  transform: translateX(5px);
}

.item-image {
  width: 80px;
  height: 80px;
  border-radius: 10px;
  overflow: hidden;
  flex-shrink: 0;
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
  background: #FFE8CC;
  color: #FF9966;
}

.item-info {
  flex: 1;
}

.item-name {
  font-size: 16px;
  font-weight: bold;
  color: #333;
  margin-bottom: 5px;
}

.item-price {
  font-size: 14px;
  color: #FF6B35;
}

.item-quantity {
  font-size: 16px;
  color: #666;
  min-width: 60px;
  text-align: center;
}

.item-subtotal {
  font-size: 18px;
  font-weight: bold;
  color: #FF6B35;
  min-width: 100px;
  text-align: right;
}

/* 费用汇总 */
.summary {
  padding-top: 20px;
  border-top: 2px solid #FFE8CC;
}

.summary-row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 10px;
  font-size: 15px;
}

.summary-label {
  color: #666;
}

.summary-value {
  color: #333;
  font-weight: bold;
}

.summary-value.free {
  color: #67C23A;
}

.total-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 15px;
  padding-top: 15px;
  border-top: 2px dashed #FFE8CC;
}

.total-label {
  font-size: 18px;
  font-weight: bold;
  color: #333;
}

.total-price {
  font-size: 32px;
  font-weight: bold;
  color: #FF6B35;
}

/* 支付弹窗样式（复用之前的样式）*/
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

.simulate-btn {
  background: linear-gradient(135deg, #67C23A 0%, #85CE61 100%);
  border: none;
  color: #FFF;
}

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
@media (max-width: 768px) {
  .page-title {
    font-size: 22px;
  }
  
  .status-text {
    font-size: 22px;
  }
  
  .item-row {
    flex-wrap: wrap;
  }
  
  .item-image {
    width: 60px;
    height: 60px;
  }
}
</style>
