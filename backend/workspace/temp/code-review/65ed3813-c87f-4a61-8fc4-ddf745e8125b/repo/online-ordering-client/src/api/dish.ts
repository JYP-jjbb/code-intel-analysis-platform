import request from '@/utils/request'
import type { ApiResponse, Category, Dish } from '@/types'

// 获取启用分类
export const getActiveCategories = () => {
  return request.get<any, ApiResponse<Category[]>>('/categories/active')
}

// 获取菜品列表
export const getDishList = (params: { pageNum?: number; pageSize?: number; categoryId?: number; keyword?: string }) => {
  return request.get<any, ApiResponse<{ records: Dish[]; total: number }>>('/dishes', { params })
}

// 获取菜品详情
export const getDishDetail = (id: number) => {
  return request.get<any, ApiResponse<Dish>>(`/dishes/${id}`)
}

// 新增菜品
export const addDish = (data: any) => {
  return request.post<any, ApiResponse<void>>('/dishes', data)
}

// 更新菜品
export const updateDish = (id: number, data: any) => {
  return request.put<any, ApiResponse<void>>(`/dishes/${id}`, data)
}
