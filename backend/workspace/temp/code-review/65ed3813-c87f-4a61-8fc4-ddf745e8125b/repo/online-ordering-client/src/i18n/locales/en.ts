export default {
  app: {
    name: 'Online Ordering'
  },
  common: {
    language: 'Language',
    chinese: 'Chinese(zh)',
    english: 'English(en)',
    switchLanguage: 'Switch Language',
    currentLanguage: 'Current Language',
    loading: 'Loading...',
    tip: 'Tip',
    confirm: 'Confirm',
    cancel: 'Cancel',
    submit: 'Submit',
    delete: 'Delete',
    edit: 'Edit',
    search: 'Search',
    empty: 'No data'
  },
  nav: {
    menu: 'Menu',
    profile: 'Profile',
    orders: 'My Orders',
    customerService: 'Customer Service',
    help: 'Help',
    loginRegister: 'Login / Register',
    adminPanel: 'Admin',
    logout: 'Logout'
  },
  login: {
    subtitle: 'Warm moments with good food',
    userLogin: 'User Login',
    adminLogin: 'Admin Login',
    register: 'Register',
    toRegister: 'Register',
    toLogin: 'Back to login',
    username: 'Username',
    password: 'Password',
    confirmPassword: 'Confirm Password',
    phone: 'Phone',
    email: 'Email',
    captcha: 'Captcha',
    submitLogin: 'Login',
    submitRegister: 'Register',
    refreshCaptcha: 'Click to refresh',
    placeholders: {
      username: 'Enter username',
      password: 'Enter password',
      confirmPassword: 'Enter password again',
      phone: 'Enter phone',
      email: 'Enter email',
      captcha: 'Enter captcha'
    },
    hints: {
      admin: '💡 Admin account: admin / admin123',
      user: '💡 Test account: user001 / user123'
    },
    rules: {
      usernameRequired: 'Please enter username',
      passwordRequired: 'Please enter password',
      captchaRequired: 'Please enter captcha'
    },
    messages: {
      success: 'Login succeeded',
      registerSuccess: 'Registered successfully. Please log in.',
      passwordMismatch: 'Passwords do not match',
      notAdmin: 'This account is not an admin account. Please use an admin account.',
      adminDetected: 'Admin account detected. Please switch to Admin Login.',
      loginRequired: 'Please log in first'
    }
  },
  home: {
    category: 'Categories',
    all: 'All',
    allDishes: 'All Dishes',
    totalDishes: '{count} dishes',
    searchPlaceholder: 'Search dishes (max 40 chars)',
    noImage: 'No image',
    hot: 'Hot',
    offShelf: 'Off shelf',
    addToCart: 'Add to cart',
    descFallback: 'Delicious dish, recommended',
    stock: 'Stock {count}',
    sold: 'Sold {count}',
    deliveryTime: 'Delivery time',
    emptyDishes: 'No dishes',
    helpDeveloping: 'Help is under development...',
    roleAdmin: 'Admin',
    roleMember: 'Member',
    messages: {
      loginRequired: 'Please log in first',
      addToCartSuccess: 'Added to cart',
      mealPeriodRequired: 'Please select meal period',
      deliveryTimeRequired: 'Please select delivery time',
      deliveryTimeSet: 'Delivery time set'
    }
  },
  cart: {
    title: 'Cart',
    clear: 'Clear',
    empty: 'Cart is empty',
    emptyTip: 'Your cart is empty. Go pick something~',
    goShop: 'Go shopping',
    subtotal: 'Subtotal',
    deliveryFee: 'Delivery fee',
    total: 'Total',
    checkout: 'Checkout',
    checkoutCount: 'Checkout ({count})',
    clearConfirm: 'Are you sure you want to clear the cart?',
    messages: {
      updateSuccess: 'Cart updated',
      deleteSuccess: 'Removed from cart',
      clearSuccess: 'Cart cleared'
    }
  },
  ordersPage: {
    title: 'My Orders',
    orderNo: 'Order No: {no}',
    itemCount: '{count} items',
    orderAmount: 'Order amount',
    empty: 'No orders',
    goOrder: 'Order now'
  },
  orderConfirm: {
    title: 'Confirm Order',
    section: {
      address: 'Shipping information'
    },
    form: {
      receiverName: 'Receiver',
      receiverPhone: 'Phone',
      address: 'Address',
      remark: 'Remark'
    },
    placeholders: {
      receiverName: 'Enter receiver name',
      receiverPhone: 'Enter 11-digit phone number',
      address: 'Enter full address (province/city/district/street/number)',
      remark: 'Preferences, utensil count, etc.'
    },
    delivery: {
      title: 'Delivery time',
      mode: 'Delivery mode',
      immediate: 'Deliver now',
      immediateDesc: 'Estimated 30-45 minutes',
      scheduled: 'Schedule delivery',
      scheduledDesc: 'Choose a convenient time slot',
      mealPeriod: 'Meal period',
      time: 'Time',
      timePlaceholder: 'Select a delivery time slot',
      tipImmediate: 'We will deliver as soon as possible',
      tipScheduled: 'We will deliver within your selected time slot',
      estimatedImmediate: 'Estimated 30-45 minutes',
      estimatedAt: 'Estimated delivery at {time}',
      lunch: 'Lunch',
      dinner: 'Dinner'
    },
    payment: {
      title: 'Payment method',
      wechat: 'WeChat Pay',
      wechatDesc: 'Recommended: scan with WeChat',
      alipay: 'Alipay',
      alipayDesc: 'Scan with Alipay'
    },
    summary: {
      title: 'Order details',
      dishTotal: 'Items subtotal',
      deliveryFee: 'Delivery fee',
      free: 'Free',
      coupon: 'Coupon',
      payAmount: 'Total'
    },
    actions: {
      submitOrder: 'Place order'
    },
    validation: {
      receiverNameRequired: 'Please enter receiver name',
      phoneRequired: 'Please enter phone number',
      phoneInvalid: 'Please enter a valid phone number',
      addressRequired: 'Please enter address',
      mealPeriodRequired: 'Please select meal period',
      deliveryTimeRequired: 'Please select delivery time'
    },
    messages: {
      fillDefault: 'Default address filled',
      noDefault: 'No default address set',
      createSuccess: 'Order created. Please scan to pay.',
      submitFail: 'Failed to submit order. Please try again.',
      logoutSuccess: 'Logged out',
      simulateSuccess: 'Simulated payment succeeded',
      simulateFail: 'Simulated payment failed',
      saveForLater: 'Order saved. You can continue paying later in "My Orders".'
    },
    paymentDialog: {
      title: 'Pay with {method}',
      amountLabel: 'Amount',
      generating: 'Generating QR code...',
      qrAlt: 'Payment QR code',
      scanTip: 'Scan the QR code with {method} to complete payment',
      remaining: 'Time left: ',
      cancel: 'Cancel payment',
      simulate: 'Simulate payment (test)',
      noticeTitle: '💡 Notes:',
      notice1: 'Complete payment within {time}',
      notice2: 'You will be redirected to the order details after payment',
      notice3: 'Need help? Call: 400-123-4567',
      successMessage: 'Payment succeeded!',
      viewOrder: 'View order',
      timeoutMessage: 'Payment timed out. Continue paying?',
      continuePay: 'Continue',
      payLater: 'Later',
      cancelPayMessage: 'The order has been created. If you cancel now, it will remain unpaid and you can pay later. View the order?',
      continueShopping: 'Continue shopping'
    },
    addressSelector: {
      select: 'Select address',
      manage: 'Manage addresses',
      title: 'Select shipping address',
      empty: 'No saved addresses',
      goAdd: 'Add an address',
      default: 'Default',
      cancel: 'Cancel',
      addNew: 'Add new address'
    }
  },
  profilePage: {
    tabs: {
      profile: 'Profile',
      orders: 'Recent Orders',
      benefits: 'Membership',
      addresses: 'Addresses'
    },
    sections: {
      profileInfo: '📝 Personal Info',
      security: '🔐 Security',
      recentOrders: '📦 Recent Orders',
      benefits: '👑 Membership Levels',
      addresses: '📍 My Addresses'
    },
    stats: {
      totalOrders: 'Total Orders',
      totalSpent: 'Total Spent',
      memberDays: 'Member Days',
      favorites: 'Favorites'
    },
    actions: {
      accountSettings: 'Account Settings',
      changePassword: 'Change Password',
      viewAll: 'View All →',
      addAddress: 'Add Address',
      addFirstAddress: 'Add First Address'
    },
    form: {
      username: 'Username',
      phone: 'Phone',
      email: 'Email',
      defaultAddress: 'Default Address'
    },
    memberLevels: {
      normal: 'Normal',
      silver: 'Silver',
      gold: 'Gold',
      diamond: 'Diamond'
    },
    orderStatus: {
      pendingPay: 'Pending Payment',
      pendingAccept: 'Pending Accept',
      preparing: 'Preparing',
      delivering: 'Delivering',
      delivered: 'Delivered',
      canceled: 'Canceled',
      unknown: 'Unknown'
    },
    orders: {
      orderNo: 'Order No: {no}',
      receiver: 'Receiver: ',
      phone: 'Phone: ',
      address: 'Address: ',
      amount: 'Amount',
      empty: 'No orders'
    },
    benefits: {
      normal: {
        title: 'Normal Member',
        requirement: 'Spend ¥0+',
        item1: '✓ Basic points',
        item2: '✓ Birthday coupon',
        item3: '✓ New items'
      },
      silver: {
        title: 'Silver Member',
        requirement: 'Spend ¥200+',
        item1: '✓ 1.2x points',
        item2: '✓ Monthly offers',
        item3: '✓ Priority delivery',
        item4: '✓ Dedicated support'
      },
      gold: {
        title: 'Gold Member',
        requirement: 'Spend ¥500+',
        item1: '✓ 1.5x points',
        item2: '✓ Free delivery',
        item3: '✓ VIP dishes',
        item4: '✓ Birthday gift',
        item5: '✓ Priority refund'
      },
      diamond: {
        title: 'Diamond Member',
        requirement: 'Spend ¥1000+',
        item1: '✓ 2x points',
        item2: '✓ 10% off',
        item3: '✓ Custom service',
        item4: '✓ Holiday gifts',
        item5: '✓ Priority booking',
        item6: '✓ VIP support'
      },
      nextLevelTitle: '🎯 Next Level',
      nextLevelText: 'Spend ¥{amount} more to upgrade to Silver'
    },
    address: {
      empty: 'No addresses',
      default: 'Default',
      setDefault: 'Set as default',
      validation: {
        receiverNameRequired: 'Please enter receiver name',
        phoneRequired: 'Please enter phone number',
        phoneInvalid: 'Please enter a valid phone number',
        addressRequired: 'Please enter address'
      },
      messages: {
        addSuccess: 'Address added',
        updateSuccess: 'Address updated',
        deleteConfirm: 'Delete this address?',
        deleteSuccess: 'Address deleted',
        deleteFail: 'Delete failed',
        setDefaultSuccess: 'Default address set',
        setDefaultFail: 'Set default failed'
      }
    },
    addressDialog: {
      addTitle: 'Add Address',
      editTitle: 'Edit Address',
      receiverName: 'Receiver',
      receiverPhone: 'Phone',
      address: 'Address',
      placeholders: {
        receiverName: 'Enter receiver name',
        receiverPhone: 'Enter 11-digit phone',
        address: 'Enter full address (province/city/district/street/number)'
      }
    },
    passwordDialog: {
      title: 'Change Password',
      oldPassword: 'Old Password',
      newPassword: 'New Password',
      confirmPassword: 'Confirm Password'
    },
    messages: {
      profileSaved: 'Profile saved',
      passwordMismatch: 'Passwords do not match',
      passwordChanged: 'Password changed',
      logoutSuccess: 'Logged out',
      actionFail: 'Action failed'
    }
  },
  admin: {
    title: 'Admin Panel',
    subtitle: 'Admin Panel',
    dishManage: 'Dish Management',
    orderManage: 'Order Management',
    csWorkbench: 'Customer Service',
    backHome: 'Back to Home',
    logout: 'Logout',
    adminTag: 'Admin'
  },
  welcome: {
    title: 'Online Ordering System',
    subtitle: 'Warm moments with good food',
    description: 'Fresh ingredients · Carefully cooked · Delivering warm flavors to you',
    startOrder: 'Start Ordering',
    scrollHint: 'Scroll down to explore more',
    sections: {
      section1: {
        title: '🍜 Good Food, Warm Moments',
        desc: 'Selected premium ingredients, carefully cooked delicious dishes, let you enjoy the happiness on your tongue'
      },
      section2: {
        title: '🌶️ Fresh Hot Dishes · Hot from the Kitchen',
        desc: 'Classic Sichuan, Cantonese, and Hunan dishes with perfect color, aroma, and taste, delivered hot to your home'
      },
      section3: {
        title: '🥗 Nutritious Cold Dishes · Refreshing',
        desc: 'Selected seasonal fresh vegetables, crisp taste, the best choice for healthy and delicious food'
      },
      section4: {
        title: '🍚 Delicious Staples · Carefully Prepared',
        desc: 'Distinct fried rice, fragrant dumplings, meeting all your expectations for staple food'
      },
      section5: {
        title: '🍰 Exquisite Desserts · Sweet Time',
        desc: 'Handmade with care, every bite is the taste of happiness'
      },
      section6: {
        title: '🥤 Refreshing Drinks · First Choice for Thirst',
        desc: 'Various hot and cold drinks, better with food, making your dining experience more perfect'
      }
    },
    cta: {
      title: 'Ready to start your culinary journey?',
      subtitle: 'Register now and enjoy first order discount',
      button: 'Order Now'
    },
    footer: '© 2025 Online Ordering System · Warm moments with good food'
  },
  dishDetail: {
    outOfStock: 'Out of stock',
    stockLimit: 'Stock limit reached',
    remaining: '{count} remaining',
    addToCartSuccess: 'Added to cart',
    loginRequired: 'Please log in first',
    offShelf: 'This dish is off shelf',
    noImage: 'No image',
    sold: 'Sold',
    about: 'About',
    minutes: 'min',
    perServing: '/serving',
    stock: 'Stock',
    remaining: 'Remaining',
    portions: 'portions',
    allergenWarning: '⚠️ Allergen Warning',
    allergenContains: 'Contains: {items}',
    perPortion: '(per serving)',
    spicyLevel: {
      none: 'Not Spicy',
      mild: 'Mild',
      medium: 'Medium',
      hot: 'Hot',
      extraHot: 'Extra Hot',
      extreme: 'Extreme'
    },
    defaultIngredients: ['Selected Ingredients', 'Special Seasoning'],
    defaultTags: ['Delicious', 'Fresh'],
    defaultDescription: 'Carefully prepared delicious dish made with fresh ingredients, excellent taste and rich nutrition.'
  },
  customerService: {
    title: 'Customer Service',
    online: 'Online',
    offline: 'Offline',
    emptyMessage: 'No messages yet, start chatting~',
    messages: {
      connectionFailed: 'Connection failed, please check network',
      connectionLost: 'Connection lost, please refresh the page',
      invalidFileType: 'Please select an image file',
      fileSizeLimit: 'Image size cannot exceed 10MB',
      imageSent: 'Image sent successfully'
    }
  },
  adminCustomerService: {
    title: 'Customer Service Desk',
    sessionList: 'Session List',
    emptySessions: 'No sessions',
    selectSession: 'Please select a session from the left',
    adminOnly: 'Only administrators can access customer service desk'
  },
  adminDish: {
    title: 'Dish Management',
    subtitle: 'Manage all dishes in the restaurant',
    table: {
      id: 'ID',
      image: 'Image',
      name: 'Dish Name',
      price: 'Price',
      category: 'Category',
      stock: 'Stock',
      actions: 'Actions'
    },
    dialog: {
      addTitle: 'Add Dish',
      editTitle: 'Edit Dish',
      name: 'Dish Name',
      price: 'Price',
      category: 'Category',
      description: 'Description',
      imageUrl: 'Image URL',
      stock: 'Stock',
      status: 'Status'
    },
    validation: {
      nameRequired: 'Please enter dish name',
      priceRequired: 'Please enter price',
      categoryRequired: 'Please select category',
      stockRequired: 'Please enter stock'
    },
    messages: {
      fetchFailed: 'Failed to fetch dish list',
      categoryFetchFailed: 'Failed to fetch category list',
      addSuccess: 'Added successfully',
      updateSuccess: 'Updated successfully',
      deleteConfirm: 'Are you sure to delete this dish?',
      deleteSuccess: 'Deleted successfully'
    }
  },
  adminOrder: {
    title: 'Order Management',
    subtitle: 'Manage all user orders',
    table: {
      orderNo: 'Order No',
      orderTime: 'Order Time',
      receiver: 'Receiver',
      phone: 'Phone',
      amount: 'Amount',
      status: 'Status',
      address: 'Address',
      actions: 'Actions'
    },
    noData: 'No Data',
    pagination: {
      total: 'Total {count}',
      pageSize: '/page',
      goTo: 'Go to',
      page: ''
    }
  },
  route: {
    welcome: 'Welcome',
    categories: 'Categories',
    login: 'Login',
    dishDetail: 'Dish Detail',
    cart: 'Cart',
    orderConfirm: 'Confirm Order',
    orders: 'My Orders',
    orderDetail: 'Order Detail',
    profile: 'Profile',
    customerService: 'Customer Service',
    admin: 'Admin'
  }
}
