import request from '@/utils/request'
import type { ApiResponse, LoginResponse, UserInfo } from '@/types'

// 获取图形验证码
export const getCaptcha = () => {
  return request.get<any, ApiResponse<{ captchaId: string; captchaImage: string }>>('/auth/captcha')
}

// 发送邮箱验证码
export const sendEmailCode = (data: {
  email: string
  scene: string
  captcha: string
  captchaId: string
}) => {
  return request.post<any, ApiResponse<void>>('/auth/email/code', data)
}

// 注册
export const register = (data: any) => {
  return request.post<any, ApiResponse<void>>('/auth/register', data)
}

// 登录
export const login = (data: any) => {
  return request.post<any, ApiResponse<LoginResponse>>('/auth/login', data)
}

// 获取用户信息
export const getUserProfile = () => {
  return request.get<any, ApiResponse<UserInfo>>('/auth/profile')
}










