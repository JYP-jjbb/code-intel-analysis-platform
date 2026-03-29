/**
 * 数据翻译组合式函数
 * 结合当前语言环境自动翻译数据库内容
 */

import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useLocaleStore } from '@/store/locale'
import {
  translateCategoryName,
  translateDishName,
  translateDishDescription,
  translateOrderStatus,
  translateDish,
  translateCategory,
  translateDishLabel,
  translateIngredient,
  translateTag
} from '@/utils/dataTranslation'

export function useDataTranslation() {
  const localeStore = useLocaleStore()
  const { locale } = storeToRefs(localeStore)

  // 翻译分类名称
  const translateCategoryNameByLocale = (name: string) => {
    return translateCategoryName(name, locale.value)
  }

  // 翻译菜品名称
  const translateDishNameByLocale = (name: string) => {
    return translateDishName(name, locale.value)
  }

  // 翻译菜品描述
  const translateDishDescriptionByLocale = (description: string) => {
    return translateDishDescription(description, locale.value)
  }

  // 翻译订单状态
  const translateOrderStatusByLocale = (status: string) => {
    return translateOrderStatus(status, locale.value)
  }

  // 翻译整个菜品对象
  const translateDishByLocale = (dish: any) => {
    return translateDish(dish, locale.value)
  }

  // 翻译整个分类对象
  const translateCategoryByLocale = (category: any) => {
    return translateCategory(category, locale.value)
  }

  // 批量翻译菜品列表
  const translateDishList = (dishes: any[]) => {
    return computed(() => {
      return dishes.map(dish => translateDishByLocale(dish))
    })
  }

  // 批量翻译分类列表
  const translateCategoryList = (categories: any[]) => {
    return computed(() => {
      return categories.map(category => translateCategoryByLocale(category))
    })
  }

  // 翻译菜品详细信息标签
  const translateDishLabelByLocale = (label: string) => {
    return translateDishLabel(label, locale.value)
  }

  // 翻译食材
  const translateIngredientByLocale = (ingredient: string) => {
    return translateIngredient(ingredient, locale.value)
  }

  // 翻译标签
  const translateTagByLocale = (tag: string) => {
    return translateTag(tag, locale.value)
  }

  return {
    locale,
    translateCategoryNameByLocale,
    translateDishNameByLocale,
    translateDishDescriptionByLocale,
    translateOrderStatusByLocale,
    translateDishByLocale,
    translateCategoryByLocale,
    translateDishList,
    translateCategoryList,
    translateDishLabelByLocale,
    translateIngredientByLocale,
    translateTagByLocale
  }
}