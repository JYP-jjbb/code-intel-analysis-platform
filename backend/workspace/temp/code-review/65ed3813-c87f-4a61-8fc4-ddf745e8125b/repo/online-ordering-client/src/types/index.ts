// 通用响应结构
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

// 用户信息
export interface UserInfo {
  id: number
  username: string
  role: string
  phone: string
  email: string
  avatar: string
  address?: string
  defaultAddress?: string
  defaultReceiverName?: string
  defaultReceiverPhone?: string
  level?: string
  progress?: number
  nextLevelAmount?: number
  benefits?: string[]
  totalOrders?: number
  totalSpent?: number
  memberDays?: number
  favoriteDishes?: number
}

// 登录响应
export interface LoginResponse {
  token: string
  user: UserInfo
}

// 分类
export interface Category {
  id: number
  name: string
  sort: number
  description: string
}

// 菜品
export interface Dish {
  id: number
  name: string
  price: number
  categoryId: number
  description: string
  imageUrl: string
  stock: number
  sales: number
  status: number
  categoryName?: string
}

// 菜品详情（扩展信息）
export interface DishDetail extends Dish {
  ingredients?: string[]  // 食材
  allergens?: string[]    // 过敏原
  calories?: number       // 卡路里
  servingSize?: string    // 份量
  cookingTime?: string    // 烹饪时间
  spicyLevel?: number     // 辣度等级 0-5
  tags?: string[]         // 标签
  nutritionFacts?: {      // 营养成分
    protein?: number
    fat?: number
    carbs?: number
  }
}

// 购物车项
export interface CartItem {
  id: number
  userId: number
  dishId: number
  quantity: number
  price: number
  subtotal: number
  dishName: string
  imageUrl: string
  stock: number
}

// 订单项
export interface OrderItem {
  id: number
  dishId: number
  dishName: string
  price: number
  quantity: number
  subtotal: number
  imageUrl: string
}

// 订单摘要
export interface OrderSummary {
  id: number
  orderNo: string
  totalAmount: number
  status: number
  statusDesc: string
  createTime: string
  receiverName: string
  receiverPhone: string
  address: string
  items: OrderItem[]
  itemCount?: number
}

// 订单详情
export interface OrderDetail {
  id: number
  orderNo: string
  userId: number
  totalAmount: number
  status: number
  statusDesc: string
  address: string
  receiverName: string
  receiverPhone: string
  remark: string
  paymentMethod: string
  createTime: string
  payTime?: string
  deliveryTime?: string
  estimatedDeliveryTime?: string
  items: OrderItem[]
}

// 创建订单参数
export interface OrderSubmitParams {
  address: string
  receiverName: string
  receiverPhone: string
  remark?: string
  paymentMethod: string
  deliveryTime?: string
  saveAsDefault?: boolean
}








