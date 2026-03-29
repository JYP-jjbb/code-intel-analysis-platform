<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { getOrderList } from '@/api/order'
import { getAddressList, addAddress, updateAddress, deleteAddress, setDefaultAddress, type Address } from '@/api/address'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  User,
  Phone,
  Message,
  Location,
  Edit,
  Key,
  ShoppingBag,
  TrendCharts,
  Clock,
  Medal,
  Setting,
  ArrowLeft,
  Menu,
  QuestionFilled,
  ArrowDown,
  SwitchButton,
  Plus,
  Delete,
  Star,
  ChatDotRound
} from '@element-plus/icons-vue'
import type { OrderSummary } from '@/types'

const router = useRouter()
const userStore = useUserStore()

const { t } = useI18n()

const activeTab = ref('profile')
const editMode = ref(false)
const recentOrders = ref<OrderSummary[]>([])
const loading = ref(false)

// 地址管理
const addressList = ref<Address[]>([])
const showAddressDialog = ref(false)
const addressForm = ref<Address>({
  receiverName: '',
  receiverPhone: '',
  address: '',
  isDefault: false
})
const editingAddressId = ref<number | null>(null)

const addressDialogTitle = computed(() => {
  return editingAddressId.value ? t('profilePage.addressDialog.editTitle') : t('profilePage.addressDialog.addTitle')
})

// 用户统计数据
const userStats = ref({
  totalOrders: 0,
  totalSpent: 0,
  favoriteCount: 8,
  memberDays: 0
})

// 表单数据
const userForm = ref({
  username: '',
  phone: '',
  email: '',
  address: ''
})

// 密码修改表单
const passwordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const showPasswordDialog = ref(false)

onMounted(async () => {
  if (userStore.userInfo) {
    userForm.value = {
      username: userStore.userInfo.username,
      phone: userStore.userInfo.phone || '',
      email: userStore.userInfo.email || '',
      address: userStore.userInfo.address || ''
    }
    
    // 计算会员天数
    const createDate = new Date('2024-01-01') // 模拟注册日期
    const today = new Date()
    userStats.value.memberDays = Math.floor((today.getTime() - createDate.getTime()) / (1000 * 60 * 60 * 24))
  }
  
  await fetchRecentOrders()
  await fetchAddresses()
})

// 获取地址列表
const fetchAddresses = async () => {
  try {
    const res = await getAddressList()
    if (res.code === 200) {
      addressList.value = res.data
    }
  } catch (error) {
    console.error('获取地址列表失败:', error)
  }
}

// 打开添加地址对话框
const openAddAddressDialog = () => {
  editingAddressId.value = null
  addressForm.value = {
    receiverName: '',
    receiverPhone: '',
    address: '',
    isDefault: false
  }
  showAddressDialog.value = true
}

// 编辑地址
const handleEditAddress = (address: Address) => {
  editingAddressId.value = address.id || null
  addressForm.value = {
    receiverName: address.receiverName,
    receiverPhone: address.receiverPhone,
    address: address.address,
    isDefault: address.isDefault
  }
  showAddressDialog.value = true
}

// 保存地址
const handleSaveAddress = async () => {
  // 验证表单
  if (!addressForm.value.receiverName) {
    ElMessage.warning(t('profilePage.address.validation.receiverNameRequired'))
    return
  }
  if (!addressForm.value.receiverPhone) {
    ElMessage.warning(t('profilePage.address.validation.phoneRequired'))
    return
  }
  if (!/^1[3-9]\d{9}$/.test(addressForm.value.receiverPhone)) {
    ElMessage.warning(t('profilePage.address.validation.phoneInvalid'))
    return
  }
  if (!addressForm.value.address) {
    ElMessage.warning(t('profilePage.address.validation.addressRequired'))
    return
  }

  try {
    if (editingAddressId.value) {
      // 编辑
      await updateAddress(editingAddressId.value, addressForm.value)
      ElMessage.success(t('profilePage.address.messages.updateSuccess'))
    } else {
      // 新增
      await addAddress(addressForm.value)
      ElMessage.success(t('profilePage.address.messages.addSuccess'))
    }
    showAddressDialog.value = false
    await fetchAddresses()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || t('profilePage.messages.actionFail'))
  }
}

// 删除地址
const handleDeleteAddress = async (id: number) => {
  try {
    await ElMessageBox.confirm(t('profilePage.address.messages.deleteConfirm'), t('common.tip'), {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      type: 'warning'
    })
    
    await deleteAddress(id)
    ElMessage.success(t('profilePage.address.messages.deleteSuccess'))
    await fetchAddresses()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || t('profilePage.address.messages.deleteFail'))
    }
  }
}

// 设置默认地址
const handleSetDefaultAddress = async (id: number) => {
  try {
    await setDefaultAddress(id)
    ElMessage.success(t('profilePage.address.messages.setDefaultSuccess'))
    await fetchAddresses()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || t('profilePage.address.messages.setDefaultFail'))
  }
}

const fetchRecentOrders = async () => {
  loading.value = true
  try {
    const res = await getOrderList({ pageNum: 1, pageSize: 5 })
    if (res.code === 200 && res.data) {
      recentOrders.value = res.data.records
      userStats.value.totalOrders = res.data.total
      
      // 计算总消费
      userStats.value.totalSpent = recentOrders.value.reduce((sum, order) => {
        return sum + Number(order.totalAmount)
      }, 0)
    }
  } catch (error: any) {
    console.error(error)
  } finally {
    loading.value = false
  }
}

const handleSaveProfile = () => {
  ElMessage.success(t('profilePage.messages.profileSaved'))
  editMode.value = false
  // TODO: 调用后端API保存用户信息
}

const handleChangePassword = () => {
  if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
    ElMessage.error(t('profilePage.messages.passwordMismatch'))
    return
  }
  ElMessage.success(t('profilePage.messages.passwordChanged'))
  showPasswordDialog.value = false
  passwordForm.value = {
    oldPassword: '',
    newPassword: '',
    confirmPassword: ''
  }
  // TODO: 调用后端API修改密码
}

const getMemberLevel = computed(() => {
  const spent = userStats.value.totalSpent
  if (spent >= 1000) return { level: t('profilePage.memberLevels.diamond'), color: '#409EFF', icon: '💎' }
  if (spent >= 500) return { level: t('profilePage.memberLevels.gold'), color: '#E6A23C', icon: '👑' }
  if (spent >= 200) return { level: t('profilePage.memberLevels.silver'), color: '#909399', icon: '🥈' }
  return { level: t('profilePage.memberLevels.normal'), color: '#67C23A', icon: '🌟' }
})

const getOrderStatusTag = (status: number) => {
  const map: Record<number, { type: any, text: string }> = {
    0: { type: 'warning', text: t('profilePage.orderStatus.pendingPay') },
    1: { type: 'primary', text: t('profilePage.orderStatus.pendingAccept') },
    2: { type: 'warning', text: t('profilePage.orderStatus.preparing') },
    3: { type: 'success', text: t('profilePage.orderStatus.delivering') },
    4: { type: 'success', text: t('profilePage.orderStatus.delivered') },
    5: { type: 'info', text: t('profilePage.orderStatus.canceled') }
  }
  return map[status] || { type: 'info', text: t('profilePage.orderStatus.unknown') }
}

const goToOrders = () => {
  router.push('/orders')
}

const goBack = () => {
  router.back()
}

// 退出登录
const handleLogout = async () => {
  await userStore.logout()
  ElMessage.success(t('nav.logoutSuccess'))
  router.push('/login')
}
</script>

<template>
  <div class="profile-container">
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
            class="nav-link active"
          >
            <el-icon><User /></el-icon>
            <span>{{ $t('nav.profile') }}</span>
          </div>
          <div 
            class="nav-link"
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

    <div class="profile-content">
      <!-- 左侧用户卡片 -->
      <div class="user-card">
        <div class="user-header">
          <el-avatar :size="100" :icon="User" class="user-avatar" />
          <div class="user-info">
            <h2>{{ userStore.userInfo?.username }}</h2>
            <el-tag 
              :type="getMemberLevel.color" 
              effect="dark" 
              size="large"
              class="member-tag"
            >
              {{ getMemberLevel.icon }} {{ getMemberLevel.level }}
            </el-tag>
          </div>
        </div>

        <!-- 统计数据 -->
        <div class="stats-grid">
          <div class="stat-item">
            <div class="stat-icon">
              <el-icon :size="24" color="#FF6B35"><ShoppingBag /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ userStats.totalOrders }}</div>
              <div class="stat-label">{{ $t('profilePage.stats.totalOrders') }}</div>
            </div>
          </div>
          <div class="stat-item">
            <div class="stat-icon">
              <el-icon :size="24" color="#E6A23C"><TrendCharts /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">¥{{ userStats.totalSpent.toFixed(2) }}</div>
              <div class="stat-label">{{ $t('profilePage.stats.totalSpent') }}</div>
            </div>
          </div>
          <div class="stat-item">
            <div class="stat-icon">
              <el-icon :size="24" color="#67C23A"><Clock /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ userStats.memberDays }}</div>
              <div class="stat-label">{{ $t('profilePage.stats.memberDays') }}</div>
            </div>
          </div>
          <div class="stat-item">
            <div class="stat-icon">
              <el-icon :size="24" color="#409EFF"><Medal /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ userStats.favoriteCount }}</div>
              <div class="stat-label">{{ $t('profilePage.stats.favorites') }}</div>
            </div>
          </div>
        </div>

        <!-- 快捷操作 -->
        <div class="quick-actions">
          <el-button type="primary" :icon="ShoppingBag" @click="goToOrders" class="action-btn">
            {{ $t('nav.orders') }}
          </el-button>
          <el-button type="warning" :icon="Setting" class="action-btn">
            {{ $t('profilePage.actions.accountSettings') }}
          </el-button>
        </div>
      </div>

      <!-- 右侧内容区 -->
      <div class="content-area">
        <el-tabs v-model="activeTab" class="profile-tabs">
          <!-- 个人资料 -->
          <el-tab-pane :label="$t('profilePage.tabs.profile')" name="profile">
            <div class="tab-content">
              <div class="section-header">
                <h3>{{ $t('profilePage.sections.profileInfo') }}</h3>
                <el-button 
                  v-if="!editMode" 
                  type="primary" 
                  :icon="Edit" 
                  @click="editMode = true"
                  link
                >
                  {{ $t('common.edit') }}
                </el-button>
              </div>

              <el-form :model="userForm" label-width="100px" class="profile-form">
                <el-form-item :label="$t('profilePage.form.username')">
                  <el-input 
                    v-model="userForm.username" 
                    :prefix-icon="User"
                    :disabled="!editMode"
                  />
                </el-form-item>
                <el-form-item :label="$t('profilePage.form.phone')">
                  <el-input 
                    v-model="userForm.phone" 
                    :prefix-icon="Phone"
                    :disabled="!editMode"
                  />
                </el-form-item>
                <el-form-item :label="$t('profilePage.form.email')">
                  <el-input 
                    v-model="userForm.email" 
                    :prefix-icon="Message"
                    :disabled="!editMode"
                  />
                </el-form-item>
                <el-form-item :label="$t('profilePage.form.defaultAddress')">
                  <el-input 
                    v-model="userForm.address" 
                    :prefix-icon="Location"
                    :disabled="!editMode"
                    type="textarea"
                    :rows="2"
                  />
                </el-form-item>

                <el-form-item v-if="editMode">
                  <el-button type="primary" @click="handleSaveProfile">{{ $t('common.save') }}</el-button>
                  <el-button @click="editMode = false">{{ $t('common.cancel') }}</el-button>
                </el-form-item>
              </el-form>

              <div class="section-divider"></div>

              <div class="section-header">
                <h3>{{ $t('profilePage.sections.security') }}</h3>
              </div>
              <el-button 
                type="warning" 
                :icon="Key" 
                @click="showPasswordDialog = true"
                class="change-password-btn"
              >
                {{ $t('profilePage.actions.changePassword') }}
              </el-button>
            </div>
          </el-tab-pane>

          <!-- 最近订单 -->
          <el-tab-pane :label="$t('profilePage.tabs.orders')" name="orders">
            <div class="tab-content">
              <div class="section-header">
                <h3>{{ $t('profilePage.sections.recentOrders') }}</h3>
                <el-button type="primary" link @click="goToOrders">
                  {{ $t('profilePage.actions.viewAll') }}
                </el-button>
              </div>

              <div v-loading="loading" class="orders-list">
                <div 
                  v-for="order in recentOrders" 
                  :key="order.id"
                  class="order-card"
                  @click="router.push(`/order/${order.id}`)"
                >
                  <div class="order-header">
                    <span class="order-no">{{ t('profilePage.orders.orderNo', { no: order.orderNo }) }}</span>
                    <el-tag :type="getOrderStatusTag(order.status).type">
                      {{ getOrderStatusTag(order.status).text }}
                    </el-tag>
                  </div>
                  <div class="order-body">
                    <div class="order-info">
                      <p><strong>{{ $t('profilePage.orders.receiver') }}</strong>{{ order.receiverName }}</p>
                      <p><strong>{{ $t('profilePage.orders.phone') }}</strong>{{ order.receiverPhone }}</p>
                      <p class="order-address"><strong>{{ $t('profilePage.orders.address') }}</strong>{{ order.address }}</p>
                    </div>
                    <div class="order-amount">
                      <div class="amount-label">{{ $t('profilePage.orders.amount') }}</div>
                      <div class="amount-value">¥{{ order.totalAmount }}</div>
                    </div>
                  </div>
                  <div class="order-footer">
                    <span class="order-time">{{ order.createTime }}</span>
                  </div>
                </div>

                <el-empty 
                  v-if="!loading && recentOrders.length === 0" 
                  :description="$t('profilePage.orders.empty')"
                />
              </div>
            </div>
          </el-tab-pane>

          <!-- 会员权益 -->
          <el-tab-pane :label="$t('profilePage.tabs.benefits')" name="benefits">
            <div class="tab-content">
              <div class="section-header">
                <h3>{{ $t('profilePage.sections.benefits') }}</h3>
              </div>

              <div class="benefits-grid">
                <div class="benefit-card">
                  <div class="benefit-icon">🌟</div>
                  <h4>{{ $t('profilePage.benefits.normal.title') }}</h4>
                  <p class="benefit-requirement">{{ $t('profilePage.benefits.normal.requirement') }}</p>
                  <ul class="benefit-list">
                    <li>{{ $t('profilePage.benefits.normal.item1') }}</li>
                    <li>{{ $t('profilePage.benefits.normal.item2') }}</li>
                    <li>{{ $t('profilePage.benefits.normal.item3') }}</li>
                  </ul>
                </div>

                <div class="benefit-card silver">
                  <div class="benefit-icon">🥈</div>
                  <h4>{{ $t('profilePage.benefits.silver.title') }}</h4>
                  <p class="benefit-requirement">{{ $t('profilePage.benefits.silver.requirement') }}</p>
                  <ul class="benefit-list">
                    <li>{{ $t('profilePage.benefits.silver.item1') }}</li>
                    <li>{{ $t('profilePage.benefits.silver.item2') }}</li>
                    <li>{{ $t('profilePage.benefits.silver.item3') }}</li>
                    <li>{{ $t('profilePage.benefits.silver.item4') }}</li>
                  </ul>
                </div>

                <div class="benefit-card gold">
                  <div class="benefit-icon">👑</div>
                  <h4>{{ $t('profilePage.benefits.gold.title') }}</h4>
                  <p class="benefit-requirement">{{ $t('profilePage.benefits.gold.requirement') }}</p>
                  <ul class="benefit-list">
                    <li>{{ $t('profilePage.benefits.gold.item1') }}</li>
                    <li>{{ $t('profilePage.benefits.gold.item2') }}</li>
                    <li>{{ $t('profilePage.benefits.gold.item3') }}</li>
                    <li>{{ $t('profilePage.benefits.gold.item4') }}</li>
                    <li>{{ $t('profilePage.benefits.gold.item5') }}</li>
                  </ul>
                </div>

                <div class="benefit-card diamond">
                  <div class="benefit-icon">💎</div>
                  <h4>{{ $t('profilePage.benefits.diamond.title') }}</h4>
                  <p class="benefit-requirement">{{ $t('profilePage.benefits.diamond.requirement') }}</p>
                  <ul class="benefit-list">
                    <li>{{ $t('profilePage.benefits.diamond.item1') }}</li>
                    <li>{{ $t('profilePage.benefits.diamond.item2') }}</li>
                    <li>{{ $t('profilePage.benefits.diamond.item3') }}</li>
                    <li>{{ $t('profilePage.benefits.diamond.item4') }}</li>
                    <li>{{ $t('profilePage.benefits.diamond.item5') }}</li>
                    <li>{{ $t('profilePage.benefits.diamond.item6') }}</li>
                  </ul>
                </div>
              </div>

              <div class="progress-section">
                <h4>{{ $t('profilePage.benefits.nextLevelTitle') }}</h4>
                <el-progress 
                  :percentage="Math.min((userStats.totalSpent / 200) * 100, 100)" 
                  :color="getMemberLevel.color"
                  :stroke-width="20"
                  :text-inside="true"
                />
                <p class="progress-text">
                  {{ t('profilePage.benefits.nextLevelText', { amount: Math.max(200 - userStats.totalSpent, 0).toFixed(2) }) }}
                </p>
              </div>
            </div>
          </el-tab-pane>

          <!-- 送餐地址 -->
          <el-tab-pane :label="$t('profilePage.tabs.addresses')" name="addresses">
            <div class="tab-content">
              <div class="section-header">
                <h3>{{ $t('profilePage.sections.addresses') }}</h3>
                <el-button 
                  type="primary" 
                  :icon="Plus" 
                  @click="openAddAddressDialog"
                >
                  {{ $t('profilePage.actions.addAddress') }}
                </el-button>
              </div>

              <div v-if="addressList.length === 0" class="empty-state">
                <el-icon :size="80" color="#D0D0D0"><Location /></el-icon>
                <p>{{ $t('profilePage.address.empty') }}</p>
                <el-button type="primary" @click="openAddAddressDialog">{{ $t('profilePage.actions.addFirstAddress') }}</el-button>
              </div>

              <div v-else class="address-list">
                <div 
                  v-for="addr in addressList" 
                  :key="addr.id"
                  class="address-card"
                  :class="{ 'is-default': addr.isDefault }"
                >
                  <div class="address-header">
                    <div class="address-name">
                      <span class="receiver-name">{{ addr.receiverName }}</span>
                      <span class="receiver-phone">{{ addr.receiverPhone }}</span>
                      <el-tag v-if="addr.isDefault" type="warning" size="small" class="default-tag">
                        <el-icon><Star /></el-icon>
                        {{ $t('profilePage.address.default') }}
                      </el-tag>
                    </div>
                    <div class="address-actions">
                      <el-button 
                        link 
                        type="primary" 
                        :icon="Edit"
                        @click="handleEditAddress(addr)"
                      >
                        {{ $t('common.edit') }}
                      </el-button>
                      <el-button 
                        link 
                        type="danger" 
                        :icon="Delete"
                        @click="handleDeleteAddress(addr.id!)"
                      >
                        {{ $t('common.delete') }}
                      </el-button>
                    </div>
                  </div>
                  <div class="address-detail">
                    <el-icon><Location /></el-icon>
                    <span>{{ addr.address }}</span>
                  </div>
                  <div v-if="!addr.isDefault" class="address-footer">
                    <el-button 
                      link 
                      type="warning"
                      @click="handleSetDefaultAddress(addr.id!)"
                    >
                      {{ $t('profilePage.address.setDefault') }}
                    </el-button>
                  </div>
                </div>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>

    <!-- 地址编辑对话框 -->
    <el-dialog
      v-model="showAddressDialog"
      :title="addressDialogTitle"
      width="500px"
    >
      <el-form :model="addressForm" label-width="90px">
        <el-form-item :label="$t('profilePage.addressDialog.receiverName')" required>
          <el-input 
            v-model="addressForm.receiverName" 
            :placeholder="$t('profilePage.addressDialog.placeholders.receiverName')"
            maxlength="20"
          />
        </el-form-item>
        <el-form-item :label="$t('profilePage.addressDialog.receiverPhone')" required>
          <el-input 
            v-model="addressForm.receiverPhone" 
            :placeholder="$t('profilePage.addressDialog.placeholders.receiverPhone')"
            maxlength="11"
          />
        </el-form-item>
        <el-form-item :label="$t('profilePage.addressDialog.address')" required>
          <el-input 
            v-model="addressForm.address" 
            type="textarea"
            :rows="3"
            :placeholder="$t('profilePage.addressDialog.placeholders.address')"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="">
          <el-checkbox v-model="addressForm.isDefault">
            {{ $t('profilePage.address.setDefault') }}
          </el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddressDialog = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleSaveAddress">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 修改密码对话框 -->
    <el-dialog
      v-model="showPasswordDialog"
      :title="$t('profilePage.passwordDialog.title')"
      width="400px"
    >
      <el-form :model="passwordForm" label-width="100px">
        <el-form-item :label="$t('profilePage.passwordDialog.oldPassword')">
          <el-input 
            v-model="passwordForm.oldPassword" 
            type="password"
            show-password
          />
        </el-form-item>
        <el-form-item :label="$t('profilePage.passwordDialog.newPassword')">
          <el-input 
            v-model="passwordForm.newPassword" 
            type="password"
            show-password
          />
        </el-form-item>
        <el-form-item :label="$t('profilePage.passwordDialog.confirmPassword')">
          <el-input 
            v-model="passwordForm.confirmPassword" 
            type="password"
            show-password
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPasswordDialog = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleChangePassword">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.profile-container {
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

.profile-content {
  max-width: 1400px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 350px 1fr;
  gap: 24px;
}

.user-card {
  background: #FFF;
  border-radius: 20px;
  padding: 30px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  height: fit-content;
  position: sticky;
  top: 20px;
}

.user-header {
  text-align: center;
  padding-bottom: 24px;
  border-bottom: 2px dashed #FFE8CC;
}

.user-avatar {
  background: linear-gradient(135deg, #FF9966 0%, #FF6B35 100%);
  color: #FFF;
  margin-bottom: 16px;
}

.user-info h2 {
  margin: 0 0 12px 0;
  color: #333;
  font-size: 24px;
}

.member-tag {
  font-size: 14px;
  padding: 8px 16px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  margin: 24px 0;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: #FFF5E6;
  border-radius: 12px;
  transition: all 0.3s;
}

.stat-item:hover {
  transform: translateY(-4px);
  box-shadow: 0 4px 12px rgba(255, 107, 53, 0.2);
}

.stat-icon {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #FFF;
  border-radius: 12px;
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 20px;
  font-weight: bold;
  color: #333;
  margin-bottom: 4px;
}

.stat-label {
  font-size: 12px;
  color: #999;
}

.quick-actions {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.action-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
  font-weight: bold;
  border-radius: 12px;
}

.content-area {
  background: #FFF;
  border-radius: 20px;
  padding: 30px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}

.profile-tabs :deep(.el-tabs__nav-wrap::after) {
  background: #FFE8CC;
}

.profile-tabs :deep(.el-tabs__item) {
  font-size: 16px;
  font-weight: bold;
  color: #999;
}

.profile-tabs :deep(.el-tabs__item.is-active) {
  color: #FF6B35;
}

.profile-tabs :deep(.el-tabs__active-bar) {
  background: linear-gradient(90deg, #FF9966 0%, #FF6B35 100%);
  height: 3px;
}

.tab-content {
  padding: 20px 0;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.section-header h3 {
  margin: 0;
  font-size: 20px;
  color: #FF6B35;
}

.section-divider {
  height: 2px;
  background: linear-gradient(90deg, transparent 0%, #FFE8CC 50%, transparent 100%);
  margin: 32px 0;
}

.profile-form {
  max-width: 600px;
}

.change-password-btn {
  margin-top: 16px;
}

/* 地址列表 */
.address-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
  gap: 20px;
}

.address-card {
  background: #FFF;
  border: 2px solid #F0F0F0;
  border-radius: 12px;
  padding: 20px;
  transition: all 0.3s;
  cursor: pointer;
}

.address-card:hover {
  border-color: #FF9966;
  box-shadow: 0 4px 12px rgba(255, 153, 102, 0.15);
  transform: translateY(-2px);
}

.address-card.is-default {
  border-color: #FF6B35;
  background: linear-gradient(135deg, #FFF5E6 0%, #FFFFFF 100%);
}

.address-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}

.address-name {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.receiver-name {
  font-size: 16px;
  font-weight: bold;
  color: #333;
}

.receiver-phone {
  font-size: 14px;
  color: #666;
}

.default-tag {
  margin-top: 6px;
  width: fit-content;
}

.default-tag .el-icon {
  margin-right: 4px;
}

.address-actions {
  display: flex;
  gap: 8px;
}

.address-detail {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px;
  background: #F9F9F9;
  border-radius: 8px;
  margin-bottom: 12px;
  font-size: 14px;
  color: #666;
  line-height: 1.6;
}

.address-detail .el-icon {
  color: #FF6B35;
  margin-top: 2px;
  flex-shrink: 0;
}

.address-footer {
  padding-top: 12px;
  border-top: 1px dashed #E0E0E0;
  text-align: right;
}

.empty-state {
  text-align: center;
  padding: 80px 20px;
}

.empty-state p {
  font-size: 16px;
  color: #999;
  margin: 20px 0;
}

.orders-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.order-card {
  padding: 20px;
  background: #FFF5E6;
  border-radius: 12px;
  border: 2px solid transparent;
  transition: all 0.3s;
  cursor: pointer;
}

.order-card:hover {
  border-color: #FF9966;
  box-shadow: 0 4px 12px rgba(255, 107, 53, 0.2);
  transform: translateY(-2px);
}

.order-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px dashed #FFE8CC;
}

.order-no {
  font-weight: bold;
  color: #666;
}

.order-body {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 12px;
}

.order-info {
  flex: 1;
}

.order-info p {
  margin: 8px 0;
  color: #666;
  font-size: 14px;
}

.order-address {
  color: #999;
}

.order-amount {
  text-align: right;
}

.amount-label {
  font-size: 12px;
  color: #999;
  margin-bottom: 4px;
}

.amount-value {
  font-size: 24px;
  font-weight: bold;
  color: #FF6B35;
}

.order-footer {
  text-align: right;
}

.order-time {
  font-size: 12px;
  color: #999;
}

.benefits-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 20px;
  margin-bottom: 32px;
}

.benefit-card {
  padding: 24px;
  background: #FFF5E6;
  border-radius: 16px;
  border: 3px solid #FFE8CC;
  transition: all 0.3s;
}

.benefit-card:hover {
  transform: translateY(-8px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
}

.benefit-card.silver {
  border-color: #909399;
}

.benefit-card.gold {
  border-color: #E6A23C;
  background: linear-gradient(135deg, #FFF5E6 0%, #FFF0D6 100%);
}

.benefit-card.diamond {
  border-color: #409EFF;
  background: linear-gradient(135deg, #F0F9FF 0%, #E6F7FF 100%);
}

.benefit-icon {
  font-size: 48px;
  text-align: center;
  margin-bottom: 16px;
}

.benefit-card h4 {
  text-align: center;
  margin: 0 0 8px 0;
  font-size: 20px;
  color: #333;
}

.benefit-requirement {
  text-align: center;
  color: #999;
  font-size: 14px;
  margin-bottom: 16px;
}

.benefit-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.benefit-list li {
  padding: 8px 0;
  color: #666;
  font-size: 14px;
  border-bottom: 1px dashed #FFE8CC;
}

.benefit-list li:last-child {
  border-bottom: none;
}

.progress-section {
  padding: 24px;
  background: #FFF5E6;
  border-radius: 16px;
}

.progress-section h4 {
  margin: 0 0 16px 0;
  color: #FF6B35;
  font-size: 18px;
}

.progress-text {
  margin: 12px 0 0 0;
  text-align: center;
  color: #666;
  font-size: 14px;
}

@media (max-width: 1024px) {
  .profile-content {
    grid-template-columns: 1fr;
  }

  .user-card {
    position: static;
  }
}
</style>

