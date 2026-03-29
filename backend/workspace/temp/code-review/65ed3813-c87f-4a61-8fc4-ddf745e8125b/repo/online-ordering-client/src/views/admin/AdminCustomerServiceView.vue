<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { getMessages, getActiveSessions, uploadChatImage, type CustomerServiceMessage, type SessionInfo } from '@/api/customerService'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  ChatDotRound,
  Picture,
  Camera,
  ArrowLeft,
  Plus,
  User
} from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()
const { t } = useI18n()

// WebSocket连接
let ws: WebSocket | null = null
const connected = ref(false)

// 会话列表
const sessions = ref<SessionInfo[]>([])
const activeSession = ref<string>('')

// 消息列表
const messages = ref<CustomerServiceMessage[]>([])
const messageInput = ref('')
const messagesContainer = ref<HTMLElement | null>(null)

// 文件上传相关
const uploading = ref(false)
const showImageActions = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)
const cameraInputRef = ref<HTMLInputElement | null>(null)

// 当前选中的用户信息
const currentUserInfo = computed(() => {
  if (!activeSession.value) return null
  const session = sessions.value.find(s => s.sessionId === activeSession.value)
  return session ? {
    userId: session.userId,
    username: session.username
  } : null
})

// 初始化WebSocket
const initWebSocket = () => {
  const wsUrl = `ws://localhost:8080/ws/customer-service`
  ws = new WebSocket(wsUrl)

  ws.onopen = () => {
    console.log('WebSocket连接成功')
    connected.value = true
    
    // 注册管理员
    ws?.send(JSON.stringify({
      type: 'register',
      userId: userStore.userInfo?.id
    }))
    
    // 加载会话列表
    loadSessions()
  }

  ws.onmessage = (event) => {
    console.log('收到消息:', event.data)
    try {
      const data = JSON.parse(event.data)
      
      if (data.type === 'system') {
        console.log('系统消息:', data.content)
        // 系统消息不需要添加到聊天列表
      } else if (data.type === 'message') {
        // 只有当消息的发送者不是当前管理员时,才添加到列表
        // 避免收到自己发送的消息时重复显示
        if (data.senderId !== userStore.userInfo?.id) {
          const msg: CustomerServiceMessage = {
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
          }
          
          // 如果是当前会话的消息,添加到列表
          if (data.sessionId === activeSession.value) {
            messages.value.push(msg)
            scrollToBottom()
          }
          
          // 刷新会话列表
          loadSessions()
        }
      }
    } catch (e) {
      console.error('解析消息失败:', e)
    }
  }

  ws.onerror = (error) => {
    console.error('WebSocket错误:', error)
    ElMessage.error('连接失败，请检查网络')
  }

  ws.onclose = () => {
    console.log('WebSocket连接关闭')
    connected.value = false
  }
}

// 加载会话列表
const loadSessions = async () => {
  try {
    const res = await getActiveSessions()
    if (res.code === 200) {
      sessions.value = res.data
      // 如果还没选择会话且有会话,自动选择第一个
      if (!activeSession.value && sessions.value.length > 0) {
        selectSession(sessions.value[0].sessionId)
      }
    }
  } catch (error: any) {
    console.error('加载会话列表失败:', error)
  }
}

// 选择会话
const selectSession = async (sessionId: string) => {
  activeSession.value = sessionId
  await loadMessages()
  
  // 标记该会话的消息为已读，并刷新会话列表
  setTimeout(() => {
    loadSessions()
  }, 500)
}

// 加载历史消息
const loadMessages = async () => {
  if (!activeSession.value) return
  
  try {
    const res = await getMessages(activeSession.value)
    if (res.code === 200) {
      messages.value = res.data
      await nextTick()
      scrollToBottom()
    }
  } catch (error: any) {
    console.error('加载消息失败:', error)
  }
}

// 发送文本消息
const sendMessage = () => {
  if (!messageInput.value.trim()) {
    return
  }

  if (!activeSession.value) {
    ElMessage.warning('请先选择一个会话')
    return
  }

  if (!ws || ws.readyState !== WebSocket.OPEN) {
    ElMessage.error('连接已断开，请刷新页面')
    return
  }

  if (!currentUserInfo.value) {
    ElMessage.error('无法获取用户信息')
    return
  }

  const message = {
    type: 'message',
    sessionId: activeSession.value,
    senderId: userStore.userInfo?.id,
    senderName: userStore.userInfo?.username || '客服',
    senderRole: 'admin',
    receiverId: currentUserInfo.value.userId,
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

  if (!activeSession.value) {
    ElMessage.warning('请先选择一个会话')
    return
  }

  if (!currentUserInfo.value) {
    ElMessage.error('无法获取用户信息')
    return
  }

  // 验证文件类型
  if (!file.type.startsWith('image/')) {
    ElMessage.error('请选择图片文件')
    return
  }

  // 验证文件大小（最大10MB）
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.error('图片大小不能超过10MB')
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
        sessionId: activeSession.value,
        senderId: userStore.userInfo?.id,
        senderName: userStore.userInfo?.username || '客服',
        senderRole: 'admin',
        receiverId: currentUserInfo.value.userId,
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
      ElMessage.success('图片发送成功')
    }
  } catch (error: any) {
    console.error('图片上传失败:', error)
    ElMessage.error(error.response?.data?.message || '图片上传失败')
  } finally {
    uploading.value = false
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

// 格式化时间
const formatTime = (time: string) => {
  const date = new Date(time)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  
  if (diff < 60 * 1000) {
    return '刚刚'
  }
  
  if (diff < 60 * 60 * 1000) {
    return `${Math.floor(diff / 60 / 1000)}分钟前`
  }
  
  if (date.toDateString() === now.toDateString()) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  
  return date.toLocaleString('zh-CN', { 
    month: '2-digit', 
    day: '2-digit', 
    hour: '2-digit', 
    minute: '2-digit' 
  })
}

onMounted(() => {
  // 检查是否是管理员
  if (userStore.userInfo?.role !== 'admin') {
    ElMessage.error(t('adminCustomerService.adminOnly'))
    router.push('/categories')
    return
  }
  
  initWebSocket()
})

onUnmounted(() => {
  if (ws) {
    ws.close()
  }
})
</script>

<template>
  <div class="admin-customer-service-container">
    <!-- 顶部导航栏 -->
    <div class="chat-header">
      <el-button 
        :icon="ArrowLeft" 
        circle 
        @click="router.push('/admin/dishes')"
        class="back-btn"
      />
      <div class="header-info">
        <div class="header-title">
          <el-icon class="title-icon"><ChatDotRound /></el-icon>
          <span>{{ t('adminCustomerService.title') }}</span>
        </div>
        <div class="header-status">
          <span class="status-dot" :class="{ 'online': connected }"></span>
          <span class="status-text">{{ connected ? t('customerService.online') : t('customerService.offline') }}</span>
        </div>
      </div>
      <div class="header-placeholder"></div>
    </div>

    <div class="main-content">
      <!-- 左侧会话列表 -->
      <div class="sessions-sidebar">
        <div class="sidebar-header">
          <h3>{{ t('adminCustomerService.sessionList') }}</h3>
          <el-badge 
            :value="sessions.reduce((sum, s) => sum + s.unreadCount, 0)" 
            :max="99" 
            class="session-badge"
            v-if="sessions.reduce((sum, s) => sum + s.unreadCount, 0) > 0"
          />
        </div>
        <div class="sessions-list">
          <div 
            v-for="session in sessions" 
            :key="session.sessionId"
            class="session-item"
            :class="{ 'active': session.sessionId === activeSession }"
            @click="selectSession(session.sessionId)"
          >
            <el-badge 
              :value="session.unreadCount" 
              :max="99"
              :hidden="session.unreadCount === 0"
              class="session-avatar-badge"
            >
              <el-avatar :size="45">
                <el-icon><User /></el-icon>
              </el-avatar>
            </el-badge>
            <div class="session-info">
              <div class="session-name">{{ session.username }}</div>
            </div>
          </div>

          <div v-if="sessions.length === 0" class="empty-sessions">
            <el-icon :size="60" color="#D0D0D0"><ChatDotRound /></el-icon>
            <p>{{ t('adminCustomerService.emptySessions') }}</p>
          </div>
        </div>
      </div>

      <!-- 右侧聊天区域 -->
      <div class="chat-area">
        <div v-if="!activeSession" class="no-session-selected">
          <el-icon :size="100" color="#D0D0D0"><ChatDotRound /></el-icon>
          <p>{{ t('adminCustomerService.selectSession') }}</p>
        </div>

        <template v-else>
          <!-- 消息列表 -->
          <div class="messages-container" ref="messagesContainer">
            <div 
              v-for="msg in messages" 
              :key="msg.id"
              class="message-item"
              :class="{ 'message-self': msg.senderRole === 'admin' }"
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
                :disabled="!messageInput.trim() || !connected || !activeSession"
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
            
            <!-- 隐藏的相机输入 -->
            <input 
              ref="cameraInputRef"
              type="file" 
              accept="image/*"
              capture="user"
              style="display: none"
              @change="handleImageUpload"
            />
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.admin-customer-service-container {
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

/* 主内容区 */
.main-content {
  flex: 1;
  display: flex;
  overflow: hidden;
}

/* 左侧会话列表 */
.sessions-sidebar {
  width: 300px;
  background: #FFF;
  border-right: 1px solid #E0E0E0;
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 20px;
  border-bottom: 1px solid #E0E0E0;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.sidebar-header h3 {
  margin: 0;
  font-size: 16px;
  color: #333;
}

.sessions-list {
  flex: 1;
  overflow-y: auto;
}

.session-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  cursor: pointer;
  transition: background 0.3s;
  border-bottom: 1px solid #F5F5F5;
  position: relative;
}

.session-avatar-badge {
  flex-shrink: 0;
}

.session-item:hover {
  background: #F5F5F5;
}

.session-item.active {
  background: #FFF5E6;
  border-left: 3px solid #FF6B35;
}

.session-info {
  flex: 1;
  min-width: 0;
}

.session-name {
  font-size: 15px;
  font-weight: bold;
  color: #333;
  margin-bottom: 4px;
}

.session-id {
  font-size: 12px;
  color: #999;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.empty-sessions {
  text-align: center;
  padding: 80px 20px;
}

.empty-sessions p {
  margin: 20px 0;
  color: #999;
  font-size: 14px;
}

/* 右侧聊天区域 */
.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #F5F5F5;
}

.no-session-selected {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.no-session-selected p {
  margin: 20px 0;
  color: #999;
  font-size: 16px;
}

/* 消息列表 */
.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
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

