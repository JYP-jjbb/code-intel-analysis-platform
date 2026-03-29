# 网上订餐系统

一个基于 Spring Boot 3.2 和 Vue 3 开发的前后端分离订餐系统，支持完整的在线点餐流程。

![Logo](online-ordering-client/public/logo.png)

**后端**
- Spring Boot 3.2.1
- MyBatis 3.0.3
- SQL Server 2016+
- JWT 认证
- Redis（邮箱验证码、限流）
- BCrypt 密码加密
- ZXing（二维码生成）

**前端**
- Vue 3.3.11 + TypeScript
- Element Plus 2.4.4
- Pinia（状态管理）
- Vue Router 4.2.5
- Axios + Vite

## 快速开始

### 环境要求

- JDK 17+
- Node.js 16+
- SQL Server 2016+
- Redis（可选，用于邮箱验证功能）

### 配置数据库

编辑 `src/main/resources/application.properties`：

```properties
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=online_ordering;encrypt=false;trustServerCertificate=true
spring.datasource.username=your_username
spring.datasource.password=your_password
```

### 启动方式

**方式一：一键启动（Windows）**

```bash
startup.bat
```

自动完成数据库初始化、后端启动、前端启动。

**方式二：手动启动**

后端：
```bash
mvnw.cmd spring-boot:run
```

前端：
```bash
cd online-ordering-client
npm install
npm run dev
```

### 访问地址

- 前端页面：http://localhost:3000
- 后端 API：http://localhost:8080
- API 文档：http://localhost:8080/swagger-ui.html

### 测试账号

| 角色 | 用户名 | 密码 |
|------|--------|------|
| 管理员 | admin | admin123 |
| 用户 | user001 | user123 |

## 功能说明

### 基础功能

本系统实现了完整的在线订餐业务流程：

**用户模块**
- 注册登录：用户名密码注册，支持图形验证码防护
- 个人信息：查看和修改个人资料、默认收货地址

**菜品模块**
- 菜品展示：分类浏览，支持按名称搜索
- 菜品详情：查看菜品描述、价格、库存、销量等信息
- 分页查询：每页 10 条记录，提升加载速度

**购物车模块**
- 添加商品：选择菜品和数量加入购物车
- 数量调整：增加、减少商品数量
- 删除清空：单个删除或一键清空购物车
- 实时计算：自动计算商品总价

**订单模块**
- 提交订单：填写收货信息（收货人、电话、地址）
- 订单查询：按时间倒序查看历史订单
- 订单详情：查看商品明细、配送信息、费用明细
- 取消订单：未支付和待接单状态可取消

**管理功能**
- 菜品管理：增删改查，上下架控制
- 分类管理：管理菜品分类
- 订单管理：查看所有订单，更新订单状态
- 用户管理：查看用户列表，禁用/启用账号

### 新增功能 ✨

在基础功能之上，我们实现了以下企业级增强功能：

#### 1. 邮箱验证码注册

集成了完整的邮箱验证码注册流程，提升账号安全性：

**技术实现**
- 验证码生成：随机 6 位数字，使用 SHA-256 + 随机盐值哈希后存储
- Redis 存储：验证码有效期 5 分钟，验证后立即删除（一次性使用）
- 异步发送：使用 `@Async` + 线程池异步发送邮件，不阻塞用户请求
- 邮件服务：支持 SMTP 协议，兼容 QQ 邮箱、网易邮箱等

**安全机制**
- 发送限流：
  - 同一邮箱 60 秒内只能请求 1 次
  - 同一邮箱每天最多请求 10 次
  - 同一 IP 每分钟最多请求 20 次（可配置）
- 验证限制：
  - 同一邮箱验证错误 5 次后锁定 10 分钟
  - 锁定期间无法发送新验证码
- 防暴力破解：验证码使用 hash 存储，无法从 Redis 反推原文

**使用流程**
1. 用户填写邮箱地址
2. 点击"获取邮箱验证码"按钮
3. 后端生成验证码，哈希后存入 Redis
4. 异步发送邮件到用户邮箱
5. 用户输入 6 位验证码完成注册

#### 2. 在线支付功能 💳

实现了真实可用的在线支付功能，支持微信和支付宝两种主流支付方式。

##### 微信支付（Native - 真实商户）

集成了微信支付 Native 模式，适用于 PC 端扫码支付：

**接入说明**
- 支付模式：Native（原生扫码支付）
- 商户类型：真实商户（需要企业资质）
- 申请流程：
  1. 注册微信支付商户号（需营业执照）
  2. 完成商户认证和签约
  3. 获取商户号（mchid）、API 证书、API v3 密钥
  4. 配置支付回调域名

**技术实现**
- SDK：使用微信官方 Java SDK `wechatpay-java`
- API 版本：微信支付 API v3（使用 JSON 和 HTTP 协议）
- 签名算法：使用商户 API 私钥进行请求签名
- 证书验证：使用平台证书验证回调通知的真实性
- 订单查询：定时轮询订单状态，前端每 3 秒查询一次

**支付流程**
1. 用户提交订单后选择微信支付
2. 后端调用微信 Native 下单 API，获取 code_url
3. 使用 ZXing 库将 code_url 生成二维码图片（280×280）
4. 前端展示二维码和支付倒计时（5 分钟）
5. 用户使用微信扫码完成支付
6. 微信服务器发送支付结果通知到回调接口
7. 后端验证签名，更新订单状态，通知前端

**配置示例**
```properties
# 微信支付配置
wechat.pay.app-id=wx1234567890abcdef        # 微信公众号/小程序 AppID
wechat.pay.mch-id=1234567890                 # 商户号
wechat.pay.api-v3-key=your-32-characters-key # API v3 密钥
wechat.pay.merchant-serial-no=1A2B3C4D...    # 商户证书序列号
wechat.pay.private-key-path=/path/to/apiclient_key.pem  # API 私钥路径
wechat.pay.notify-url=https://yourdomain.com/api/payment/wechat/notify
```

##### 支付宝支付（沙箱环境）

集成了支付宝电脑网站支付，使用沙箱环境进行测试：

**接入说明**
- 支付模式：PC 网站支付（alipay.trade.page.pay）
- 环境类型：沙箱环境（无需企业资质，免费测试）
- 沙箱申请：
  1. 登录支付宝开放平台（https://open.alipay.com）
  2. 进入开发者中心 → 沙箱环境
  3. 获取沙箱 AppID、网关、密钥等信息
  4. 下载支付宝沙箱钱包 App 进行测试

**技术实现**
- SDK：使用支付宝官方 Java SDK `alipay-sdk-java`
- 签名方式：RSA2（SHA256WithRSA）
- 通知验证：使用支付宝公钥验证异步通知签名
- 页面跳转：支付后同步跳转到指定页面
- 测试账号：沙箱提供虚拟买家账号和余额

**支付流程**
1. 用户提交订单后选择支付宝支付
2. 后端调用 `alipay.trade.page.pay` 接口生成支付表单
3. 前端自动提交表单，跳转到支付宝收银台页面
4. 用户登录支付宝账号（沙箱提供测试账号）完成支付
5. 支付成功后同步跳转回订单详情页
6. 支付宝服务器异步通知后端回调接口
7. 后端验证签名，更新订单状态

**配置示例**
```properties
# 支付宝沙箱配置
alipay.app-id=2021000000000000              # 沙箱 AppID
alipay.gateway=https://openapi-sandbox.dl.alipaydev.com/gateway.do  # 沙箱网关
alipay.private-key=MIIEvQIBADANBgk...        # 应用私钥
alipay.public-key=MIIBIjANBgkqhk...          # 支付宝公钥
alipay.notify-url=https://yourdomain.com/api/payment/alipay/notify
alipay.return-url=http://localhost:3000/order/detail
```

**沙箱测试账号（示例）**
- 买家账号：abcdef@sandbox.com
- 登录密码：111111
- 支付密码：111111
- 账户余额：默认充足

##### 二维码生成

使用 ZXing 库生成支付二维码：

```xml
<dependency>
    <groupId>com.google.zxing</groupId>
    <artifactId>core</artifactId>
    <version>3.5.3</version>
</dependency>
<dependency>
    <groupId>com.google.zxing</groupId>
    <artifactId>javase</artifactId>
    <version>3.5.3</version>
</dependency>
```

生成 Base64 编码的二维码图片，前端直接使用 `<img>` 标签展示。

##### 支付安全

- 订单验证：支付前验证订单归属和状态
- 金额校验：支付回调时比对订单金额
- 签名验证：使用平台证书/公钥验证通知真实性
- 幂等处理：防止重复处理同一笔支付通知
- 状态机控制：订单状态严格按流程流转

### 其他安全特性

- JWT 认证：无状态的用户身份验证，有效期 24 小时
- BCrypt 加密：密码使用 10 轮加盐 hash，无法反向破解
- 图形验证码：登录时需要输入验证码，防止暴力破解
- 角色权限：区分普通用户和管理员，接口级别权限控制
- 拦截器：登录拦截器验证 Token，排除公开接口（登录、注册等）

## 项目结构

```
online-ordering-server/
│
├── online-ordering-client/              # 【前端项目】Vue 3 + TypeScript
│   ├── public/
│   │   ├── logo.png                    # 项目 Logo
│   │   └── images/                     # 菜品图片
│   │       ├── 500008.jpg             # 宫保鸡丁
│   │       ├── 500022.jpg             # 鱼香肉丝
│   │       └── ...                    # 其他菜品图片
│   │
│   ├── src/
│   │   ├── api/                        # API 接口层
│   │   │   ├── auth.ts                # 认证接口
│   │   │   ├── dish.ts                # 菜品接口
│   │   │   ├── cart.ts                # 购物车接口
│   │   │   ├── order.ts               # 订单接口
│   │   │   └── payment.ts             # 支付接口
│   │   │
│   │   ├── views/                      # 页面组件
│   │   │   ├── WelcomeView.vue        # 欢迎页
│   │   │   ├── HomeView.vue           # 菜品分类页
│   │   │   ├── LoginView.vue          # 登录注册页
│   │   │   ├── CartView.vue           # 购物车页
│   │   │   ├── OrderConfirmView.vue   # 确认订单页
│   │   │   ├── OrderListView.vue      # 订单列表页
│   │   │   ├── OrderDetailView.vue    # 订单详情页
│   │   │   └── DishDetailView.vue     # 菜品详情页
│   │   │
│   │   ├── router/                     # 路由配置
│   │   │   └── index.ts               # 路由定义
│   │   │
│   │   ├── store/                      # Pinia 状态管理
│   │   │   ├── user.ts                # 用户状态
│   │   │   └── cart.ts                # 购物车状态
│   │   │
│   │   ├── types/                      # TypeScript 类型定义
│   │   │   └── index.ts               # 全局类型
│   │   │
│   │   ├── utils/                      # 工具函数
│   │   │   └── request.ts             # Axios 封装
│   │   │
│   │   ├── style/                      # 全局样式
│   │   │   └── theme.css              # 主题变量
│   │   │
│   │   ├── App.vue                     # 根组件
│   │   └── main.ts                     # 入口文件
│   │
│   ├── package.json                    # 前端依赖
│   ├── vite.config.ts                  # Vite 配置
│   └── tsconfig.json                   # TypeScript 配置
│
├── src/main/
│   ├── java/com/mickey/onlineordering/onlineorderingserver/
│   │   │
│   │   ├── controller/                 # 控制器层
│   │   │   ├── AuthController.java    # 认证控制器
│   │   │   ├── DishController.java    # 菜品控制器
│   │   │   ├── CategoryController.java # 分类控制器
│   │   │   ├── CartController.java    # 购物车控制器
│   │   │   ├── OrderController.java   # 订单控制器
│   │   │   └── PaymentController.java # 支付控制器
│   │   │
│   │   ├── service/                    # 业务逻辑层
│   │   │   ├── impl/                   # 实现类
│   │   │   ├── UserService.java
│   │   │   ├── DishService.java
│   │   │   ├── CartService.java
│   │   │   ├── OrderService.java
│   │   │   ├── PaymentService.java    # 支付服务
│   │   │   ├── EmailService.java       # 邮件服务
│   │   │   ├── EmailCodeService.java   # 邮箱验证码服务
│   │   │   └── CaptchaService.java     # 图形验证码服务
│   │   │
│   │   ├── mapper/                     # MyBatis Mapper
│   │   │   ├── UserMapper.java
│   │   │   ├── DishMapper.java
│   │   │   ├── CategoryMapper.java
│   │   │   ├── CartItemMapper.java
│   │   │   ├── OrderMapper.java
│   │   │   └── OrderItemMapper.java
│   │   │
│   │   ├── entity/                     # 实体类
│   │   │   ├── User.java
│   │   │   ├── Dish.java
│   │   │   ├── Category.java
│   │   │   ├── CartItem.java
│   │   │   ├── Order.java
│   │   │   └── OrderItem.java
│   │   │
│   │   ├── dto/                        # 数据传输对象（请求参数）
│   │   │   ├── LoginRequestDto.java
│   │   │   ├── RegisterRequestDto.java
│   │   │   ├── SendEmailCodeRequestDto.java
│   │   │   ├── DishQueryDto.java
│   │   │   ├── CartAddItemDto.java
│   │   │   ├── OrderSubmitDto.java
│   │   │   └── PaymentCreateDto.java
│   │   │
│   │   ├── vo/                         # 视图对象（响应数据）
│   │   │   ├── LoginVo.java
│   │   │   ├── UserProfileVo.java
│   │   │   ├── DishListVo.java
│   │   │   ├── DishDetailVo.java
│   │   │   ├── CartItemVo.java
│   │   │   ├── OrderSummaryVo.java
│   │   │   ├── OrderDetailVo.java
│   │   │   ├── PaymentInfoVo.java
│   │   │   ├── PaymentStatusVo.java
│   │   │   └── CaptchaVo.java
│   │   │
│   │   ├── common/                     # 通用组件
│   │   │   ├── Result.java            # 统一响应封装
│   │   │   ├── PageResult.java        # 分页结果封装
│   │   │   └── ErrorCode.java         # 错误码枚举
│   │   │
│   │   ├── exception/                  # 异常处理
│   │   │   ├── BizException.java      # 业务异常
│   │   │   └── GlobalExceptionHandler.java  # 全局异常处理器
│   │   │
│   │   ├── security/                   # 安全相关
│   │   │   ├── JwtUtil.java           # JWT 工具类
│   │   │   ├── PasswordEncoderUtil.java  # 密码加密工具
│   │   │   └── LoginInterceptor.java  # 登录拦截器
│   │   │
│   │   ├── config/                     # 配置类
│   │   │   ├── WebMvcConfig.java      # Web MVC 配置
│   │   │   ├── RedisConfig.java       # Redis 配置
│   │   │   ├── AsyncConfig.java       # 异步任务配置
│   │   │   └── SwaggerConfig.java     # Swagger 配置
│   │   │
│   │   ├── util/                       # 工具类
│   │   │   ├── CodeHashUtil.java      # 验证码哈希工具
│   │   │   ├── QRCodeUtil.java        # 二维码生成工具
│   │   │   └── BeanCopyUtil.java      # Bean 拷贝工具
│   │   │
│   │   └── OnlineOrderingServerApplication.java  # 启动类
│   │
│   └── resources/
│       ├── application.properties      # 应用配置
│       ├── init.sql                   # 数据库初始化脚本
│       └── mapper/                     # MyBatis XML 映射文件
│           ├── UserMapper.xml
│           ├── DishMapper.xml
│           ├── CategoryMapper.xml
│           ├── CartItemMapper.xml
│           ├── OrderMapper.xml
│           └── OrderItemMapper.xml
│
├── src/test/java/.../                 # 测试类
│   ├── util/
│   │   ├── CodeHashUtilTest.java
│   │   ├── JwtUtilTest.java
│   │   ├── PasswordEncoderUtilTest.java
│   │   └── BeanCopyUtilTest.java
│   ├── service/
│   │   ├── EmailCodeServiceTest.java
│   │   ├── EmailServiceTest.java
│   │   ├── UserServiceTest.java
│   │   ├── CartServiceTest.java
│   │   ├── DishServiceTest.java
│   │   └── OrderServiceTest.java
│   ├── controller/
│   │   └── AuthControllerTest.java
│   └── security/
│       └── CaptchaServiceTest.java
│
├── sql/                                # SQL 脚本
│   └── init.sql                       # 初始化脚本（表结构 + 初始数据）
│
├── startup.bat                         # 一键启动脚本（Windows）
├── pom.xml                            # Maven 配置
├── mvnw.cmd                           # Maven Wrapper (Windows)
├── README.md                          # 项目说明文档
└── HELP.md                            # 帮助文档
```

## 主要 API 接口

| 模块 | 端点 | 方法 | 说明 |
|------|------|------|------|
| 认证 | `/api/auth/login` | POST | 用户登录 |
| 认证 | `/api/auth/register` | POST | 用户注册 |
| 认证 | `/api/auth/email/code` | POST | 发送邮箱验证码 |
| 验证码 | `/api/captcha/generate` | GET | 生成图形验证码 |
| 菜品 | `/api/dishes` | GET | 菜品列表（支持分页、分类筛选） |
| 菜品 | `/api/dishes/{id}` | GET | 菜品详情 |
| 分类 | `/api/categories/active` | GET | 启用的分类列表 |
| 购物车 | `/api/cart` | GET | 查询购物车 |
| 购物车 | `/api/cart/add` | POST | 添加商品到购物车 |
| 购物车 | `/api/cart/update/{id}` | PUT | 更新购物车商品数量 |
| 购物车 | `/api/cart/delete/{id}` | DELETE | 删除购物车商品 |
| 购物车 | `/api/cart/clear` | DELETE | 清空购物车 |
| 订单 | `/api/orders` | POST | 提交订单 |
| 订单 | `/api/orders` | GET | 订单列表（分页） |
| 订单 | `/api/orders/{id}` | GET | 订单详情 |
| 订单 | `/api/orders/{id}/cancel` | PUT | 取消订单 |
| 支付 | `/api/payment/create` | POST | 创建支付（生成二维码） |
| 支付 | `/api/payment/status/{orderId}` | GET | 查询支付状态 |
| 支付 | `/api/payment/wechat/notify` | POST | 微信支付回调通知 |
| 支付 | `/api/payment/alipay/notify` | POST | 支付宝支付回调通知 |

## 配置说明

### 后端配置

`src/main/resources/application.properties` 主要配置项：

```properties
# ==================== 服务器配置 ====================
server.port=8080

# ==================== 数据库配置 ====================
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=online_ordering;encrypt=false;trustServerCertificate=true
spring.datasource.username=test
spring.datasource.password=123456
spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver

# ==================== MyBatis 配置 ====================
mybatis.mapper-locations=classpath:mapper/*.xml
mybatis.type-aliases-package=com.mickey.onlineordering.onlineorderingserver.entity
mybatis.configuration.map-underscore-to-camel-case=true

# ==================== JWT 配置 ====================
jwt.secret=your-secret-key-at-least-32-characters-long
jwt.expiration=86400000

# ==================== Redis 配置（邮箱验证码功能需要）====================
spring.redis.host=localhost
spring.redis.port=6379
spring.redis.database=0
spring.redis.timeout=5000

# ==================== 邮件服务配置（邮箱验证码功能需要）====================
spring.mail.host=smtp.qq.com
spring.mail.port=587
spring.mail.username=your_email@qq.com
spring.mail.password=your_authorization_code
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true

# ==================== 邮箱验证码配置 ====================
email.code.expire-minutes=5
email.code.rate-limit-seconds=60
email.code.daily-limit=10
email.code.ip-minute-limit=20
email.code.max-attempts=5
email.code.lock-minutes=10

# ==================== 微信支付配置（真实商户）====================
wechat.pay.app-id=wx1234567890abcdef
wechat.pay.mch-id=1234567890
wechat.pay.api-v3-key=your-32-characters-api-v3-key
wechat.pay.merchant-serial-no=1A2B3C4D5E6F7G8H9I0J1K2L3M4N5O6P
wechat.pay.private-key-path=classpath:apiclient_key.pem
wechat.pay.notify-url=https://yourdomain.com/api/payment/wechat/notify

# ==================== 支付宝配置（沙箱环境）====================
alipay.app-id=2021000000000000
alipay.gateway=https://openapi-sandbox.dl.alipaydev.com/gateway.do
alipay.private-key=MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC...
alipay.public-key=MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...
alipay.notify-url=https://yourdomain.com/api/payment/alipay/notify
alipay.return-url=http://localhost:3000/order/detail
alipay.sign-type=RSA2
alipay.charset=UTF-8
alipay.format=json
```

**配置说明**

1. **数据库配置**：必填，修改为实际的数据库地址和账号密码
2. **JWT 配置**：必填，建议使用 32 位以上的随机字符串作为密钥
3. **Redis 配置**：可选，如不使用邮箱验证码功能可注释
4. **邮件配置**：可选，QQ 邮箱需要使用授权码而非密码
5. **微信支付配置**：可选，需要申请微信支付商户号
6. **支付宝配置**：可选，使用沙箱环境无需企业资质

### 前端配置

`online-ordering-client/vite.config.ts`：

```typescript
export default defineConfig({
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
```

## 常见问题

### 1. 端口被占用

```bash
# Windows 查找并结束进程
netstat -ano | findstr :8080
taskkill /F /PID <进程ID>
```

### 2. 数据库连接失败

检查：
- SQL Server 服务是否启动
- 用户名和密码是否正确
- 数据库是否已创建（运行 `sql/init.sql`）

### 3. 前端无法访问后端

检查：
- 后端服务是否正常启动（http://localhost:8080）
- `vite.config.ts` 代理配置是否正确
- 浏览器控制台网络请求是否有错误

### 4. 邮箱验证码功能不可用

邮箱验证码功能需要：
- Redis 服务运行（默认端口 6379）
- 配置有效的邮件服务器信息
- 如不需要此功能，可注释掉相关配置

### 5. 微信支付配置问题

微信支付需要：
- 真实的商户号和 API 证书
- 正确配置商户私钥路径
- 配置外网可访问的回调地址
- 测试时可暂时注释微信支付相关配置

### 6. 支付宝沙箱测试

访问支付宝开放平台沙箱：
1. 登录 https://open.alipay.com
2. 进入"开发者中心" → "沙箱环境"
3. 获取沙箱 AppID、网关、密钥
4. 下载"支付宝沙箱钱包" App
5. 使用沙箱提供的买家账号测试支付

## 开发命令

### 后端

```bash
# 编译
mvnw.cmd clean compile

# 运行测试
mvnw.cmd test

# 打包
mvnw.cmd clean package

# 启动
mvnw.cmd spring-boot:run
```

### 前端

```bash
cd online-ordering-client

# 安装依赖
npm install

# 开发模式（支持热更新）
npm run dev

# 构建生产版本
npm run build

# 预览生产版本
npm run preview
```

## 数据库脚本

```bash
# 初始化数据库（创建表和初始数据）
sqlcmd -S localhost -E -i sql\init.sql

# 或在 SQL Server Management Studio 中执行 sql\init.sql
```

## 代码规范

项目遵循《Java代码规范向详细版.txt》中的规范：
- 命名：驼峰命名法，常量使用大写下划线
- 注释：所有类和方法必须有 Javadoc 注释
- 异常：使用自定义业务异常 `BizException`
- 日志：使用 SLF4J，合理使用 debug/info/warn/error 级别
- SQL：避免 N+1 查询，合理使用索引

## 测试

已实现的测试类：
- `CodeHashUtilTest` - 验证码哈希工具测试
- `EmailCodeServiceTest` - 邮箱验证码服务测试
- `AuthControllerTest` - 认证控制器测试
- `UserServiceTest` - 用户服务测试
- `JwtUtilTest` - JWT 工具测试
- `PasswordEncoderUtilTest` - 密码加密工具测试
- `CartServiceTest` - 购物车服务测试
- `DishServiceTest` - 菜品服务测试
- `OrderServiceTest` - 订单服务测试
- `CaptchaServiceTest` - 验证码服务测试
- `EmailServiceTest` - 邮件服务测试
- `BeanCopyUtilTest` - Bean 拷贝工具测试

运行测试：
```bash
mvnw.cmd test
```

## 演示视频

演示视频已作为 Release 附件上传：

- [点击前往 GitHub Releases 页面查看并下载]

## 许可证

MIT License


