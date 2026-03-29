import request from '@/utils/request'

export interface Address {
  id?: number
  userId?: number
  receiverName: string
  receiverPhone: string
  address: string
  isDefault: boolean
  createdAt?: string
  updatedAt?: string
}

// 获取地址列表
export function getAddressList() {
  return request({
    url: '/addresses',
    method: 'get'
  })
}

// 获取地址详情
export function getAddressDetail(id: number) {
  return request({
    url: `/addresses/${id}`,
    method: 'get'
  })
}

// 添加地址
export function addAddress(data: Address) {
  return request({
    url: '/addresses',
    method: 'post',
    data
  })
}

// 更新地址
export function updateAddress(id: number, data: Address) {
  return request({
    url: `/addresses/${id}`,
    method: 'put',
    data
  })
}

// 删除地址
export function deleteAddress(id: number) {
  return request({
    url: `/addresses/${id}`,
    method: 'delete'
  })
}

// 设置默认地址
export function setDefaultAddress(id: number) {
  return request({
    url: `/addresses/${id}/default`,
    method: 'put'
  })
}

