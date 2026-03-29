<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getAdminOrderList, updateOrderStatus } from '@/api/order'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useDataTranslation } from '@/composables/useDataTranslation'
import { 
  Document, 
  Clock, 
  Check, 
  Van, 
  CircleCheck,
  Close
} from '@element-plus/icons-vue'
import type { OrderSummary } from '@/types'

const { t } = useI18n()
const { translateOrderStatusByLocale } = useDataTranslation()

const loading = ref(false)
const tableData = ref<OrderSummary[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getAdminOrderList({
      pageNum: pageNum.value,
      pageSize: pageSize.value
    })
    if (res.code === 200 && res.data) {
      tableData.value = res.data.records
      total.value = res.data.total
    }
  } catch (error: any) {
    ElMessage.error(error.message || t('common.empty'))
    console.error(error)
  } finally {
    loading.value = false
  }
}

// 0-Pending, 1-Paid/WaitAccept, 2-Cooking, 3-Delivering, 4-Completed, 5-Cancelled
const getStatusTag = (status: number) => {
  const map: Record<number, string> = {
    0: 'warning',
    1: 'primary',
    2: 'warning',
    3: 'success',
    4: 'success',
    5: 'info'
  }
  return map[status] || 'info'
}

const getStatusText = (status: number) => {
  const map: Record<number, string> = {
    0: '待支付',
    1: '待接单',
    2: '制作中',
    3: '已派送',
    4: '已送达',
    5: '已取消'
  }
  const statusText = map[status] || '未知'
  return translateOrderStatusByLocale(statusText)
}

const getStatusIcon = (status: number) => {
  const map: Record<number, any> = {
    0: Clock,
    1: Document,
    2: Clock,
    3: Van,
    4: CircleCheck,
    5: Close
  }
  return map[status] || Document
}

const handleUpdateStatus = async (row: OrderSummary, status: number, actionName: string) => {
  try {
    await ElMessageBox.confirm(`确认${actionName}吗？`, t('common.tip'), {
      type: 'warning',
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel')
    })
    
    await updateOrderStatus(row.id, status)
    ElMessage.success(`${actionName}成功`)
    fetchData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || t('common.empty'))
      console.error(error)
    }
  }
}

onMounted(() => {
  fetchData()
})
</script>

<template>
  <div class="order-manage">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">📋 {{ t('adminOrder.title') }}</h2>
        <p class="page-subtitle">{{ t('adminOrder.subtitle') }}</p>
      </div>
      <div class="stats-box">
        <div class="stat-item">
          <div class="stat-value">{{ total }}</div>
          <div class="stat-label">{{ t('profilePage.stats.totalOrders') }}</div>
        </div>
      </div>
    </div>

    <!-- 数据表格 -->
    <div class="table-container">
      <el-table 
        :data="tableData" 
        v-loading="loading" 
        style="width: 100%"
        :header-cell-style="{ background: '#FFF5E6', color: '#FF6B35', fontWeight: 'bold' }"
        stripe
      >
        <el-table-column prop="orderNo" :label="t('adminOrder.table.orderNo')" width="200" show-overflow-tooltip />
        <el-table-column prop="createTime" :label="t('adminOrder.table.orderTime')" width="180" align="center" />
        <el-table-column prop="receiverName" :label="t('adminOrder.table.receiver')" width="120" align="center" />
        <el-table-column prop="receiverPhone" :label="t('adminOrder.table.phone')" width="130" align="center" />
        <el-table-column prop="totalAmount" :label="t('adminOrder.table.amount')" width="120" align="center">
          <template #default="{ row }">
            <span class="price-text">¥{{ row.totalAmount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" :label="t('adminOrder.table.status')" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusTag(row.status)" :icon="getStatusIcon(row.status)">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="address" :label="t('adminOrder.table.address')" min-width="200" show-overflow-tooltip />
        <el-table-column :label="t('adminOrder.table.actions')" fixed="right" width="280" align="center">
          <template #default="{ row }">
            <!-- 待接单 -> 接单 (转制作中) -->
            <el-button 
              v-if="row.status === 1"
              type="primary" 
              size="small"
              :icon="Check"
              @click="handleUpdateStatus(row, 2, '接单')"
              class="action-btn"
            >
              接单
            </el-button>

            <!-- 制作中 -> 派送 (转已派送) -->
            <el-button 
              v-if="row.status === 2"
              type="success" 
              size="small"
              :icon="Van"
              @click="handleUpdateStatus(row, 3, '派送')"
              class="action-btn"
            >
              派送
            </el-button>

            <!-- 已派送 -> 送达 (转已完成) -->
            <el-button 
              v-if="row.status === 3"
              type="success" 
              size="small"
              :icon="CircleCheck"
              @click="handleUpdateStatus(row, 4, '确认送达')"
              class="action-btn"
            >
              送达
            </el-button>
            
            <!-- 待支付/待接单 -> 取消 -->
            <el-button 
              v-if="row.status === 0 || row.status === 1"
              type="danger" 
              size="small"
              :icon="Close"
              @click="handleUpdateStatus(row, 5, '取消订单')"
              class="action-btn"
            >
              取消
            </el-button>

            <!-- 已完成或已取消显示状态 -->
            <el-tag 
              v-if="row.status === 4 || row.status === 5"
              :type="row.status === 4 ? 'success' : 'info'"
              size="large"
            >
              {{ row.status === 4 ? '✓ 已完成' : '✗ 已取消' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="fetchData"
          @size-change="fetchData"
          background
          class="warm-pagination"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.order-manage {
  padding: 24px;
  background: linear-gradient(135deg, #FFF5E6 0%, #FFE8CC 100%);
  min-height: 100vh;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding: 24px;
  background: #FFF;
  border-radius: 16px;
  box-shadow: 0 4px 12px rgba(255, 107, 53, 0.1);
  animation: slideInDown 0.6s ease-out;
}

@keyframes slideInDown {
  from {
    opacity: 0;
    transform: translateY(-20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.header-left {
  flex: 1;
}

.page-title {
  margin: 0 0 8px 0;
  font-size: 28px;
  font-weight: bold;
  color: #FF6B35;
  text-shadow: 1px 1px 2px rgba(0, 0, 0, 0.05);
}

.page-subtitle {
  margin: 0;
  font-size: 14px;
  color: #999;
}

.stats-box {
  display: flex;
  gap: 20px;
}

.stat-item {
  text-align: center;
  padding: 16px 32px;
  background: linear-gradient(135deg, #FFE8CC 0%, #FFD9B3 100%);
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.stat-value {
  font-size: 32px;
  font-weight: bold;
  color: #FF6B35;
  margin-bottom: 4px;
}

.stat-label {
  font-size: 14px;
  color: #999;
}

.table-container {
  background: #FFF;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
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

.price-text {
  font-size: 16px;
  font-weight: bold;
  color: #FF6B35;
}

.action-btn {
  font-weight: bold;
  margin: 2px;
  border-radius: 16px;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid #FFE8CC;
}

.warm-pagination {
  --el-pagination-bg-color: #FFF;
  --el-pagination-button-color: #FF9966;
  --el-pagination-hover-color: #FF6B35;
}

.warm-pagination :deep(.el-pager li) {
  background: #FFF;
  border: 2px solid #FFE8CC;
  color: #FF9966;
  font-weight: bold;
  border-radius: 8px;
  margin: 0 4px;
  transition: all 0.3s;
}

.warm-pagination :deep(.el-pager li:hover) {
  border-color: #FF9966;
  color: #FF6B35;
  transform: translateY(-2px);
}

.warm-pagination :deep(.el-pager li.is-active) {
  background: linear-gradient(135deg, #FF9966 0%, #FF6B35 100%);
  border-color: #FF6B35;
  color: #FFF;
  box-shadow: 0 4px 12px rgba(255, 107, 53, 0.3);
}

.warm-pagination :deep(.btn-prev),
.warm-pagination :deep(.btn-next) {
  background: #FFF;
  border: 2px solid #FFE8CC;
  color: #FF9966;
  font-weight: bold;
  border-radius: 8px;
  transition: all 0.3s;
}

.warm-pagination :deep(.btn-prev:hover),
.warm-pagination :deep(.btn-next:hover) {
  border-color: #FF9966;
  color: #FF6B35;
  transform: translateY(-2px);
}

/* 表格样式优化 */
:deep(.el-table) {
  border-radius: 12px;
  overflow: hidden;
}

:deep(.el-table__row:hover) {
  background: #FFF5E6 !important;
}

:deep(.el-table td),
:deep(.el-table th) {
  padding: 16px 0;
}
</style>
