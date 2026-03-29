# 用 ngrok 配置微信 / 支付宝支付回调（组员必看）

这份说明的目标：**每个组员在自己电脑上跑后端时，都能让微信支付/支付宝沙箱的异步回调打到自己的本机**。

> 本项目后端监听端口默认是 **8080**。
> 你如果改成了 8088 / 9090，下面命令里把端口号改成你自己的即可。

---

## 0. 你需要准备什么

- 能正常启动后端（Spring Boot）并能访问：
  - `http://localhost:8080/`
- 已注册并登录 ngrok（建议 GitHub / Google 登录）
- 已在 ngrok Dashboard 获取到 **authtoken**

---

## 1. 安装并登录 ngrok（只需做一次）

1) 下载并解压 `ngrok.exe`

2) 在 `ngrok.exe` 所在目录打开命令行，执行（把 token 换成你自己的）：

```bat
ngrok config add-authtoken YOUR_AUTHTOKEN
```

看到类似 `Authtoken saved to configuration file ...` 即成功。

---

## 2. 启动内网穿透（每次启动后端都要做）

后端启动后，再开一个新的命令行窗口执行：

```bat
ngrok http 8080
```

ngrok 会输出一个公网地址，形如：

- `https://xxxx-xxxx.ngrok-free.dev`

**复制这个 https 的域名（只要域名，不要带路径）**。

✅ 正确示例（只到域名结束）：

- `https://unnihilistic-diagonally-mack.ngrok-free.dev`

❌ 错误示例（不要把 /api/... 写进去）：

- `https://unnihilistic-diagonally-mack.ngrok-free.dev/api/pay/alipay/notify`

---

## 3. 修改项目配置：把 notify-domain 改成自己的 ngrok 域名

打开后端配置文件：

- `src/main/resources/alipay.properties`
- `src/main/resources/wxpay.properties`

把下面 2 个配置改成你刚复制的 ngrok 域名：

### 3.1 支付宝（沙箱）

文件：`src/main/resources/alipay.properties`

```properties
# 最终回调地址 = {alipay.notify-domain}/api/pay/alipay/notify
alipay.notify-domain=https://xxxxxx.ngrok-free.dev
```

### 3.2 微信支付

文件：`src/main/resources/wxpay.properties`

```properties
# 最终回调地址 = wxpay.notify-domain + /api/pay/wechat/notify
wxpay.notify-domain=https://xxxxxx.ngrok-free.dev
```

---

## 4. 回调地址最终会是什么？（用于确认）

只要你填对了 `notify-domain`，后端会自动拼接成：

- 支付宝回调：
  - `https://你的域名/api/pay/alipay/notify`
- 微信回调：
  - `https://你的域名/api/pay/wechat/notify`

---

## 5. 常见问题（最容易踩的坑）

### Q1：我已经付款了，但页面一直“待支付”，订单状态不更新

几乎都是 **异步回调没打通**，排查顺序：

1. ngrok 必须一直开着（不要关窗口）
2. `notify-domain` 必须是 **https://xxxx.ngrok-free.dev**（不要带路径）
3. 付款后看后端日志，应该能看到类似：
   - `Alipay notify ...`
   - 或微信的 notify 相关日志
4. 如果支付宝回调到达但报 `验签失败`：
   - 多半是 `alipay.alipay-public-key` 配错（必须用“支付宝公钥”，不是“应用公钥”）

### Q2：ngrok 提示 endpoint 已经 online

说明你已经开了一个相同的隧道：

- 先把旧的 ngrok 窗口关掉，再重新 `ngrok http 8080`

### Q3：我想同时映射前端(3000)和后端(8080)

免费版通常**不能同时复用同一个固定域名**，建议：

- 支付回调只需要后端（8080）公网可访问即可
- 前端不必映射到公网

---

## 6. 推荐的组员操作顺序（最稳）

1. 启动后端（Spring Boot）
2. `ngrok http 8080`
3. 复制 ngrok 的 `https://...ngrok-free.dev`
4. 修改：
   - `alipay.notify-domain=`
   - `wxpay.notify-domain=`
5. **重启后端**（让配置生效）
6. 下单支付测试

