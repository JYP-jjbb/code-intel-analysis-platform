<script setup lang="ts">
import { RouterView, useRoute, useRouter } from 'vue-router'
import { computed } from 'vue'
import { useUserStore } from '@/store/user'
import { Food, List, House, SwitchButton, User, ChatDotRound } from '@element-plus/icons-vue'
import LanguageSwitcher from '@/components/LanguageSwitcher.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const activeMenu = computed(() => route.path)

const handleLogout = () => {
  userStore.logout()
  router.push('/login')
}
</script>

<template>
  <div class="admin-layout">
    <el-container>
      <!-- 侧边栏 -->
      <el-aside width="240px">
        <div class="logo">
          <img src="/logo.png" alt="Logo" class="logo-img" />
          <div class="logo-text">
            <div class="logo-title">{{ $t('admin.title') }}</div>
            <div class="logo-subtitle">{{ $t('admin.subtitle') }}</div>
          </div>
        </div>

        <div class="language-row">
          <LanguageSwitcher />
        </div>
        
        <!-- 用户信息 -->
        <div class="user-info">
          <el-avatar :size="50" :icon="User" class="user-avatar" />
          <div class="user-details">
            <div class="user-name">{{ userStore.userInfo?.username }}</div>
            <el-tag type="danger" size="small" effect="dark">{{ $t('admin.adminTag') }}</el-tag>
          </div>
        </div>

        <!-- 菜单 -->
        <el-menu
          :default-active="activeMenu"
          router
          class="admin-menu"
        >
          <el-menu-item index="/admin/dishes">
            <el-icon><Food /></el-icon>
            <span>{{ $t('admin.dishManage') }}</span>
          </el-menu-item>
          <el-menu-item index="/admin/orders">
            <el-icon><List /></el-icon>
            <span>{{ $t('admin.orderManage') }}</span>
          </el-menu-item>
          <el-menu-item index="/admin/customer-service">
            <el-icon><ChatDotRound /></el-icon>
            <span>{{ $t('admin.csWorkbench') }}</span>
          </el-menu-item>
          <el-menu-item index="/categories" class="back-home">
            <el-icon><House /></el-icon>
            <span>{{ $t('admin.backHome') }}</span>
          </el-menu-item>
        </el-menu>

        <!-- 退出登录 -->
        <div class="logout-section">
          <el-button 
            type="danger" 
            :icon="SwitchButton" 
            @click="handleLogout"
            class="logout-btn"
            plain
          >
            {{ $t('admin.logout') }}
          </el-button>
        </div>
      </el-aside>

      <!-- 主内容区 -->
      <el-main>
        <RouterView />
      </el-main>
    </el-container>
  </div>
</template>

<style scoped>
.admin-layout {
  height: 100vh;
  display: flex;
  overflow: hidden;
}

.el-container {
  height: 100%;
  width: 100%;
}

.el-aside {
  background: linear-gradient(180deg, #FFF 0%, #FFF5E6 100%);
  border-right: 2px solid #FFE8CC;
  display: flex;
  flex-direction: column;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.05);
}

.logo {
  height: 80px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 20px;
  border-bottom: 2px solid #FFE8CC;
  background: linear-gradient(135deg, #FF9966 0%, #FF6B35 100%);
}

.logo-img {
  height: 50px;
  width: 50px;
  object-fit: cover;
  border-radius: 50%;
  border: 3px solid #FFF;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
}

.logo-text {
  color: #FFF;
}

.logo-title {
  font-size: 20px;
  font-weight: bold;
  text-shadow: 1px 1px 2px rgba(0, 0, 0, 0.2);
}

.logo-subtitle {
  font-size: 12px;
  opacity: 0.9;
}

.language-row {
  display: flex;
  justify-content: center;
  padding: 12px 16px;
  border-bottom: 1px solid rgba(255, 232, 204, 0.9);
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px;
  border-bottom: 1px solid #FFE8CC;
  background: #FFF;
}

.user-avatar {
  background: linear-gradient(135deg, #FF9966 0%, #FF6B35 100%);
  color: #FFF;
}

.user-details {
  flex: 1;
}

.user-name {
  font-size: 16px;
  font-weight: bold;
  color: #333;
  margin-bottom: 4px;
}

.admin-menu {
  flex: 1;
  border: none;
  background: transparent;
  padding: 10px;
}

.admin-menu .el-menu-item {
  border-radius: 12px;
  margin: 8px 0;
  font-size: 15px;
  font-weight: 500;
  color: #666;
  transition: all 0.3s;
}

.admin-menu .el-menu-item:hover {
  background: linear-gradient(90deg, #FFE8CC 0%, transparent 100%);
  color: #FF6B35;
}

.admin-menu .el-menu-item.is-active {
  background: linear-gradient(135deg, #FF9966 0%, #FF6B35 100%);
  color: #FFF;
  box-shadow: 0 4px 12px rgba(255, 107, 53, 0.3);
}

.admin-menu .el-menu-item.back-home {
  margin-top: auto;
  border-top: 1px solid #FFE8CC;
  padding-top: 16px;
}

.logout-section {
  padding: 20px;
  border-top: 2px solid #FFE8CC;
}

.logout-btn {
  width: 100%;
  font-weight: bold;
  border-radius: 12px;
  transition: all 0.3s;
}

.logout-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(245, 108, 108, 0.3);
}

.el-main {
  background: linear-gradient(135deg, #FFF5E6 0%, #FFE8CC 100%);
  padding: 0;
  overflow-y: auto;
}

/* 滚动条样式 */
.el-main::-webkit-scrollbar {
  width: 8px;
}

.el-main::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 4px;
}

.el-main::-webkit-scrollbar-thumb {
  background: #FF9966;
  border-radius: 4px;
}

.el-main::-webkit-scrollbar-thumb:hover {
  background: #FF6B35;
}
</style>
