import request from '@/utils/request'
import type { ApiResponse } from '@/types'

// 支付类型
export type PaymentType = 'wechat' | 'alipay'

// 支付信息
export interface PaymentInfo {
  orderId: number
  orderNo: string
  amount: number
  paymentType: PaymentType
  qrCode: string // Base64 编码的二维码图片
  expireTime: string
}

// 支付状态
export interface PaymentStatus {
  orderId: number
  isPaid: boolean
  payTime?: string
}

//创建支付订单（生成二维码）
export const createPayment = (data: { orderId: number; paymentType: PaymentType }) => {
  return request.post<any, ApiResponse<PaymentInfo>>('/payment/create', data)
}

//查询支付状态
export const checkPaymentStatus = (orderId: number) => {
  return request.get<any, ApiResponse<PaymentStatus>>(`/payment/status/${orderId}`)
}

//模拟支付成功（仅用于测试）
export const simulatePayment = (orderId: number) => {
  return request.post<any, ApiResponse<void>>(`/payment/simulate/${orderId}`)
}

