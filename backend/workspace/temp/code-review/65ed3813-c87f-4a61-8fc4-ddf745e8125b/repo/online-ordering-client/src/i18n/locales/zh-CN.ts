export default {
  app: {
    name: '网上订餐系统'
  },
  common: {
    language: 'Language',
    chinese: '中文(zh)',
    english: '英文(en)',
    switchLanguage: '切换语言',
    currentLanguage: '当前语言',
    loading: '加载中...',
    tip: '提示',
    confirm: '确认',
    cancel: '取消',
    save: '保存',
    submit: '提交',
    delete: '删除',
    edit: '编辑',
    search: '搜索',
    empty: '暂无数据'
  },
  nav: {
    menu: '餐品菜单',
    profile: '个人中心',
    orders: '我的订单',
    customerService: '联系客服',
    help: '帮助',
    loginRegister: '登录/注册',
    adminPanel: '管理后台',
    logout: '退出登录'
  },
  login: {
    subtitle: '美食美刻，温暖相伴',
    userLogin: '用户登录',
    adminLogin: '管理员登录',
    register: '用户注册',
    toRegister: '去注册',
    toLogin: '去登录',
    username: '用户名',
    password: '密码',
    confirmPassword: '确认密码',
    phone: '手机号',
    email: '邮箱',
    captcha: '验证码',
    submitLogin: '立即登录',
    submitRegister: '立即注册',
    refreshCaptcha: '点击刷新',
    placeholders: {
      username: '请输入用户名',
      password: '请输入密码',
      confirmPassword: '请再次输入密码',
      phone: '请输入手机号',
      email: '请输入邮箱',
      captcha: '请输入验证码'
    },
    hints: {
      admin: '💡 管理员账号：admin / admin123',
      user: '💡 测试账号：user001 / user123'
    },
    rules: {
      usernameRequired: '请输入用户名',
      passwordRequired: '请输入密码',
      captchaRequired: '请输入验证码'
    },
    messages: {
      success: '登录成功',
      registerSuccess: '注册成功，请登录',
      passwordMismatch: '两次密码不一致',
      notAdmin: '该账号不是管理员账号，请使用管理员账号登录',
      adminDetected: '检测到管理员账号，请切换到管理员登录',
      loginRequired: '请先登录'
    }
  },
  home: {
    category: '菜品分类',
    all: '全部',
    allDishes: '全部美食',
    totalDishes: '共 {count} 道美食',
    searchPlaceholder: '请输入餐品名称，最多40个字',
    noImage: '暂无图片',
    hot: '热销',
    offShelf: '已下架',
    addToCart: '加入购物车',
    descFallback: '美味佳肴，诚意推荐',
    stock: '库存 {count}',
    sold: '已售 {count}',
    deliveryTime: '送达时间',
    emptyDishes: '暂无菜品',
    helpDeveloping: '帮助功能开发中...',
    roleAdmin: '管理员',
    roleMember: '普通会员',
    messages: {
      loginRequired: '请先登录',
      addToCartSuccess: '加入购物车成功',
      mealPeriodRequired: '请选择餐段',
      deliveryTimeRequired: '请选择配送时间',
      deliveryTimeSet: '配送时间已设置'
    }
  },
  cart: {
    title: '购物车',
    clear: '清空',
    empty: '购物车是空的',
    emptyTip: '购物车是空的，快去选购美食吧~',
    goShop: '去选购',
    subtotal: '餐品小计',
    deliveryFee: '外送费',
    total: '合计',
    checkout: '去结算',
    checkoutCount: '去结算 ({count})',
    clearConfirm: '确定要清空购物车吗？',
    messages: {
      updateSuccess: '购物车更新成功',
      deleteSuccess: '已从购物车中移除',
      clearSuccess: '购物车已清空'
    }
  },
  ordersPage: {
    title: '我的订单',
    orderNo: '订单号：{no}',
    itemCount: '共 {count} 件商品',
    orderAmount: '订单金额',
    empty: '暂无订单',
    goOrder: '去点餐'
  },
  orderConfirm: {
    title: '确认订单',
    section: {
      address: '收货信息'
    },
    form: {
      receiverName: '收货人',
      receiverPhone: '手机号',
      address: '收货地址',
      remark: '备注'
    },
    placeholders: {
      receiverName: '请输入收货人姓名',
      receiverPhone: '请输入11位手机号',
      address: '请输入详细地址（省/市/区/街道门牌号）',
      remark: '口味偏好、餐具数量等特殊要求'
    },
    delivery: {
      title: '配送时间',
      mode: '配送方式',
      immediate: '立即配送',
      immediateDesc: '预计30-45分钟送达',
      scheduled: '预约配送',
      scheduledDesc: '选择您方便的时间段',
      mealPeriod: '选择餐段',
      time: '选择时间',
      timePlaceholder: '请选择预约配送时间',
      tipImmediate: '我们将尽快为您配送',
      tipScheduled: '我们将在您选择的时段内为您配送',
      estimatedImmediate: '预计30-45分钟送达',
      estimatedAt: '预计 {time} 送达',
      lunch: '午餐',
      dinner: '晚餐'
    },
    payment: {
      title: '支付方式',
      wechat: '微信支付',
      wechatDesc: '推荐使用微信扫码支付',
      alipay: '支付宝',
      alipayDesc: '使用支付宝扫码支付'
    },
    summary: {
      title: '订单明细',
      dishTotal: '商品总价',
      deliveryFee: '配送费',
      free: '免费',
      coupon: '优惠券',
      payAmount: '实付金额'
    },
    actions: {
      submitOrder: '提交订单'
    },
    validation: {
      receiverNameRequired: '请输入收货人姓名',
      phoneRequired: '请输入手机号',
      phoneInvalid: '请输入正确的手机号',
      addressRequired: '请输入收货地址',
      mealPeriodRequired: '请选择餐段',
      deliveryTimeRequired: '请选择预约配送时间'
    },
    messages: {
      fillDefault: '已填充默认地址',
      noDefault: '您还没有设置默认地址',
      createSuccess: '订单创建成功，请扫码支付',
      submitFail: '提交订单失败，请重试',
      logoutSuccess: '已退出登录',
      simulateSuccess: '模拟支付成功',
      simulateFail: '模拟支付失败',
      saveForLater: '订单已保存，您可以稍后在"我的订单"中继续支付'
    },
    paymentDialog: {
      title: '{method}支付',
      amountLabel: '支付金额',
      generating: '正在生成二维码...',
      qrAlt: '支付二维码',
      scanTip: '请使用{method}扫描二维码完成支付',
      remaining: '剩余时间：',
      cancel: '取消支付',
      simulate: '模拟支付成功（测试）',
      noticeTitle: '💡 温馨提示：',
      notice1: '请在 {time} 内完成支付',
      notice2: '支付成功后会自动跳转到订单详情',
      notice3: '如遇到问题请联系客服：400-123-4567',
      successMessage: '支付成功！',
      viewOrder: '查看订单',
      timeoutMessage: '支付已超时，是否继续支付？',
      continuePay: '继续支付',
      payLater: '稍后支付',
      cancelPayMessage: '订单已创建，取消支付后订单将保留为未支付状态，您可以稍后继续支付。是否查看订单？',
      continueShopping: '继续购物'
    },
    addressSelector: {
      select: '选择地址',
      manage: '管理地址',
      title: '选择收货地址',
      empty: '还没有保存的地址',
      goAdd: '去添加地址',
      default: '默认',
      cancel: '取消',
      addNew: '添加新地址'
    }
  },
  profilePage: {
    tabs: {
      profile: '个人资料',
      orders: '最近订单',
      benefits: '会员权益',
      addresses: '送餐地址'
    },
    sections: {
      profileInfo: '📝 个人信息',
      security: '🔐 账号安全',
      recentOrders: '📦 最近订单',
      benefits: '👑 会员等级说明',
      addresses: '📍 我的地址'
    },
    stats: {
      totalOrders: '总订单',
      totalSpent: '总消费',
      memberDays: '会员天数',
      favorites: '收藏菜品'
    },
    actions: {
      accountSettings: '账号设置',
      changePassword: '修改密码',
      viewAll: '查看全部 →',
      addAddress: '添加地址',
      addFirstAddress: '添加第一个地址'
    },
    form: {
      username: '用户名',
      phone: '手机号',
      email: '邮箱',
      defaultAddress: '默认地址'
    },
    memberLevels: {
      normal: '普通会员',
      silver: '白银会员',
      gold: '黄金会员',
      diamond: '钻石会员'
    },
    orderStatus: {
      pendingPay: '待支付',
      pendingAccept: '待接单',
      preparing: '制作中',
      delivering: '已派送',
      delivered: '已送达',
      canceled: '已取消',
      unknown: '未知'
    },
    orders: {
      orderNo: '订单号：{no}',
      receiver: '收货人：',
      phone: '联系电话：',
      address: '配送地址：',
      amount: '订单金额',
      empty: '暂无订单'
    },
    benefits: {
      normal: {
        title: '普通会员',
        requirement: '累计消费 ¥0+',
        item1: '✓ 基础积分奖励',
        item2: '✓ 生日优惠券',
        item3: '✓ 新品尝鲜'
      },
      silver: {
        title: '白银会员',
        requirement: '累计消费 ¥200+',
        item1: '✓ 1.2倍积分',
        item2: '✓ 每月专属优惠',
        item3: '✓ 优先配送',
        item4: '✓ 专属客服'
      },
      gold: {
        title: '黄金会员',
        requirement: '累计消费 ¥500+',
        item1: '✓ 1.5倍积分',
        item2: '✓ 免配送费',
        item3: '✓ VIP专属菜品',
        item4: '✓ 生日大礼包',
        item5: '✓ 优先退款'
      },
      diamond: {
        title: '钻石会员',
        requirement: '累计消费 ¥1000+',
        item1: '✓ 2倍积分',
        item2: '✓ 全场9折',
        item3: '✓ 专属定制服务',
        item4: '✓ 节日礼品',
        item5: '✓ 优先预订',
        item6: '✓ 尊享客服'
      },
      nextLevelTitle: '🎯 距离下一等级',
      nextLevelText: '再消费 ¥{amount} 即可升级为白银会员'
    },
    address: {
      empty: '还没有收货地址',
      default: '默认',
      setDefault: '设为默认地址',
      validation: {
        receiverNameRequired: '请输入收货人姓名',
        phoneRequired: '请输入手机号',
        phoneInvalid: '请输入正确的手机号',
        addressRequired: '请输入详细地址'
      },
      messages: {
        addSuccess: '地址添加成功',
        updateSuccess: '地址更新成功',
        deleteConfirm: '确定要删除这个地址吗？',
        deleteSuccess: '地址删除成功',
        deleteFail: '删除失败',
        setDefaultSuccess: '已设置为默认地址',
        setDefaultFail: '设置失败'
      }
    },
    addressDialog: {
      addTitle: '添加地址',
      editTitle: '编辑地址',
      receiverName: '收货人',
      receiverPhone: '手机号',
      address: '详细地址',
      placeholders: {
        receiverName: '请输入收货人姓名',
        receiverPhone: '请输入11位手机号',
        address: '请输入详细地址（省/市/区/街道门牌号）'
      }
    },
    passwordDialog: {
      title: '修改密码',
      oldPassword: '原密码',
      newPassword: '新密码',
      confirmPassword: '确认密码'
    },
    messages: {
      profileSaved: '个人信息已保存',
      passwordMismatch: '两次密码输入不一致',
      passwordChanged: '密码修改成功',
      logoutSuccess: '已退出登录',
      actionFail: '操作失败'
    }
  },
  admin: {
    title: '管理后台',
    subtitle: 'Admin Panel',
    dishManage: '菜品管理',
    orderManage: '订单管理',
    csWorkbench: '客服工作台',
    backHome: '返回首页',
    logout: '退出登录',
    adminTag: '管理员'
  },
  welcome: {
    title: '网上订餐系统',
    subtitle: '美食美刻，温暖相伴',
    description: '新鲜食材 · 用心烹饪 · 为您送上温暖的味道',
    startOrder: '开始点餐',
    scrollHint: '向下滚动探索更多',
    sections: {
      section1: {
        title: '🍜 美食美刻，温暖相伴',
        desc: '精选优质食材，用心烹饪每一道美味佳肴，让您享受舌尖上的幸福'
      },
      section2: {
        title: '🌶️ 新鲜热菜 · 火热出炉',
        desc: '川菜、粤菜、湘菜经典佳肴，色香味俱全，热气腾腾送到家'
      },
      section3: {
        title: '🥗 营养凉菜 · 清爽解腻',
        desc: '精选时令新鲜蔬菜，口感爽脆，健康美味的不二之选'
      },
      section4: {
        title: '🍚 美味主食 · 精心烹制',
        desc: '粒粒分明的炒饭，香喷喷的水饺，满足您对主食的所有期待'
      },
      section5: {
        title: '🍰 精致甜点 · 甜蜜时光',
        desc: '手工制作，用心呈现，每一口都是幸福的味道'
      },
      section6: {
        title: '🥤 清凉饮品 · 解渴首选',
        desc: '各式冷热饮品，搭配美食更佳，让您的用餐体验更完美'
      }
    },
    cta: {
      title: '准备好开始您的美食之旅了吗？',
      subtitle: '立即注册，享受首单优惠',
      button: '立即点餐'
    },
    footer: '© 2025 网上订餐系统 · 美食美刻，温暖相伴'
  },
  dishDetail: {
    outOfStock: '库存不足',
    stockLimit: '已达库存上限',
    remaining: '剩余 {count} 份',
    addToCartSuccess: '已加入购物车',
    loginRequired: '请先登录',
    offShelf: '该菜品已下架',
    noImage: '暂无图片',
    sold: '已售',
    about: '约',
    minutes: '分钟',
    perServing: '/份',
    stock: '库存',
    remaining: '剩余',
    portions: '份',
    allergenWarning: '⚠️ 过敏原提示',
    allergenContains: '本菜品含：{items}',
    perPortion: '（每份）',
    spicyLevel: {
      none: '不辣',
      mild: '微辣',
      medium: '中辣',
      hot: '重辣',
      extraHot: '特辣',
      extreme: '变态辣'
    },
    defaultIngredients: ['精选食材', '秘制调料'],
    defaultTags: ['美味', '新鲜'],
    defaultDescription: '精心烹制的美味佳肴，选用新鲜食材，口感绝佳，营养丰富。'
  },
  customerService: {
    title: '在线客服',
    online: '在线',
    offline: '离线',
    emptyMessage: '暂无消息，开始聊天吧~',
    messages: {
      connectionFailed: '连接失败，请检查网络',
      connectionLost: '连接已断开，请刷新页面',
      invalidFileType: '请选择图片文件',
      fileSizeLimit: '图片大小不能超过10MB',
      imageSent: '图片发送成功'
    }
  },
  adminCustomerService: {
    title: '客服工作台',
    sessionList: '会话列表',
    emptySessions: '暂无会话',
    selectSession: '请从左侧选择一个会话',
    adminOnly: '只有管理员才能访问客服工作台'
  },
  adminDish: {
    title: '菜品管理',
    subtitle: '管理餐厅的所有菜品信息',
    table: {
      id: 'ID',
      image: '图片',
      name: '菜品名称',
      price: '价格',
      category: '分类',
      stock: '库存',
      actions: '操作'
    },
    dialog: {
      addTitle: '新增菜品',
      editTitle: '编辑菜品',
      name: '菜品名称',
      price: '价格',
      category: '分类',
      description: '描述',
      imageUrl: '图片地址',
      stock: '库存',
      status: '状态'
    },
    validation: {
      nameRequired: '请输入菜品名称',
      priceRequired: '请输入价格',
      categoryRequired: '请选择分类',
      stockRequired: '请输入库存'
    },
    messages: {
      fetchFailed: '获取菜品列表失败',
      categoryFetchFailed: '获取分类列表失败',
      addSuccess: '添加成功',
      updateSuccess: '更新成功',
      deleteConfirm: '确定要删除该菜品吗？',
      deleteSuccess: '删除成功'
    }
  },
  adminOrder: {
    title: '订单管理',
    subtitle: '管理所有用户的订单信息',
    table: {
      orderNo: '订单号',
      orderTime: '下单时间',
      receiver: '收货人',
      phone: '联系电话',
      amount: '订单金额',
      status: '订单状态',
      address: '配送地址',
      actions: '操作'
    },
    noData: '暂无数据',
    pagination: {
      total: '共 {count} 条',
      pageSize: '条/页',
      goTo: '跳至',
      page: '页'
    }
  },
  route: {
    welcome: '欢迎',
    categories: '菜品分类',
    login: '登录',
    dishDetail: '菜品详情',
    cart: '购物车',
    orderConfirm: '确认订单',
    orders: '我的订单'
  }
}
