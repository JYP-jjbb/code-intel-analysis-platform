<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { getDishList, addDish, updateDish, getActiveCategories } from '@/api/dish'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { Plus, Edit, Delete, Top, Bottom, Picture as IconPicture, View } from '@element-plus/icons-vue'
import DishDetailModal from '@/components/DishDetailModal.vue'
import type { Dish, Category } from '@/types'

const { t } = useI18n()

const loading = ref(false)
const tableData = ref<Dish[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const categories = ref<Category[]>([])

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref()
const form = ref({
  id: undefined as number | undefined,
  name: '',
  price: 0,
  categoryId: undefined as number | undefined,
  description: '',
  imageUrl: '',
  stock: 100,
  status: 1
})

// 菜品详情模态框
const showDishDetail = ref(false)
const selectedDish = ref<Dish | null>(null)

const rules = computed(() => ({
  name: [{ required: true, message: t('adminDish.validation.nameRequired'), trigger: 'blur' }],
  price: [{ required: true, message: t('adminDish.validation.priceRequired'), trigger: 'blur' }],
  categoryId: [{ required: true, message: t('adminDish.validation.categoryRequired'), trigger: 'change' }],
  stock: [{ required: true, message: t('adminDish.validation.stockRequired'), trigger: 'blur' }]
}))

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getDishList({
      pageNum: pageNum.value,
      pageSize: pageSize.value
    })
    if (res.code === 200 && res.data) {
      tableData.value = res.data.records
      total.value = res.data.total
    }
  } catch (error: any) {
    ElMessage.error(error.message || t('adminDish.messages.fetchFailed'))
    console.error(error)
  } finally {
    loading.value = false
  }
}

const fetchCategories = async () => {
  try {
    const res = await getActiveCategories()
    if (res.code === 200) {
      categories.value = res.data
    }
  } catch (error: any) {
    ElMessage.error(t('adminDish.messages.categoryFetchFailed'))
    console.error(error)
  }
}

const handleAdd = () => {
  dialogTitle.value = t('adminDish.dialog.addTitle')
  form.value = {
    id: undefined,
    name: '',
    price: 0,
    categoryId: undefined,
    description: '',
    imageUrl: '',
    stock: 100,
    status: 1
  }
  dialogVisible.value = true
}

const handleEdit = (row: Dish) => {
  dialogTitle.value = t('adminDish.dialog.editTitle')
  form.value = { ...row }
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid: boolean) => {
    if (valid) {
      try {
        if (form.value.id) {
          await updateDish(form.value.id, form.value)
          ElMessage.success(t('adminDish.messages.updateSuccess'))
        } else {
          await addDish(form.value)
          ElMessage.success(t('adminDish.messages.addSuccess'))
        }
        dialogVisible.value = false
        fetchData()
      } catch (error: any) {
        ElMessage.error(error.message || t('common.empty'))
        console.error(error)
      }
    }
  })
}

const handleStatusChange = async (row: Dish) => {
  try {
    const newStatus = row.status === 1 ? 0 : 1
    const actionText = newStatus === 1 ? '上架' : '下架'
    
    await ElMessageBox.confirm(`确认要${actionText}该菜品吗？`, t('common.tip'), {
      type: 'warning',
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel')
    })
    
    // Copy row data and update status
    const data = { ...row, status: newStatus }
    await updateDish(row.id, data)
    ElMessage.success(`${actionText}成功`)
    fetchData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || t('common.empty'))
      console.error(error)
    }
  }
}

const getCategoryName = (categoryId: number) => {
  return categories.value.find(c => c.id === categoryId)?.name || t('common.empty')
}

const handleViewDetail = (row: Dish) => {
  selectedDish.value = row
  showDishDetail.value = true
}

onMounted(() => {
  fetchCategories()
  fetchData()
})
</script>

<template>
  <div class="dish-manage">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">🍽️ {{ t('adminDish.title') }}</h2>
        <p class="page-subtitle">{{ t('adminDish.subtitle') }}</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="handleAdd" class="add-btn">
        {{ t('adminDish.dialog.addTitle') }}
      </el-button>
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
        <el-table-column prop="id" :label="t('adminDish.table.id')" width="80" align="center" />
        <el-table-column :label="t('adminDish.table.image')" width="100" align="center">
          <template #default="{ row }">
            <el-image 
              style="width: 60px; height: 60px; border-radius: 8px;" 
              :src="row.imageUrl" 
              fit="cover"
              :preview-src-list="[row.imageUrl]"
              preview-teleported
            >
              <template #error>
                <div class="image-slot">
                  <el-icon :size="30" color="#ccc"><IconPicture /></el-icon>
                </div>
              </template>
            </el-image>
          </template>
        </el-table-column>
        <el-table-column prop="name" :label="t('adminDish.table.name')" min-width="150" show-overflow-tooltip />
        <el-table-column prop="price" :label="t('adminDish.table.price')" width="100" align="center">
          <template #default="{ row }">
            <span class="price-text">¥{{ row.price }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="categoryName" :label="t('adminDish.table.category')" width="120" align="center">
          <template #default="{ row }">
            <el-tag type="warning" effect="plain">{{ getCategoryName(row.categoryId) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="stock" :label="t('adminDish.table.stock')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.stock > 50 ? 'success' : row.stock > 10 ? 'warning' : 'danger'">
              {{ row.stock }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sales" :label="t('dishDetail.sold')" width="100" align="center">
          <template #default="{ row }">
            <span class="sales-text">{{ row.sales }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" :label="t('adminDish.dialog.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '上架' : '下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('adminDish.table.actions')" fixed="right" width="280" align="center">
          <template #default="{ row }">
            <el-button 
              type="info" 
              :icon="View" 
              link 
              @click="handleViewDetail(row)"
              class="action-btn"
            >
              {{ t('common.search') }}
            </el-button>
            <el-button 
              type="primary" 
              :icon="Edit" 
              link 
              @click="handleEdit(row)"
              class="action-btn"
            >
              {{ t('common.edit') }}
            </el-button>
            <el-button 
              :type="row.status === 1 ? 'warning' : 'success'" 
              :icon="row.status === 1 ? Bottom : Top"
              link 
              @click="handleStatusChange(row)"
              class="action-btn"
            >
              {{ row.status === 1 ? '下架' : '上架' }}
            </el-button>
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

    <!-- 编辑对话框 -->
    <el-dialog 
      v-model="dialogVisible" 
      :title="dialogTitle" 
      width="600px"
      :close-on-click-modal="false"
      class="dish-dialog"
    >
      <el-form :model="form" ref="formRef" :rules="rules" label-width="90px">
        <el-form-item label="菜品名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入菜品名称" />
        </el-form-item>
        <el-form-item label="分类" prop="categoryId">
          <el-select v-model="form.categoryId" placeholder="请选择分类" style="width: 100%">
            <el-option
              v-for="item in categories"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="价格" prop="price">
          <el-input-number 
            v-model="form.price" 
            :min="0" 
            :precision="2" 
            :step="1"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="库存" prop="stock">
          <el-input-number 
            v-model="form.stock" 
            :min="0" 
            :precision="0"
            :step="10"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input 
            v-model="form.description" 
            type="textarea" 
            :rows="3"
            placeholder="请输入菜品描述"
          />
        </el-form-item>
        <el-form-item label="图片URL" prop="imageUrl">
          <el-input v-model="form.imageUrl" placeholder="请输入图片URL" />
          <div v-if="form.imageUrl" class="image-preview">
            <el-image 
              :src="form.imageUrl" 
              fit="cover"
              style="width: 100px; height: 100px; border-radius: 8px; margin-top: 10px;"
            >
              <template #error>
                <div class="image-slot">
                  <el-icon><IconPicture /></el-icon>
                  <span>加载失败</span>
                </div>
              </template>
            </el-image>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false" size="large">取消</el-button>
          <el-button type="primary" @click="handleSubmit" size="large">确定</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 菜品详情模态框 -->
    <DishDetailModal
      v-if="selectedDish"
      v-model:visible="showDishDetail"
      :dish="selectedDish"
      @add-to-cart="() => {}"
    />
  </div>
</template>

<style scoped>
.dish-manage {
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

.add-btn {
  font-size: 16px;
  font-weight: bold;
  padding: 12px 32px;
  border-radius: 25px;
  background: linear-gradient(135deg, #FF9966 0%, #FF6B35 100%);
  border: none;
  box-shadow: 0 4px 12px rgba(255, 107, 53, 0.3);
  transition: all 0.3s;
}

.add-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(255, 107, 53, 0.4);
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

.image-slot {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  width: 100%;
  height: 100%;
  background: #f5f7fa;
  color: #909399;
  font-size: 12px;
}

.price-text {
  font-size: 16px;
  font-weight: bold;
  color: #FF6B35;
}

.sales-text {
  color: #67C23A;
  font-weight: bold;
}

.action-btn {
  font-weight: bold;
  margin: 0 4px;
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

.dish-dialog :deep(.el-dialog__header) {
  background: linear-gradient(135deg, #FF9966 0%, #FF6B35 100%);
  padding: 20px;
  margin: 0;
}

.dish-dialog :deep(.el-dialog__title) {
  color: #FFF;
  font-size: 20px;
  font-weight: bold;
}

.dish-dialog :deep(.el-dialog__headerbtn .el-dialog__close) {
  color: #FFF;
  font-size: 20px;
}

.dish-dialog :deep(.el-dialog__body) {
  padding: 30px;
}

.image-preview {
  margin-top: 10px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.dialog-footer .el-button {
  border-radius: 20px;
  padding: 12px 32px;
  font-weight: bold;
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
