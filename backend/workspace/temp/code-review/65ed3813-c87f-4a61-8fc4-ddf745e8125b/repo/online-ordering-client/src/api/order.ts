import request from '@/utils/request'
import type { ApiResponse, OrderSummary, OrderDetail, OrderSubmitParams } from '@/types'

// 提交订单
export const submitOrder = (data: OrderSubmitParams) => {
  return request.post<any, ApiResponse<OrderDetail>>('/orders', data)
}

// 获取订单列表
export const getOrderList = (params: { pageNum: number; pageSize: number }) => {
  return request.get<any, ApiResponse<{ records: OrderSummary[]; total: number }>>('/orders', { params })
}

// 获取所有订单列表（管理员）
export const getAdminOrderList = (params: { pageNum: number; pageSize: number }) => {
  return request.get<any, ApiResponse<{ records: OrderSummary[]; total: number }>>('/orders/admin', { params })
}

// 获取订单详情
export const getOrderDetail = (orderId: number) => {
  return request.get<any, ApiResponse<OrderDetail>>(`/orders/${orderId}`)
}

// 取消订单
export const cancelOrder = (orderId: number) => {
  return request.put<any, ApiResponse<void>>(`/orders/${orderId}/cancel`)
}

// 支付订单
export const payOrder = (orderId: number) => {
  return request.put<any, ApiResponse<void>>(`/orders/${orderId}/pay`)
}

// 更新订单状态（管理员）
export const updateOrderStatus = (orderId: number, status: number) => {
  return request.put<any, ApiResponse<void>>(`/orders/admin/${orderId}/status/${status}`)
}
