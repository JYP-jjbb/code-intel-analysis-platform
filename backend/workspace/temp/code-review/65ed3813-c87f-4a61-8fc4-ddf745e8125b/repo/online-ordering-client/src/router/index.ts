import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/store/user'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'Welcome',
    component: () => import('@/views/WelcomeView.vue'),
    meta: { titleKey: 'route.welcome' }
  },
  {
    path: '/categories',
    name: 'Categories',
    component: () => import('@/views/HomeView.vue'),
    meta: { titleKey: 'route.categories' }
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { titleKey: 'route.login' }
  },
  {
    path: '/dish/:id',
    name: 'DishDetail',
    component: () => import('@/views/DishDetailView.vue'),
    meta: { titleKey: 'route.dishDetail' }
  },
  {
    path: '/cart',
    name: 'Cart',
    component: () => import('@/views/CartView.vue'),
    meta: { titleKey: 'route.cart', requiresAuth: true }
  },
  {
    path: '/order/confirm',
    name: 'OrderConfirm',
    component: () => import('@/views/OrderConfirmView.vue'),
    meta: { titleKey: 'route.orderConfirm', requiresAuth: true }
  },
  {
    path: '/orders',
    name: 'OrderList',
    component: () => import('@/views/OrderListView.vue'),
    meta: { titleKey: 'route.orders', requiresAuth: true }
  },
  {
    path: '/order/:id',
    name: 'OrderDetail',
    component: () => import('@/views/OrderDetailView.vue'),
    meta: { titleKey: 'route.orderDetail', requiresAuth: true }
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('@/views/ProfileView.vue'),
    meta: { titleKey: 'route.profile', requiresAuth: true }
  },
  {
    path: '/customer-service',
    name: 'CustomerService',
    component: () => import('@/views/CustomerServiceView.vue'),
    meta: { titleKey: 'route.customerService', requiresAuth: true }
  },
  // Admin Routes
  {
    path: '/admin',
    component: () => import('@/views/admin/AdminLayout.vue'),
    meta: { titleKey: 'route.admin', requiresAuth: true, requiresAdmin: true },
    children: [
      {
        path: '',
        redirect: '/admin/dishes'
      },
      {
        path: 'dishes',
        name: 'DishManage',
        component: () => import('@/views/admin/DishManageView.vue'),
        meta: { titleKey: 'admin.dishManage' }
      },
      {
        path: 'orders',
        name: 'OrderManage',
        component: () => import('@/views/admin/OrderManageView.vue'),
        meta: { titleKey: 'admin.orderManage' }
      },
      {
        path: 'customer-service',
        name: 'AdminCustomerService',
        component: () => import('@/views/admin/AdminCustomerServiceView.vue'),
        meta: { titleKey: 'admin.csWorkbench' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  const isAuthenticated = !!userStore.token
  const isAdmin = userStore.userInfo?.role === 'admin'

  if (to.meta.requiresAuth && !isAuthenticated) {
    next({ name: 'Login', query: { redirect: to.fullPath } })
  } else if (to.meta.requiresAdmin && !isAdmin) {
     // Simple check, real check should be on backend too
     // If not admin, maybe redirect to home
     next('/') 
  } else {
    next()
  }
})

export default router
