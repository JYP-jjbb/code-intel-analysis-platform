<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { getMessages, uploadChatImage, getUnreadCount, type CustomerServiceMessage } from '@/api/customerService'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  ChatDotRound,
  Picture,
  Camera,
  Close,
  ArrowLeft,
  Plus
} from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()
const { t } = useI18n()

// WebSocket连接
let ws: WebSocket | null = null
const connected = ref(false)
const unreadCount = ref(0)  // 未读消息数

// 消息列表
const messages = ref<CustomerServiceMessage[]>([])
const messageInput = ref('')
const messagesContainer = ref<HTMLElement | null>(null)

// 会话ID
const sessionId = computed(() => {
  return `session_${userStore.userInfo?.id}_admin`
})

// 文件上传相关
const uploading = ref(false)
const showImageActions = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)
const cameraInputRef = ref<HTMLInputElement | null>(null)

// 初始化WebSocket
const initWebSocket = () => {
  const wsUrl = `ws://localhost:8080/ws/customer-service`
  ws = new WebSocket(wsUrl)

  ws.onopen = () => {
    console.log('WebSocket连接成功')
    connected.value = true
    
    // 注册用户
    ws?.send(JSON.stringify({
      type: 'register',
      userId: userStore.userInfo?.id
    }))
    
    // 加载历史消息
    loadMessages()
  }

  ws.onmessage = (event) => {
    console.log('收到消息:', event.data)
    try {
      const data = JSON.parse(event.data)
      
      if (data.type === 'system') {
        console.log('系统消息:', data.content)
        // 系统消息不需要添加到聊天列表
      } else if (data.type === 'message') {
        // 只有当消息的发送者不是当前用户时,才添加到列表
        // 避免收到自己发送的消息时重复显示
        if (data.senderId !== userStore.userInfo?.id) {
          messages.value.push({
            id: Date.now(),
            sessionId: data.sessionId,
            senderId: data.senderId,
            senderName: data.senderName,
            senderRole: data.senderRole,
            receiverId: data.receiverId,
            messageType: data.messageType,
            content: data.content,
            isRead: 0,
            createTime: new Date().toISOString()
          })
          scrollToBottom()
          
          // 收到新消息，增加未读数（如果页面不在焦点）
          if (document.hidden) {
            unreadCount.value++
          } else {
            // 页面在焦点，立即标记为已读
            loadMessages()
          }
        }
      }
    } catch (e) {
      console.error('解析消息失败:', e)
    }
  }

  ws.onerror = (error) => {
    console.error('WebSocket错误:', error)
    ElMessage.error(t('customerService.messages.connectionFailed'))
  }

  ws.onclose = () => {
    console.log('WebSocket连接关闭')
    connected.value = false
  }
}

// 加载历史消息
const loadMessages = async () => {
  try {
    const res = await getMessages(sessionId.value)
    if (res.code === 200) {
      messages.value = res.data
      await nextTick()
      scrollToBottom()
      
      // 加载完消息后，重置未读数
      unreadCount.value = 0
    }
  } catch (error: any) {
    console.error('加载消息失败:', error)
  }
}

// 加载未读消息数
const loadUnreadCount = async () => {
  try {
    const res = await getUnreadCount()
    if (res.code === 200) {
      unreadCount.value = res.data
    }
  } catch (error: any) {
    console.error('加载未读消息数失败:', error)
  }
}

// 发送文本消息
const sendMessage = () => {
  if (!messageInput.value.trim()) {
    return
  }

  if (!ws || ws.readyState !== WebSocket.OPEN) {
    ElMessage.error(t('customerService.messages.connectionLost'))
    return
  }

  const message = {
    type: 'message',
    sessionId: sessionId.value,
    senderId: userStore.userInfo?.id,
    senderName: userStore.userInfo?.username,
    senderRole: 'user',
    receiverId: 1, // 管理员ID，实际应该动态获取
    messageType: 'text',
    content: messageInput.value
  }

  // 发送到服务器
  ws.send(JSON.stringify(message))

  // 添加到本地列表
  messages.value.push({
    ...message,
    id: Date.now(),
    createTime: new Date().toISOString()
  })

  messageInput.value = ''
  scrollToBottom()
}

// 打开图片选择器
const openFileSelector = () => {
  fileInputRef.value?.click()
  showImageActions.value = false
}

// 打开相机
const openCamera = () => {
  cameraInputRef.value?.click()
  showImageActions.value = false
}

// 处理图片上传
const handleImageUpload = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  
  if (!file) return

  // 验证文件类型
  if (!file.type.startsWith('image/')) {
    ElMessage.error(t('customerService.messages.invalidFileType'))
    return
  }

  // 验证文件大小（最大10MB）
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.error(t('customerService.messages.fileSizeLimit'))
    return
  }

  uploading.value = true

  try {
    // 上传图片
    const res = await uploadChatImage(file)
    
    if (res.code === 200) {
      const imageUrl = res.data

      // 发送图片消息
      const message = {
        type: 'message',
        sessionId: sessionId.value,
        senderId: userStore.userInfo?.id,
        senderName: userStore.userInfo?.username,
        senderRole: 'user',
        receiverId: 1,
        messageType: 'image',
        content: imageUrl
      }

      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify(message))
      }

      // 添加到本地列表
      messages.value.push({
        ...message,
        id: Date.now(),
        createTime: new Date().toISOString()
      })

      scrollToBottom()
      ElMessage.success(t('customerService.messages.imageSent'))
    }
  } catch (error: any) {
    console.error('图片上传失败:', error)
    ElMessage.error(error.response?.data?.message || t('common.empty'))
  } finally {
    uploading.value = false
    // 清空input
    input.value = ''
  }
}

// 滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

// 返回上一页
const goBack = () => {
  router.back()
}

// 格式化时间
const formatTime = (time: string) => {
  const date = new Date(time)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  
  // 小于1分钟显示"刚刚"
  if (diff < 60 * 1000) {
    return '刚刚'
  }
  
  // 小于1小时显示"X分钟前"
  if (diff < 60 * 60 * 1000) {
    return `${Math.floor(diff / 60 / 1000)}分钟前`
  }
  
  // 今天显示"HH:mm"
  if (date.toDateString() === now.toDateString()) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  
  // 显示"MM-DD HH:mm"
  return date.toLocaleString('zh-CN', { 
    month: '2-digit', 
    day: '2-digit', 
    hour: '2-digit', 
    minute: '2-digit' 
  })
}

onMounted(() => {
  initWebSocket()
  loadUnreadCount()
  
  // 监听页面可见性变化
  document.addEventListener('visibilitychange', () => {
    if (!document.hidden) {
      // 页面变为可见，标记消息为已读
      loadMessages()
    }
  })
})

onUnmounted(() => {
  if (ws) {
    ws.close()
  }
})
</script>

<template>
  <div class="customer-service-container">
    <!-- 顶部导航栏 -->
    <div class="chat-header">
      <el-button 
        :icon="ArrowLeft" 
        circle 
        @click="goBack"
        class="back-btn"
      />
      <div class="header-info">
        <div class="header-title">
          <el-icon class="title-icon"><ChatDotRound /></el-icon>
          <span>{{ t('customerService.title') }}</span>
          <el-badge 
            :value="unreadCount" 
            :max="99"
            v-if="unreadCount > 0"
            class="header-badge"
          />
        </div>
        <div class="header-status">
          <span class="status-dot" :class="{ 'online': connected }"></span>
          <span class="status-text">{{ connected ? t('customerService.online') : t('customerService.offline') }}</span>
        </div>
      </div>
      <div class="header-placeholder"></div>
    </div>

    <!-- 消息列表 -->
    <div class="messages-container" ref="messagesContainer">
      <div 
        v-for="msg in messages" 
        :key="msg.id"
        class="message-item"
        :class="{ 'message-self': msg.senderId === userStore.userInfo?.id }"
      >
        <div class="message-avatar">
          <el-avatar :size="40">
            {{ msg.senderName?.charAt(0) }}
          </el-avatar>
        </div>
        <div class="message-content-wrapper">
          <div class="message-name">{{ msg.senderName }}</div>
          <div class="message-bubble">
            <!-- 文本消息 -->
            <div v-if="msg.messageType === 'text'" class="message-text">
              {{ msg.content }}
            </div>
                  <!-- 图片消息 -->
                  <div v-else-if="msg.messageType === 'image'" class="message-image">
                    <el-image 
                      :src="`http://localhost:8080${msg.content}`" 
                      fit="cover"
                      :preview-src-list="[`http://localhost:8080${msg.content}`]"
                      preview-teleported
                      style="max-width: 200px; max-height: 200px; border-radius: 8px; cursor: pointer;"
                    >
                      <template #error>
                        <div class="image-error">
                          <el-icon><Picture /></el-icon>
                          <span>加载失败</span>
                        </div>
                      </template>
                    </el-image>
                  </div>
          </div>
          <div class="message-time">{{ formatTime(msg.createTime!) }}</div>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-if="messages.length === 0" class="empty-messages">
        <el-icon :size="80" color="#D0D0D0"><ChatDotRound /></el-icon>
        <p>{{ t('customerService.emptyMessage') }}</p>
      </div>
    </div>

    <!-- 输入框 -->
    <div class="input-container">
      <div class="input-toolbar">
        <el-button 
          :icon="Plus" 
          circle 
          @click="showImageActions = !showImageActions"
          class="toolbar-btn"
        />
        
        <!-- 图片操作面板 -->
        <transition name="fade">
          <div v-show="showImageActions" class="image-actions">
            <el-button 
              :icon="Picture" 
              @click="openFileSelector"
              :loading="uploading"
            >
              相册
            </el-button>
            <el-button 
              :icon="Camera" 
              @click="openCamera"
              :loading="uploading"
            >
              拍照
            </el-button>
          </div>
        </transition>
      </div>

      <div class="input-wrapper">
        <el-input
          v-model="messageInput"
          type="textarea"
          :rows="1"
          :autosize="{ minRows: 1, maxRows: 4 }"
          placeholder="输入消息..."
          @keydown.enter.exact.prevent="sendMessage"
          class="message-input"
        />
        <el-button 
          type="primary" 
          @click="sendMessage"
          :disabled="!messageInput.trim() || !connected"
          class="send-btn"
        >
          发送
        </el-button>
      </div>

      <!-- 隐藏的文件输入 -->
      <input 
        ref="fileInputRef"
        type="file" 
        accept="image/*"
        style="display: none"
        @change="handleImageUpload"
      />
      
      <!-- 隐藏的相机输入 - 使用camera捕获 -->
      <input 
        ref="cameraInputRef"
        type="file" 
        accept="image/*"
        capture="user"
        style="display: none"
        @change="handleImageUpload"
      />
    </div>
  </div>
</template>

<style scoped>
.customer-service-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #F5F5F5;
}

/* 顶部导航栏 */
.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 20px;
  background: #FFF;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  z-index: 10;
}

.back-btn {
  border: 1px solid #E0E0E0;
}

.header-info {
  flex: 1;
  text-align: center;
}

.header-title {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 18px;
  font-weight: bold;
  color: #333;
  margin-bottom: 4px;
  position: relative;
}

.header-badge {
  margin-left: 8px;
}

.title-icon {
  font-size: 20px;
  color: #FF6B35;
}

.header-status {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 12px;
  color: #999;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #CCC;
  transition: background 0.3s;
}

.status-dot.online {
  background: #67C23A;
}

.header-placeholder {
  width: 40px;
}

/* 消息列表 */
.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: #F5F5F5;
}

.message-item {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  animation: fadeInUp 0.3s ease;
}

.message-self {
  flex-direction: row-reverse;
}

.message-avatar {
  flex-shrink: 0;
}

.message-content-wrapper {
  max-width: 60%;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.message-self .message-content-wrapper {
  align-items: flex-end;
}

.message-name {
  font-size: 12px;
  color: #999;
  padding: 0 8px;
}

.message-bubble {
  background: #FFF;
  padding: 12px 16px;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  word-break: break-word;
}

.message-self .message-bubble {
  background: linear-gradient(135deg, #FF9966 0%, #FF6B35 100%);
  color: #FFF;
}

.message-text {
  line-height: 1.6;
  font-size: 15px;
}

.message-image {
  cursor: pointer;
  padding: 0;
}

.message-image :deep(.el-image) {
  display: block;
  border-radius: 8px;
  overflow: hidden;
}

.image-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 200px;
  height: 200px;
  background: #F5F5F5;
  color: #999;
  border-radius: 8px;
}

.message-time {
  font-size: 11px;
  color: #CCC;
  padding: 0 8px;
}

.empty-messages {
  text-align: center;
  padding: 100px 20px;
}

.empty-messages p {
  margin: 20px 0;
  color: #999;
  font-size: 16px;
}

/* 输入框 */
.input-container {
  background: #FFF;
  border-top: 1px solid #E0E0E0;
  padding: 12px 20px;
}

.input-toolbar {
  position: relative;
  margin-bottom: 12px;
}

.toolbar-btn {
  border: 1px solid #E0E0E0;
}

.image-actions {
  position: absolute;
  bottom: 50px;
  left: 0;
  display: flex;
  gap: 12px;
  background: #FFF;
  padding: 12px;
  border-radius: 12px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}

.input-wrapper {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.message-input {
  flex: 1;
}

.message-input :deep(.el-textarea__inner) {
  border-radius: 20px;
  padding: 10px 16px;
  font-size: 15px;
}

.send-btn {
  height: 40px;
  padding: 0 24px;
  border-radius: 20px;
  background: linear-gradient(135deg, #FF9966 0%, #FF6B35 100%);
  border: none;
  font-weight: bold;
}

.send-btn:disabled {
  opacity: 0.5;
}

/* 动画 */
@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.fade-enter-active, .fade-leave-active {
  transition: opacity 0.3s, transform 0.3s;
}

.fade-enter-from, .fade-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
</style>

