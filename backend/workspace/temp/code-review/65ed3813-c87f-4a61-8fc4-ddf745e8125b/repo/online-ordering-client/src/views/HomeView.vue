<script setup lang="ts">
import { ref, onMounted, watch, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { getActiveCategories, getDishList } from '@/api/dish'
import { addToCart } from '@/api/cart'
import { useUserStore } from '@/store/user'
import { useCartStore } from '@/store/cart'
import { useDeliveryStore } from '@/store/delivery'
import { ElMessage, ElDialog } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { 
  ArrowDown, 
  Search, 
  User, 
  SwitchButton, 
  Menu, 
  ForkSpoon, 
  Picture, 
  Star, 
  ShoppingCartFull,
  Box,
  TrendCharts,
  Setting,
  QuestionFilled,
  Clock,
  ArrowRight,
  ChatDotRound
} from '@element-plus/icons-vue'
import DishDetailModal from '@/components/DishDetailModal.vue'
import CartDrawer from '@/components/CartDrawer.vue'
import BottomBar from '@/components/BottomBar.vue'
import LanguageSwitcher from '@/components/LanguageSwitcher.vue'
import { useDataTranslation } from '@/composables/useDataTranslation'
import type { Category, Dish } from '@/types'

const router = useRouter()
const userStore = useUserStore()
const cartStore = useCartStore()
const deliveryStore = useDeliveryStore()

const { t } = useI18n()
const { 
  translateCategoryByLocale, 
  translateDishByLocale,
  locale 
} = useDataTranslation()

// 菜品详情模态框
const showDishDetail = ref(false)
const selectedDish = ref<Dish | null>(null)

// 购物车抽屉
const showCartDrawer = ref(false)

// 配送时间选择对话框
const showDeliveryTimeDialog = ref(false)

const categories = ref<Category[]>([])
const activeCategory = ref<number>(0) // 0 表示全部
const dishList = ref<Dish[]>([])
const loading = ref(false)

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  categoryId: undefined as number | undefined
})

const totalDishes = ref(0)

// 使用delivery store的时间显示
const currentTime = computed(() => deliveryStore.currentTimeDisplay)
const deliveryTime = computed(() => deliveryStore.estimatedDeliveryTime.display)

// 餐段选项
const mealPeriodOptions = [
  { label: '午餐', value: 'lunch' },
  { label: '晚餐', value: 'dinner' }
]

// 获取当前餐段的时间段选项
const currentTimeSlots = computed(() => {
  if (deliveryStore.mealPeriod) {
    return deliveryStore.getTimeSlotsByMealPeriod(deliveryStore.mealPeriod)
  }
  return []
})

// 打开配送时间选择对话框
const openDeliveryTimeDialog = () => {
  showDeliveryTimeDialog.value = true
}

// 确认配送时间选择
const confirmDeliveryTime = () => {
  if (deliveryStore.deliveryMode === 'scheduled') {
    if (!deliveryStore.mealPeriod) {
      ElMessage.warning(t('home.messages.mealPeriodRequired'))
      return
    }
    if (!deliveryStore.selectedTime) {
      ElMessage.warning(t('home.messages.deliveryTimeRequired'))
      return
    }
  } else {
    // 立即配送模式，重新生成随机送达时间
    deliveryStore.setDeliveryMode('immediate')
  }
  showDeliveryTimeDialog.value = false
  ElMessage.success(t('home.messages.deliveryTimeSet'))
}

// 初始化加载
onMounted(async () => {
  await fetchCategories()
  await fetchDishes()
  if (userStore.token) {
    cartStore.fetchCart()
  }
})

const fetchCategories = async () => {
  try {
    const res = await getActiveCategories()
    categories.value = res.data
  } catch (error) {
    console.error(error)
  }
}

const fetchDishes = async () => {
  loading.value = true
  try {
    if (activeCategory.value === 0) {
      queryParams.value.categoryId = undefined
    } else {
      queryParams.value.categoryId = activeCategory.value
    }
    const res = await getDishList(queryParams.value)
    // 后端返回的是 records 字段，不是 list
    dishList.value = res.data.records || []
    totalDishes.value = res.data.total || 0
    console.log('菜品数据:', dishList.value)
    console.log('总数:', totalDishes.value)
  } catch (error) {
    console.error('获取菜品失败:', error)
    dishList.value = []
    totalDishes.value = 0
  } finally {
    loading.value = false
  }
}

const handlePageChange = (page: number) => {
  queryParams.value.pageNum = page
  fetchDishes()
  // 滚动到顶部
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

const getCategoryEmoji = (name: string): string => {
  const emojiMap: Record<string, string> = {
    '热菜': '🌶️',
    '凉菜': '🥗',
    '主食': '🍚',
    '汤品': '🍲',
    '饮品': '🥤',
    '甜点': '🍰'
  }
  return emojiMap[name] || '🍽️'
}

const handleAddToCart = async (dish: Dish, quantity: number = 1) => {
  if (!userStore.token) {
    ElMessage.warning(t('home.messages.loginRequired'))
    router.push('/login')
    return
  }
  try {
    await addToCart({ dishId: dish.id, quantity })
    ElMessage.success(t('home.messages.addToCartSuccess'))
    cartStore.fetchCart()
  } catch (error) {
    console.error(error)
  }
}

const handleDishClick = (dish: Dish) => {
  selectedDish.value = dish
  showDishDetail.value = true
}

const handleAddToCartFromDetail = (dish: Dish, quantity: number) => {
  handleAddToCart(dish, quantity)
}

const handleLogout = () => {
  userStore.logout()
  router.push('/login')
}

watch(activeCategory, () => {
  queryParams.value.pageNum = 1
  fetchDishes()
})

// 监听搜索关键词
watch(() => queryParams.value.keyword, () => {
  queryParams.value.pageNum = 1
  fetchDishes()
})
</script>

<template>
  <div class="home-container">
    <!-- 顶部导航栏 -->
    <div class="top-nav-bar">
      <div class="nav-left">
        <img src="/logo.png" alt="Logo" class="nav-logo" />
        <span class="nav-brand">{{ $t('app.name') }}</span>
        
        <!-- 导航菜单项（移到左侧） -->
        <div class="nav-menu">
          <div 
            class="nav-link" 
            :class="{ active: router.currentRoute.value.path === '/categories' }"
            @click="router.push('/categories')"
          >
            <el-icon><Menu /></el-icon>
            <span>{{ $t('nav.menu') }}</span>
          </div>
          <div 
            class="nav-link" 
            :class="{ active: router.currentRoute.value.path === '/profile' }"
            @click="router.push('/profile')"
          >
            <el-icon><User /></el-icon>
            <span>{{ $t('nav.profile') }}</span>
          </div>
          <div 
            class="nav-link"
            :class="{ active: router.currentRoute.value.path === '/customer-service' }"
            @click="router.push('/customer-service')"
          >
            <el-icon><ChatDotRound /></el-icon>
            <span>{{ $t('nav.customerService') }}</span>
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
        <LanguageSwitcher />
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
                  <el-icon><ShoppingCartFull /></el-icon>
                  {{ $t('nav.orders') }}
                </el-dropdown-item>
                <el-dropdown-item @click="router.push('/customer-service')">
                  <el-icon><ChatDotRound /></el-icon>
                  {{ $t('nav.customerService') }}
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

    <div class="main-content">
      <!-- 左侧分类导航 -->
      <div class="category-sidebar">
        <!-- 时间显示模块（可点击） -->
        <div class="time-info clickable" @click="openDeliveryTimeDialog">
          <div class="current-time">
            <el-icon><Clock /></el-icon>
            <span>{{ currentTime }}</span>
          </div>
          <div class="delivery-time">
            <span class="delivery-label">{{ $t('home.deliveryTime') }}</span>
            <span class="delivery-value">{{ deliveryTime }}</span>
            <el-icon class="edit-icon"><ArrowRight /></el-icon>
          </div>
        </div>
        
        <div class="category-title">
          <el-icon><Menu /></el-icon>
          {{ $t('home.category') }}
        </div>
        <div 
          class="category-item" 
          :class="{ active: activeCategory === 0 }"
          @click="activeCategory = 0"
        >
          <span class="category-emoji">🏠</span>
          <span>{{ $t('home.all') }}</span>
        </div>
        <div 
          v-for="cat in categories" 
          :key="cat.id"
          class="category-item"
          :class="{ active: activeCategory === cat.id }"
          @click="activeCategory = cat.id"
        >
          <span class="category-emoji">{{ getCategoryEmoji(cat.name) }}</span>
          <span>{{ translateCategoryByLocale(cat).name }}</span>
        </div>
      </div>

      <!-- 右侧菜品列表 -->
      <div class="dish-content">
        <div class="content-header">
          <h2>
            {{ activeCategory === 0 ? $t('home.allDishes') : translateCategoryByLocale(categories.find(c => c.id === activeCategory) || { name: '' }).name }}
          </h2>
          <span class="dish-count">{{ $t('home.totalDishes', { count: totalDishes }) }}</span>
        </div>
        
        <!-- 搜索框（移到全部美食标题下方） -->
        <div class="search-section">
          <el-input 
            v-model="queryParams.keyword" 
            :placeholder="$t('home.searchPlaceholder')" 
            @keyup.enter="fetchDishes"
            class="internal-search-input"
            clearable
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </div>

        <div class="dish-list">
          <div v-loading="loading" class="loading-wrapper">
            <transition-group name="dish-list" tag="div" class="dish-grid" v-if="!loading && dishList.length > 0">
              <el-card 
                v-for="dish in dishList" 
                :key="dish.id" 
                :body-style="{ padding: '0px' }" 
                shadow="hover"
                class="dish-card"
                @click="handleDishClick(dish)"
              >
              <div class="image-wrapper">
                <img v-if="dish.imageUrl" :src="dish.imageUrl" class="dish-image" />
                <div v-else class="no-image">
                  <el-icon :size="50"><Picture /></el-icon>
                  <p>{{ $t('home.noImage') }}</p>
                </div>
                  <div class="dish-badge" v-if="dish.sales > 50 && dish.status === 1">
                  <el-icon><Star /></el-icon>
                  {{ $t('home.hot') }}
                </div>
                <div class="dish-badge off-shelf" v-if="dish.status === 0">
                  {{ $t('home.offShelf') }}
                </div>
              </div>
              <div class="dish-info">
                <div class="dish-name">{{ translateDishByLocale(dish).name }}</div>
                <div class="dish-desc">{{ translateDishByLocale(dish).description || $t('home.descFallback') }}</div>
                <div class="dish-stats">
                  <span class="stock-info" v-if="dish.stock">
                    <el-icon><Box /></el-icon>
                    {{ $t('home.stock', { count: dish.stock }) }}
                  </span>
                  <span class="sales-info" v-if="dish.sales">
                    <el-icon><TrendCharts /></el-icon>
                    {{ $t('home.sold', { count: dish.sales }) }}
                  </span>
                </div>
                <div class="dish-footer">
                  <div class="price-section">
                    <span class="price-label">¥</span>
                    <span class="price">{{ dish.price }}</span>
                  </div>
                  <el-button 
                    type="warning" 
                    size="small" 
                    @click.stop="handleAddToCart(dish)"
                    class="add-btn"
                    :disabled="dish.status === 0"
                  >
                    <el-icon><ShoppingCartFull /></el-icon>
                    {{ dish.status === 0 ? $t('home.offShelf') : $t('home.addToCart') }}
                  </el-button>
                </div>
              </div>
            </el-card>
            </transition-group>
          </div>

          <!-- 空状态 -->
          <el-empty 
            v-if="!loading && dishList.length === 0" 
            :description="$t('home.emptyDishes')"
            class="empty-state"
          >
            <template #image>
              <el-icon :size="100" color="#FFE8CC">
                <ForkSpoon />
              </el-icon>
            </template>
          </el-empty>

          <!-- 分页器 -->
          <div class="pagination-wrapper" v-if="totalDishes > queryParams.pageSize">
            <el-pagination
              v-model:current-page="queryParams.pageNum"
              :page-size="queryParams.pageSize"
              :total="totalDishes"
              layout="prev, pager, next, jumper, total"
              @current-change="handlePageChange"
              background
              class="warm-pagination"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- 菜品详情模态框 -->
    <DishDetailModal
      v-if="selectedDish"
      v-model:visible="showDishDetail"
      :dish="selectedDish"
      @add-to-cart="handleAddToCartFromDetail"
    />

    <!-- 购物车抽屉 -->
    <CartDrawer v-model:visible="showCartDrawer" />

    <!-- 底部导航栏 -->
    <BottomBar :show-drawer="showCartDrawer" @update:show-drawer="showCartDrawer = $event" />
  </div>
</template>

<style scoped>
/* 整体容器 */
.home-container {
  min-height: 100vh;
  background: linear-gradient(135deg, #FFF5E6 0%, #FFE8CC 100%);
  padding-bottom: 90px; /* 为底部导航栏留出空间 */
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
  font-size: 15px;
  border-radius: 8px;
  transition: all 0.3s;
}

.nav-link:hover {
  background: #FFF5E6;
  color: #FF6B35;
}

.nav-link.active {
  color: #FF6B35;
  background: #FFF5E6;
}

.nav-link .el-icon {
  font-size: 18px;
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

/* 搜索区域 */
.search-section {
  margin-bottom: 24px;
  padding: 0 20px;
  margin-top: 16px;
}

.internal-search-input {
  width: 100%;
}

.internal-search-input :deep(.el-input__wrapper) {
  height: 44px;
  border-radius: 22px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  border: 2px solid #F0F0F0;
  background: #FFF;
  transition: all 0.3s;
}

.internal-search-input :deep(.el-input__wrapper:hover) {
  border-color: #FF9966;
  box-shadow: 0 3px 16px rgba(255, 153, 102, 0.12);
}

.internal-search-input :deep(.el-input__wrapper.is-focus) {
  border-color: #FF6B35;
  box-shadow: 0 4px 20px rgba(255, 107, 53, 0.15);
}

.internal-search-input :deep(.el-input__inner) {
  font-size: 15px;
}

/* 主内容区 */
.main-content {
  display: flex;
  gap: 20px;
  padding: 0 30px;
  max-width: 1400px;
  margin: 0 auto;
}

/* 左侧分类栏 */
.category-sidebar {
  width: 240px;
  background: #FFF;
  border-radius: 15px;
  padding: 20px 0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  position: sticky;
  top: 80px;
  height: fit-content;
  animation: slideInLeft 0.6s ease-out;
}

/* 时间信息模块 */
.time-info {
  padding: 15px 20px;
  margin-bottom: 15px;
  border-bottom: 2px solid #FFE8CC;
  background: linear-gradient(135deg, #FFF5E6 0%, #FFE8CC 100%);
  border-radius: 10px;
  margin: 0 10px 15px 10px;
  position: relative;
}

.time-info.clickable {
  cursor: pointer;
  transition: all 0.3s;
}

.time-info.clickable:hover {
  background: linear-gradient(135deg, #FFE8CC 0%, #FFD9B3 100%);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(255, 153, 102, 0.2);
}

.edit-icon {
  position: absolute;
  right: 15px;
  bottom: 15px;
  font-size: 14px;
  color: #FF6B35;
  opacity: 0.6;
}

.current-time {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #666;
  margin-bottom: 10px;
  font-weight: 500;
}

.current-time .el-icon {
  color: #FF6B35;
  font-size: 16px;
}

.delivery-time {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.delivery-label {
  font-size: 12px;
  color: #999;
}

.delivery-value {
  font-size: 13px;
  color: #FF6B35;
  font-weight: bold;
  margin-right: 20px;
}

/* 配送时间选择对话框样式 */
.delivery-time-dialog :deep(.el-dialog__body) {
  padding: 20px;
}

.delivery-time-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.section-title {
  font-size: 16px;
  font-weight: bold;
  color: #333;
  margin-bottom: 12px;
}

.delivery-mode-section,
.scheduled-section {
  padding: 15px;
  background: #F9F9F9;
  border-radius: 8px;
}

.delivery-mode-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.delivery-mode-radio {
  width: 100%;
  margin: 0;
  padding: 12px;
  background: #FFF;
  border-radius: 8px;
  border: 2px solid #E0E0E0;
  transition: all 0.3s;
}

.delivery-mode-radio:hover {
  border-color: #FF9966;
}

.delivery-mode-radio.is-checked {
  border-color: #FF6B35;
  background: #FFF5E6;
}

.radio-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.radio-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: bold;
  color: #333;
}

.radio-desc {
  font-size: 13px;
  color: #999;
  margin-left: 24px;
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

.time-selection-section {
  margin-top: 15px;
}

.time-select {
  width: 100%;
}

.delivery-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  background: #FFF5E6;
  border-radius: 8px;
  font-size: 13px;
  color: #666;
}

.delivery-tip .el-icon {
  color: #FF6B35;
  font-size: 16px;
}

.category-title {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 20px 15px 20px;
  font-size: 18px;
  font-weight: bold;
  color: #FF6B35;
  border-bottom: 2px solid #FFE8CC;
  margin-bottom: 10px;
}

.category-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 20px;
  cursor: pointer;
  transition: all 0.3s;
  color: #666;
  font-size: 15px;
}

.category-emoji {
  font-size: 20px;
  display: inline-block;
  transition: transform 0.3s;
}

.category-item:hover .category-emoji {
  transform: scale(1.2) rotate(10deg);
}

.category-item:hover {
  background: linear-gradient(90deg, #FFE8CC 0%, transparent 100%);
  color: #FF6B35;
  padding-left: 25px;
}

.category-item.active {
  background: linear-gradient(90deg, #FF9966 0%, #FF6B35 100%);
  color: #FFF;
  font-weight: bold;
  box-shadow: 0 2px 8px rgba(255, 107, 53, 0.3);
  border-radius: 10px;
  margin: 0 10px;
  padding-left: 15px;
}

/* 菜品内容区 */
.dish-content {
  flex: 1;
  animation: fadeIn 0.8s ease-out;
}

.content-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding: 20px;
  background: #FFF;
  border-radius: 15px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.content-header h2 {
  margin: 0;
  color: #FF6B35;
  font-size: 24px;
}

.dish-count {
  color: #999;
  font-size: 14px;
}

/* 菜品网格 */
.dish-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}

.dish-card {
  border-radius: 15px;
  overflow: hidden;
  transition: all 0.3s;
  border: 2px solid transparent;
  cursor: pointer;
}

.dish-card:hover {
  transform: translateY(-8px);
  box-shadow: 0 12px 28px rgba(255, 107, 53, 0.25);
  border-color: #FF9966;
}

.image-wrapper {
  height: 200px;
  background: linear-gradient(135deg, #FFE8CC 0%, #FFD9B3 100%);
  display: flex;
  justify-content: center;
  align-items: center;
  overflow: hidden;
  position: relative;
}

.dish-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s;
}

.dish-card:hover .dish-image {
  transform: scale(1.1);
}

.no-image {
  color: #999;
  text-align: center;
}

.dish-badge {
  position: absolute;
  top: 10px;
  right: 10px;
  background: linear-gradient(135deg, #FF6B35 0%, #FF4500 100%);
  color: #FFF;
  padding: 5px 12px;
  border-radius: 15px;
  font-size: 12px;
  font-weight: bold;
  display: flex;
  align-items: center;
  gap: 4px;
  box-shadow: 0 2px 8px rgba(255, 69, 0, 0.4);
  animation: bounceIn 0.6s ease-out;
}

.dish-badge.off-shelf {
  background: #909399;
  box-shadow: 0 2px 8px rgba(144, 147, 153, 0.4);
}

.dish-info {
  padding: 16px;
}

.dish-name {
  font-size: 18px;
  font-weight: bold;
  margin-bottom: 8px;
  color: #333;
}

.dish-desc {
  font-size: 13px;
  color: #999;
  margin-bottom: 10px;
  height: 20px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dish-stats {
  display: flex;
  gap: 15px;
  margin-bottom: 12px;
  font-size: 12px;
}

.stock-info,
.sales-info {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #999;
}

.stock-info .el-icon {
  color: #67C23A;
}

.sales-info .el-icon {
  color: #FF9966;
}

.dish-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.price-section {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.price-label {
  color: #FF6B35;
  font-size: 16px;
  font-weight: bold;
  vertical-align: top;
}

.price {
  color: #FF6B35;
  font-size: 24px;
  font-weight: bold;
  font-family: 'Arial', sans-serif;
}

.sales {
  font-size: 12px;
  color: #999;
}

.add-btn {
  border-radius: 20px;
  font-weight: bold;
  transition: all 0.3s;
}

.add-btn:hover {
  transform: scale(1.05);
  box-shadow: 0 4px 12px rgba(255, 107, 53, 0.3);
}

/* 动画定义 */
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

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
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

/* 列表过渡动画 */
.dish-list-enter-active {
  animation: bounceIn 0.6s ease-out;
}

.dish-list-leave-active {
  animation: fadeOut 0.3s ease-out;
}

@keyframes fadeOut {
  to {
    opacity: 0;
    transform: scale(0.9);
  }
}

/* 空状态样式 */
.empty-state {
  padding: 80px 20px;
  animation: fadeIn 0.6s ease-out;
}

/* 分页器样式 */
.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 40px;
  padding: 30px 0;
  animation: fadeIn 0.8s ease-out;
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
  margin: 0 5px;
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

.warm-pagination :deep(.btn-prev:disabled),
.warm-pagination :deep(.btn-next:disabled) {
  opacity: 0.5;
  cursor: not-allowed;
}

.warm-pagination :deep(.el-pagination__total) {
  color: #FF9966;
  font-weight: bold;
}

.warm-pagination :deep(.el-pagination__jump) {
  color: #666;
}

</style>
