import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getCartList, updateCartItem, deleteCartItem, clearCart as clearCartApi } from '@/api/cart'
import type { CartItem } from '@/types'
import { ElMessage } from 'element-plus'
import { i18n } from '@/i18n'

const t = i18n.global.t

export const useCartStore = defineStore('cart', () => {
  const cartItems = ref<CartItem[]>([])
  
  // 别名 items 供组件使用
  const items = computed(() => cartItems.value)
  
  // 计算总数量
  const totalCount = computed(() => {
    return cartItems.value.reduce((sum, item) => sum + item.quantity, 0)
  })

  // 计算总金额
  const totalAmount = computed(() => {
    return cartItems.value.reduce((sum, item) => sum + item.price * item.quantity, 0)
  })

  // 获取购物车列表
  async function fetchCart() {
    try {
      const res = await getCartList()
      cartItems.value = res.data
    } catch (error) {
      console.error('Fetch cart failed', error)
    }
  }

  // 更新数量 (增量更新)
  async function updateQuantity(cartItemId: number, delta: number) {
    const item = cartItems.value.find(i => i.id === cartItemId)
    if (!item) return
    
    const newQuantity = item.quantity + delta
    
    if (newQuantity <= 0) {
      return removeItem(cartItemId)
    }
    
    try {
      await updateCartItem(cartItemId, newQuantity)
      // 重新获取最新数据确保一致性
      await fetchCart()
    } catch (error) {
      console.error('Update cart failed', error)
    }
  }

  // 删除项
  async function removeItem(cartItemId: number) {
    try {
      await deleteCartItem(cartItemId)
      await fetchCart()
      ElMessage.success(t('cart.messages.deleteSuccess'))
    } catch (error) {
      console.error('Remove item failed', error)
    }
  }

  // 清空购物车
  async function clearCart() {
    try {
      await clearCartApi()
      cartItems.value = []
      ElMessage.success(t('cart.messages.clearSuccess'))
    } catch (error) {
      console.error('Clear cart failed', error)
    }
  }

  return {
    cartItems,
    items,
    totalCount,
    totalAmount,
    fetchCart,
    updateQuantity,
    removeItem,
    clearCart
  }
})
