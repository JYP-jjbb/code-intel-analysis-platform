import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useDeliveryStore = defineStore('delivery', () => {
  // 配送模式：immediate(立即配送) 或 scheduled(预约配送)
  const deliveryMode = ref<'immediate' | 'scheduled'>('immediate')
  
  // 选择的餐段
  const mealPeriod = ref<string>('')
  
  // 选择的具体时间
  const selectedTime = ref<string>('')

  // 餐段时间段配置
  const timeSlots = {
    '早餐': ['07:00-07:30', '07:30-08:00', '08:00-08:30', '08:30-09:00'],
    '午餐': ['11:00-11:30', '11:30-12:00', '12:00-12:30', '12:30-13:00', '13:00-13:30'],
    '晚餐': ['17:00-17:30', '17:30-18:00', '18:00-18:30', '18:30-19:00', '19:00-19:30', '19:30-20:00'],
    '夜宵': ['20:00-20:30', '20:30-21:00', '21:00-21:30', '21:30-22:00']
  }

  // 当前时间显示
  const currentTimeDisplay = computed(() => {
    const now = new Date()
    return now.toLocaleString('zh-CN', { 
      month: '2-digit', 
      day: '2-digit', 
      hour: '2-digit', 
      minute: '2-digit' 
    })
  })

  // 预计送达时间
  const estimatedDeliveryTime = computed(() => {
    if (deliveryMode.value === 'scheduled' && selectedTime.value) {
      // 预约配送：显示选择的时间
      return {
        display: selectedTime.value,
        text: `预计 ${selectedTime.value} 送达`
      }
    } else {
      // 立即配送：当前时间 + 30-45分钟
      const now = new Date()
      const deliveryMinutes = Math.floor(Math.random() * 16) + 30 // 30-45分钟
      const deliveryTime = new Date(now.getTime() + deliveryMinutes * 60 * 1000)
      
      const timeStr = deliveryTime.toLocaleTimeString('zh-CN', { 
        hour: '2-digit', 
        minute: '2-digit' 
      })
      
      return {
        display: timeStr,
        text: `预计 ${timeStr} 送达`
      }
    }
  })

  // 根据餐段获取时间段选项
  const getTimeSlotsByMealPeriod = (period: string) => {
    return timeSlots[period as keyof typeof timeSlots] || []
  }

  // 设置配送模式
  const setDeliveryMode = (mode: 'immediate' | 'scheduled') => {
    deliveryMode.value = mode
    if (mode === 'immediate') {
      // 切换到立即配送时，清空预约信息
      mealPeriod.value = ''
      selectedTime.value = ''
    }
  }

  // 设置餐段
  const setMealPeriod = (period: string) => {
    mealPeriod.value = period
    selectedTime.value = '' // 切换餐段时清空已选时间
  }

  // 设置配送时间
  const setSelectedTime = (time: string) => {
    selectedTime.value = time
  }

  // 重置配送信息
  const reset = () => {
    deliveryMode.value = 'immediate'
    mealPeriod.value = ''
    selectedTime.value = ''
  }

  return {
    deliveryMode,
    mealPeriod,
    selectedTime,
    currentTimeDisplay,
    estimatedDeliveryTime,
    getTimeSlotsByMealPeriod,
    setDeliveryMode,
    setMealPeriod,
    setSelectedTime,
    reset
  }
})
