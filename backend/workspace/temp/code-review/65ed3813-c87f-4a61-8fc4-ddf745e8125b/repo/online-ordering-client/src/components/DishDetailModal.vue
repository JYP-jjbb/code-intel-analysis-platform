<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { 
  Star, 
  Timer, 
  Dish as DishIcon, 
  ShoppingCartFull,
  Plus,
  Minus,
  Warning,
  InfoFilled
} from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { useDataTranslation } from '@/composables/useDataTranslation'
import type { Dish } from '@/types'

interface Props {
  dish: Dish
  visible: boolean
}

interface Emits {
  (e: 'update:visible', value: boolean): void
  (e: 'add-to-cart', dish: Dish, quantity: number): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const { t } = useI18n()
const { 
  translateDishByLocale,
  translateDishLabelByLocale,
  translateIngredientByLocale,
  translateTagByLocale
} = useDataTranslation()

const quantity = ref(1)

// 根据菜品生成详细信息
const getDishDetails = (dish: Dish) => {
  const detailsMap: Record<string, any> = {
    '宫保鸡丁': {
      ingredients: ['鸡胸肉', '花生', '干辣椒', '花椒', '葱姜蒜', '酱油', '白糖', '醋'],
      allergens: ['花生', '大豆'],
      calories: 280,
      servingSize: '约300g',
      cookingTime: '15分钟',
      spicyLevel: 3,
      tags: ['川菜', '下饭', '经典'],
      nutritionFacts: { protein: 25, fat: 15, carbs: 12 },
      description: '宫保鸡丁是一道经典的川菜名菜，以鸡肉为主料，配以花生米、辣椒等辅料烹制而成。红而不辣、辣而不猛、香辣味浓、肉质滑脆。'
    },
    '鱼香肉丝': {
      ingredients: ['猪里脊', '木耳', '胡萝卜', '青椒', '泡椒', '葱姜蒜', '豆瓣酱'],
      allergens: ['大豆'],
      calories: 320,
      servingSize: '约280g',
      cookingTime: '12分钟',
      spicyLevel: 2,
      tags: ['川菜', '酸甜', '开胃'],
      nutritionFacts: { protein: 20, fat: 18, carbs: 15 },
      description: '鱼香肉丝是川菜的经典之作，虽名为"鱼香"却不含鱼，其独特的味道来自于泡椒、葱姜蒜等调料的完美融合。'
    },
    '麻婆豆腐': {
      ingredients: ['嫩豆腐', '牛肉末', '豆瓣酱', '花椒', '辣椒', '葱姜蒜'],
      allergens: ['大豆'],
      calories: 180,
      servingSize: '约250g',
      cookingTime: '10分钟',
      spicyLevel: 4,
      tags: ['川菜', '麻辣', '素食可选'],
      nutritionFacts: { protein: 12, fat: 10, carbs: 8 },
      description: '麻婆豆腐是川菜中的经典名菜，以其麻、辣、烫、香、酥、嫩、鲜、活八字特色而闻名。豆腐嫩滑，麻辣鲜香。'
    },
    '糖醋里脊': {
      ingredients: ['猪里脊', '番茄酱', '白糖', '醋', '淀粉', '鸡蛋'],
      allergens: ['鸡蛋', '小麦'],
      calories: 380,
      servingSize: '约300g',
      cookingTime: '18分钟',
      spicyLevel: 0,
      tags: ['酸甜', '儿童最爱', '宴客菜'],
      nutritionFacts: { protein: 22, fat: 16, carbs: 35 },
      description: '糖醋里脊是一道传统名菜，外酥里嫩，酸甜可口。金黄色的外皮包裹着鲜嫩的里脊肉，是老少皆宜的美味佳肴。'
    },
    '酸辣土豆丝': {
      ingredients: ['土豆', '青椒', '红椒', '醋', '辣椒', '蒜'],
      allergens: [],
      calories: 120,
      servingSize: '约200g',
      cookingTime: '8分钟',
      spicyLevel: 2,
      tags: ['素菜', '爽口', '快手菜'],
      nutritionFacts: { protein: 3, fat: 5, carbs: 18 },
      description: '酸辣土豆丝是一道经典的家常菜，土豆丝爽脆可口，酸辣开胃。简单却不失美味，是餐桌上的常客。'
    },
    '凉拌黄瓜': {
      ingredients: ['黄瓜', '蒜', '香油', '醋', '盐', '白糖'],
      allergens: [],
      calories: 45,
      servingSize: '约150g',
      cookingTime: '5分钟',
      spicyLevel: 0,
      tags: ['凉菜', '清爽', '低卡'],
      nutritionFacts: { protein: 1, fat: 2, carbs: 6 },
      description: '凉拌黄瓜清爽解腻，是夏日必备的开胃小菜。黄瓜脆嫩多汁，配上蒜香和醋的酸爽，让人食欲大开。'
    },
    '皮蛋豆腐': {
      ingredients: ['内酯豆腐', '皮蛋', '葱', '香油', '酱油', '香菜'],
      allergens: ['大豆', '鸡蛋'],
      calories: 150,
      servingSize: '约200g',
      cookingTime: '5分钟',
      spicyLevel: 0,
      tags: ['凉菜', '营养', '经典'],
      nutritionFacts: { protein: 10, fat: 8, carbs: 5 },
      description: '皮蛋豆腐是一道经典的凉菜，豆腐嫩滑，皮蛋香醇，两者完美结合，营养丰富，口感独特。'
    }
  }

  // 默认信息
  const defaultDetails = {
    ingredients: t('dishDetail.defaultIngredients'),
    allergens: [],
    calories: 200,
    servingSize: '约250g',
    cookingTime: '10-15分钟',
    spicyLevel: 1,
    tags: t('dishDetail.defaultTags'),
    nutritionFacts: { protein: 15, fat: 10, carbs: 20 },
    description: dish.description || t('dishDetail.defaultDescription')
  }

  return detailsMap[dish.name] || defaultDetails
}

const dishDetails = ref(getDishDetails(props.dish))

const handleClose = () => {
  emit('update:visible', false)
  quantity.value = 1
}

const handleAddToCart = () => {
  if (props.dish.status === 0) {
    ElMessage.warning(t('dishDetail.offShelf'))
    return
  }
  if (quantity.value > props.dish.stock) {
    ElMessage.warning(t('dishDetail.outOfStock'))
    return
  }
  emit('add-to-cart', props.dish, quantity.value)
  handleClose()
}

const increaseQuantity = () => {
  if (quantity.value < props.dish.stock) {
    quantity.value++
  } else {
    ElMessage.warning(t('dishDetail.stockLimit'))
  }
}

const decreaseQuantity = () => {
  if (quantity.value > 1) {
    quantity.value--
  }
}

const getSpicyText = (level: number) => {
  const texts = [
    t('dishDetail.spicyLevel.none'),
    t('dishDetail.spicyLevel.mild'),
    t('dishDetail.spicyLevel.medium'),
    t('dishDetail.spicyLevel.hot'),
    t('dishDetail.spicyLevel.extraHot'),
    t('dishDetail.spicyLevel.extreme')
  ]
  return texts[level] || t('dishDetail.spicyLevel.none')
}

const getSpicyColor = (level: number) => {
  if (level === 0) return '#67C23A'
  if (level <= 2) return '#E6A23C'
  if (level <= 4) return '#F56C6C'
  return '#C71585'
}

// 翻译份量文本
const getServingSizeText = (servingSize: string) => {
  // 替换"约"为翻译后的文本
  return servingSize.replace('约', t('dishDetail.about'))
}

// 翻译烹饪时间文本
const getCookingTimeText = (cookingTime: string) => {
  // 替换"分钟"为翻译后的文本
  return cookingTime.replace('分钟', t('dishDetail.minutes'))
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="handleClose"
    width="800px"
    :close-on-click-modal="false"
    class="dish-detail-dialog"
  >
    <template #header>
      <div class="dialog-header">
        <h2>{{ translateDishByLocale(dish).name }}</h2>
        <div class="header-tags">
          <el-tag 
            v-for="tag in dishDetails.tags" 
            :key="tag"
            type="warning"
            effect="plain"
            size="small"
          >
            {{ translateTagByLocale(tag) }}
          </el-tag>
        </div>
      </div>
    </template>

    <div class="dish-detail-content">
      <!-- 左侧图片 -->
      <div class="image-section">
        <el-image 
          :src="dish.imageUrl" 
          fit="cover"
          class="dish-image"
          :preview-src-list="[dish.imageUrl]"
          preview-teleported
        >
          <template #error>
            <div class="image-error">
              <el-icon :size="60"><DishIcon /></el-icon>
              <p>{{ t('dishDetail.noImage') }}</p>
            </div>
          </template>
        </el-image>
        
        <div class="image-info">
          <div class="info-item">
            <el-icon><Star /></el-icon>
            <span>{{ t('dishDetail.sold') }} {{ dish.sales }}</span>
          </div>
          <div class="info-item">
            <el-icon><Timer /></el-icon>
            <span>{{ getCookingTimeText(dishDetails.cookingTime) }}</span>
          </div>
        </div>
      </div>

      <!-- 右侧信息 -->
      <div class="info-section">
        <!-- 价格和状态 -->
        <div class="price-box">
          <div class="price">
            <span class="currency">¥</span>
            <span class="amount">{{ dish.price }}</span>
            <span class="unit">{{ t('dishDetail.perServing') }}</span>
          </div>
          <el-tag 
            :type="dish.status === 1 ? 'success' : 'info'"
            size="large"
          >
            {{ dish.status === 1 ? translateDishLabelByLocale('供应中') : translateDishLabelByLocale('已下架') }}
          </el-tag>
        </div>

        <!-- 描述 -->
        <div class="description">
          <p>{{ translateDishByLocale(dish).description || dishDetails.description }}</p>
        </div>

        <!-- 详细信息 -->
        <div class="detail-info">
          <div class="info-row">
            <span class="label">🌶️ {{ translateDishLabelByLocale('辣度') }}：</span>
            <el-rate 
              :model-value="dishDetails.spicyLevel" 
              disabled 
              :colors="[getSpicyColor(dishDetails.spicyLevel)]"
            />
            <span :style="{ color: getSpicyColor(dishDetails.spicyLevel), marginLeft: '8px', fontWeight: 'bold' }">
              {{ getSpicyText(dishDetails.spicyLevel) }}
            </span>
          </div>

          <div class="info-row">
            <span class="label">📦 {{ translateDishLabelByLocale('份量') }}：</span>
            <span>{{ getServingSizeText(dishDetails.servingSize) }}</span>
          </div>

          <div class="info-row">
            <span class="label">🔥 {{ translateDishLabelByLocale('热量') }}：</span>
            <span>{{ dishDetails.calories }} {{ translateDishLabelByLocale('千卡') }}</span>
          </div>

          <div class="info-row">
            <span class="label">📋 {{ t('dishDetail.stock') }}：</span>
            <el-tag :type="dish.stock > 50 ? 'success' : dish.stock > 10 ? 'warning' : 'danger'">
              {{ t('dishDetail.remaining') }} {{ dish.stock }} {{ t('dishDetail.portions') }}
            </el-tag>
          </div>
        </div>

        <!-- 食材 -->
        <div class="ingredients-section">
          <h4>🥘 {{ translateDishLabelByLocale('主要食材') }}</h4>
          <div class="ingredients-list">
            <el-tag 
              v-for="ingredient in dishDetails.ingredients" 
              :key="ingredient"
              type="success"
              effect="plain"
              size="small"
            >
              {{ translateIngredientByLocale(ingredient) }}
            </el-tag>
          </div>
        </div>

        <!-- 过敏原提示 -->
        <div v-if="dishDetails.allergens && dishDetails.allergens.length > 0" class="allergens-section">
          <el-alert
            type="warning"
            :closable="false"
            show-icon
          >
            <template #title>
              <span style="font-weight: bold;">{{ t('dishDetail.allergenWarning') }}</span>
            </template>
            <div class="allergens-list">
              {{ t('dishDetail.allergenContains', { items: dishDetails.allergens.join('、') }) }}
            </div>
          </el-alert>
        </div>

        <!-- 营养成分 -->
        <div class="nutrition-section">
          <h4>📊 {{ translateDishLabelByLocale('营养成分') }}{{ t('dishDetail.perPortion') }}</h4>
          <div class="nutrition-grid">
            <div class="nutrition-item">
              <div class="nutrition-label">{{ translateDishLabelByLocale('蛋白质') }}</div>
              <div class="nutrition-value">{{ dishDetails.nutritionFacts.protein }}g</div>
            </div>
            <div class="nutrition-item">
              <div class="nutrition-label">{{ translateDishLabelByLocale('脂肪') }}</div>
              <div class="nutrition-value">{{ dishDetails.nutritionFacts.fat }}g</div>
            </div>
            <div class="nutrition-item">
              <div class="nutrition-label">{{ translateDishLabelByLocale('碳水') }}</div>
              <div class="nutrition-value">{{ dishDetails.nutritionFacts.carbs }}g</div>
            </div>
          </div>
        </div>

        <!-- 数量选择和加入购物车 -->
        <div class="action-section">
          <div class="quantity-selector">
            <span class="quantity-label">{{ translateDishLabelByLocale('数量') }}：</span>
            <el-button 
              :icon="Minus" 
              circle 
              @click="decreaseQuantity"
              :disabled="quantity <= 1"
            />
            <el-input-number 
              v-model="quantity" 
              :min="1" 
              :max="dish.stock"
              controls-position="right"
              class="quantity-input"
            />
            <el-button 
              :icon="Plus" 
              circle 
              @click="increaseQuantity"
              :disabled="quantity >= dish.stock"
            />
          </div>

          <el-button 
            type="warning" 
            size="large"
            :icon="ShoppingCartFull"
            @click="handleAddToCart"
            :disabled="dish.status === 0"
            class="add-cart-btn"
          >
            {{ dish.status === 0 ? translateDishLabelByLocale('已下架') : `${translateDishLabelByLocale('加入购物车')} ¥${(dish.price * quantity).toFixed(2)}` }}
          </el-button>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<style scoped>
.dish-detail-dialog :deep(.el-dialog) {
  border-radius: 20px;
  overflow: hidden;
}

.dish-detail-dialog :deep(.el-dialog__header) {
  background: linear-gradient(135deg, #FF9966 0%, #FF6B35 100%);
  padding: 24px 30px;
  margin: 0;
}

.dish-detail-dialog :deep(.el-dialog__close) {
  color: #FFF;
  font-size: 24px;
}

.dialog-header h2 {
  color: #FFF;
  margin: 0 0 12px 0;
  font-size: 28px;
  text-shadow: 2px 2px 4px rgba(0, 0, 0, 0.2);
}

.header-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.dish-detail-content {
  display: flex;
  gap: 30px;
  padding: 30px;
}

.image-section {
  flex: 0 0 350px;
}

.dish-image {
  width: 100%;
  height: 350px;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}

.image-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  background: #f5f7fa;
  color: #909399;
}

.image-info {
  display: flex;
  justify-content: space-around;
  margin-top: 16px;
  padding: 16px;
  background: #FFF5E6;
  border-radius: 12px;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #FF6B35;
  font-weight: bold;
}

.info-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.price-box {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
  background: linear-gradient(135deg, #FFE8CC 0%, #FFD9B3 100%);
  border-radius: 12px;
}

.price {
  display: flex;
  align-items: baseline;
}

.currency {
  font-size: 24px;
  color: #FF6B35;
  font-weight: bold;
}

.amount {
  font-size: 42px;
  color: #FF6B35;
  font-weight: bold;
  margin: 0 4px;
}

.unit {
  font-size: 16px;
  color: #999;
}

.description {
  padding: 16px;
  background: #FFF;
  border-left: 4px solid #FF9966;
  border-radius: 8px;
  line-height: 1.8;
  color: #666;
}

.detail-info {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.info-row {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 15px;
}

.label {
  font-weight: bold;
  color: #666;
  min-width: 80px;
}

.ingredients-section h4,
.nutrition-section h4 {
  margin: 0 0 12px 0;
  color: #FF6B35;
  font-size: 16px;
}

.ingredients-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.allergens-section {
  margin: 16px 0;
}

.allergens-list {
  color: #E6A23C;
  font-weight: bold;
}

.nutrition-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.nutrition-item {
  text-align: center;
  padding: 16px;
  background: #FFF5E6;
  border-radius: 12px;
}

.nutrition-label {
  font-size: 14px;
  color: #999;
  margin-bottom: 8px;
}

.nutrition-value {
  font-size: 24px;
  font-weight: bold;
  color: #FF6B35;
}

.action-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: auto;
  padding-top: 20px;
  border-top: 2px dashed #FFE8CC;
}

.quantity-selector {
  display: flex;
  align-items: center;
  gap: 12px;
}

.quantity-label {
  font-weight: bold;
  color: #666;
}

.quantity-input {
  width: 120px;
}

.add-cart-btn {
  width: 100%;
  height: 50px;
  font-size: 18px;
  font-weight: bold;
  border-radius: 25px;
  background: linear-gradient(135deg, #FF9966 0%, #FF6B35 100%);
  border: none;
  box-shadow: 0 4px 12px rgba(255, 107, 53, 0.3);
  transition: all 0.3s;
}

.add-cart-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(255, 107, 53, 0.4);
}
</style>


