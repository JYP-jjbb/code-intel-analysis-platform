import request from '@/utils/request'
import type { ApiResponse, CartItem } from '@/types'

// 获取购物车列表
export const getCartList = () => {
  return request.get<any, ApiResponse<CartItem[]>>('/cart')
}

// 添加到购物车
export const addToCart = (data: { dishId: number; quantity: number }) => {
  return request.post<any, ApiResponse<void>>('/cart', data)
}

// 更新数量
export const updateCartItem = (cartItemId: number, quantity: number) => {
  return request.put<any, ApiResponse<void>>(`/cart/${cartItemId}`, null, { params: { quantity } })
}

// 删除购物车项
export const deleteCartItem = (cartItemId: number) => {
  return request.delete<any, ApiResponse<void>>(`/cart/${cartItemId}`)
}

// 清空购物车
export const clearCart = () => {
  return request.delete<any, ApiResponse<void>>('/cart')
}












