// 分类名称翻译映射
export const categoryTranslations: Record<string, string> = {
  '热菜': 'Hot Dishes',
  '凉菜': 'Cold Dishes', 
  '主食': 'Staples',
  '汤品': 'Soups',
  '饮品': 'Beverages',
  '甜点': 'Desserts',
  '餐具': 'Tableware'
}

// 菜品名称翻译映射
export const dishNameTranslations: Record<string, string> = {
  // 热菜
  '宫保鸡丁': 'Kung Pao Chicken',
  '鱼香肉丝': 'Fish-Flavored Pork Shreds',
  '麻婆豆腐': 'Mapo Tofu',
  '糖醋里脊': 'Sweet and Sour Pork',
  
  // 凉菜
  '酸辣土豆丝': 'Spicy and Sour Potato Shreds',
  '凉拌黄瓜': 'Cold Cucumber Salad',
  '皮蛋豆腐': 'Preserved Egg with Tofu',
  '提拉米苏': 'Tiramisu',
  '堤拉米苏': 'Tiramisu',
  
  // 主食
  '白米饭': 'Steamed Rice',
  '炒饭': 'Fried Rice',
  '手工水饺': 'Handmade Dumplings',
  '蛋糕': 'Cake',
  '蛋肽': 'Cake',
  '提拉来苏': 'Tiramisu',
  
  // 汤品
  '番茄蛋汤': 'Tomato and Egg Soup',
  '紫菜蛋汤': 'Seaweed and Egg Soup',
  '抹茶蛋糕': 'Matcha Cake',
  
  // 饮品
  '可乐': 'Coca Cola',
  '雪碧': 'Sprite',
  '酸奶': 'Yogurt',
  
  // 甜点
  '蛋挞': 'Egg Tart',
  '雪糕': 'Ice Cream',
  '雪碧': 'Sprite',
  '酸奶': 'Yogurt',
  '可乐': 'Cola',
  
  // 餐具
  '一次性餐具': 'Disposable Tableware'
}

// 菜品描述翻译映射
export const dishDescriptionTranslations: Record<string, string> = {
  // 热菜描述
  '经典川菜，鸡肉鲜嫩，花生酥脆，回味无穷': 'Classic Sichuan dish with tender chicken and crispy peanuts, unforgettable taste',
  '酸甜可口，色泽红亮，经典家常菜': 'Sweet and sour taste with bright red color, classic home-style dish',
  '麻辣鲜香，豆腐嫩滑，川菜精品': 'Spicy and fragrant with silky tofu, premium Sichuan cuisine',
  '外酥里嫩，酸甜适中，老少皆宜': 'Crispy outside and tender inside, perfect sweet and sour balance for all ages',
  
  // 凉菜描述
  '爽脆可口，酸辣开胃，清爽小菜': 'Crispy and refreshing, spicy and sour appetizer, light side dish',
  '清爽解腻，夏日必备，健康低脂': 'Refreshing and light, summer essential, healthy and low-fat',
  '咸香滑嫩，营养丰富，经典凉菜': 'Savory and smooth, nutritious, classic cold dish',
  
  // 主食描述
  '东北优质大米，粒粒饱满': 'Premium Northeast rice, every grain is plump',
  '粒粒分明，香味浓郁，配料丰富': 'Distinct grains with rich aroma and abundant ingredients',
  '皮薄馅大，鲜香美味，纯手工制作': 'Thin skin with generous filling, fresh and delicious, purely handmade',
  
  // 汤品描述
  '酸甜可口，营养丰富，家常美味': 'Sweet and sour taste, nutritious, homestyle delicious',
  '清淡鲜美，补碘佳品，营养健康': 'Light and fresh, excellent source of iodine, nutritious and healthy',
  
  // 饮品描述
  '冰爽可乐，清凉解渴': 'Ice-cold cola, refreshing and thirst-quenching',
  '清凉雪碧，透心凉爽': 'Cool Sprite, refreshingly crisp',
  '浓郁酸奶，益生菌丰富': 'Rich yogurt, abundant probiotics',
  
  // 甜点描述
  '外酥里嫩，奶香浓郁，现烤热卖': 'Crispy outside and tender inside, rich milk aroma, freshly baked',
  '意式经典甜品，层次丰富，回味悠长': 'Classic Italian dessert, rich layers, lingering aftertaste',
  '清新抹茶香，口感细腻，甜而不腻': 'Fresh matcha aroma, delicate texture, sweet but not cloying',
  
  // 餐具描述
  '一次性餐具（筷子/勺子/纸巾）': 'Disposable tableware (chopsticks/spoon/napkin)'
}

// 菜品详细信息翻译
export const dishDetailTranslations: Record<string, any> = {
  // 通用标签
  labels: {
    '美味': 'Delicious',
    '新鲜': 'Fresh', 
    '辣度': 'Spicy Level',
    '份量': 'Serving Size',
    '热量': 'Calories',
    '产地': 'Origin',
    '主要食材': 'Main Ingredients',
    '营养成分': 'Nutrition Facts',
    '蛋白质': 'Protein',
    '脂肪': 'Fat',
    '碳水': 'Carbs',
    '数量': 'Quantity',
    '加入购物车': 'Add to Cart',
    '已售': 'Sold',
    '分钟': 'min',
    '千卡': 'kcal',
    '供应中': 'Available',
    '已下架': 'Out of Stock'
  },
  
  // 食材翻译
  ingredients: {
    '鸡胸肉': 'Chicken Breast',
    '花生': 'Peanuts',
    '干辣椒': 'Dried Chili',
    '花椒': 'Sichuan Pepper',
    '葱姜蒜': 'Scallion, Ginger, Garlic',
    '酱油': 'Soy Sauce',
    '白糖': 'Sugar',
    '醋': 'Vinegar',
    '豆腐': 'Tofu',
    '猪肉末': 'Ground Pork',
    '豆瓣酱': 'Bean Paste',
    '绿色蔬菜': 'Green Vegetables',
    '秘制调料': 'Secret Seasoning'
  },
  
  // 标签翻译
  tags: {
    '川菜': 'Sichuan Cuisine',
    '下饭': 'Perfect with Rice',
    '经典': 'Classic',
    '家常': 'Home Style',
    '麻辣': 'Spicy & Numbing',
    '清淡': 'Light',
    '营养': 'Nutritious',
    '健康': 'Healthy',
    '酸甜': 'Sweet & Sour',
    '开胃': 'Appetizer',
    '素食可选': 'Vegetarian Option',
    '儿童最爱': 'Kids Favorite',
    '宴客菜': 'Banquet Dish',
    '素菜': 'Vegetarian',
    '爽口': 'Refreshing',
    '快手菜': 'Quick Dish',
    '凉菜': 'Cold Dish',
    '清爽': 'Light & Fresh',
    '低卡': 'Low Calorie'
  }
}

// 订单状态翻译
export const orderStatusTranslations: Record<string, string> = {
  '待支付': 'Pending Payment',
  '待接单': 'Pending Accept',
  '制作中': 'Preparing',
  '已派送': 'Delivering',
  '已送达': 'Delivered',
  '已取消': 'Cancelled'
}

/**
 * 翻译分类名称
 */
export function translateCategoryName(name: string, locale: string): string {
  if (locale === 'en' && categoryTranslations[name]) {
    return categoryTranslations[name]
  }
  return name
}

/**
 * 翻译菜品名称
 */
export function translateDishName(name: string, locale: string): string {
  if (locale === 'en' && dishNameTranslations[name]) {
    return dishNameTranslations[name]
  }
  return name
}

/**
 * 翻译菜品描述
 */
export function translateDishDescription(description: string, locale: string): string {
  if (locale === 'en' && dishDescriptionTranslations[description]) {
    return dishDescriptionTranslations[description]
  }
  return description
}

/**
 * 翻译订单状态
 */
export function translateOrderStatus(status: string, locale: string): string {
  if (locale === 'en' && orderStatusTranslations[status]) {
    return orderStatusTranslations[status]
  }
  return status
}

/**
 * 批量翻译菜品数据
 */
export function translateDish(dish: any, locale: string) {
  return {
    ...dish,
    name: translateDishName(dish.name, locale),
    description: translateDishDescription(dish.description, locale),
    categoryName: dish.categoryName ? translateCategoryName(dish.categoryName, locale) : dish.categoryName
  }
}

/**
 * 批量翻译分类数据
 */
export function translateCategory(category: any, locale: string) {
  return {
    ...category,
    name: translateCategoryName(category.name, locale)
  }
}

/**
 * 翻译菜品详细信息标签
 */
export function translateDishLabel(label: string, locale: string): string {
  if (locale === 'en' && dishDetailTranslations.labels[label]) {
    return dishDetailTranslations.labels[label]
  }
  return label
}

/**
 * 翻译食材名称
 */
export function translateIngredient(ingredient: string, locale: string): string {
  if (locale === 'en' && dishDetailTranslations.ingredients[ingredient]) {
    return dishDetailTranslations.ingredients[ingredient]
  }
  return ingredient
}

/**
 * 翻译标签
 */
export function translateTag(tag: string, locale: string): string {
  if (locale === 'en' && dishDetailTranslations.tags[tag]) {
    return dishDetailTranslations.tags[tag]
  }
  return tag
}
