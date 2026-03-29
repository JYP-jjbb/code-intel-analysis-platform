import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import router from '@/router'

// 创建 axios 实例
const service = axios.create({
  baseURL: '/api', // 配合 vite.config.ts 的 proxy
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
service.interceptors.request.use(
  (config) => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
service.interceptors.response.use(
  (response) => {
    const res = response.data
    // 假设后端成功状态码是 200
    if (res.code === 200) {
      return res
    } else {
      ElMessage.error(res.message || '系统错误')
      // 可以在这里处理特定错误码，比如 token 失效
      if (res.code === 401) {
        const userStore = useUserStore()
        userStore.logout()
        router.push('/login')
      }
      return Promise.reject(new Error(res.message || 'Error'))
    }
  },
  (error) => {
    console.error('Request Error:', error)
    console.error('Error Response:', error.response)
    
    let msg = '请求失败'
    
    if (error.response) {
      // 优先使用后端返回的错误信息
      if (error.response.data && error.response.data.message) {
        msg = error.response.data.message
      } else {
        switch (error.response.status) {
          case 401: 
            msg = '未授权，请登录'
            const userStore = useUserStore()
            userStore.logout()
            router.push('/login')
            break
          case 403: msg = '拒绝访问'; break
          case 404: msg = '请求地址出错'; break
          case 500: msg = '服务器内部错误'; break
          default: msg = error.response.data?.message || '网络连接错误'
        }
      }
    } else if (error.request) {
      msg = '网络连接失败，请检查网络'
    } else {
      msg = error.message || '请求配置错误'
    }
    
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export default service












