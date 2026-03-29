import request from '@/utils/request'
import type { ApiResponse } from '@/types'

// 客服消息类型
export interface CustomerServiceMessage {
  id?: number
  sessionId: string
  senderId: number
  senderName: string
  senderRole: 'user' | 'admin'
  receiverId?: number
  messageType: 'text' | 'image'
  content: string
  isRead?: number
  createTime?: string
}

// 会话信息类型
export interface SessionInfo {
  sessionId: string
  userId: number
  username: string
  userRole: string
  unreadCount: number  // 未读消息数
}

// 获取会话消息
export const getMessages = (sessionId: string) => {
  return request.get<any, ApiResponse<CustomerServiceMessage[]>>(`/customer-service/messages/${sessionId}`)
}

// 获取历史消息
export const getHistory = () => {
  return request.get<any, ApiResponse<CustomerServiceMessage[]>>('/customer-service/history')
}

// 获取未读消息数量
export const getUnreadCount = () => {
  return request.get<any, ApiResponse<number>>('/customer-service/unread-count')
}

// 获取所有活跃会话（管理员）
export const getActiveSessions = () => {
  return request.get<any, ApiResponse<SessionInfo[]>>('/customer-service/sessions')
}

// 上传聊天图片
export const uploadChatImage = (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<any, ApiResponse<string>>('/upload/chat-image', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

