-- ============================================================
-- 在线订餐系统 - 数据库初始化脚本
-- ============================================================

-- 创建数据库
IF NOT EXISTS (SELECT * FROM sys.databases WHERE name = 'online_ordering')
BEGIN
    CREATE DATABASE online_ordering;
END
GO

USE online_ordering;
GO

-- ============================================================
-- 用户表
-- ============================================================
IF OBJECT_ID('tb_user', 'U') IS NOT NULL
    DROP TABLE tb_user;
GO

CREATE TABLE tb_user (
    id BIGINT PRIMARY KEY IDENTITY(1,1),
    username NVARCHAR(50) NOT NULL UNIQUE,
    password NVARCHAR(200) NOT NULL,
    role NVARCHAR(20) NOT NULL DEFAULT 'user',
    phone NVARCHAR(20),
    address NVARCHAR(200),
    default_address NVARCHAR(200),
    default_receiver_name NVARCHAR(50),
    default_receiver_phone NVARCHAR(20),
    avatar NVARCHAR(200),
    email NVARCHAR(100),
    status INT NOT NULL DEFAULT 1,
    create_time DATETIME2 NOT NULL DEFAULT GETDATE(),
    update_time DATETIME2 NOT NULL DEFAULT GETDATE()
);
GO

-- ============================================================
-- 分类表
-- ============================================================
IF OBJECT_ID('tb_category', 'U') IS NOT NULL
    DROP TABLE tb_category;
GO

CREATE TABLE tb_category (
    id BIGINT PRIMARY KEY IDENTITY(1,1),
    name NVARCHAR(50) NOT NULL,
    sort INT NOT NULL DEFAULT 0,
    description NVARCHAR(200),
    status INT NOT NULL DEFAULT 1,
    create_time DATETIME2 NOT NULL DEFAULT GETDATE(),
    update_time DATETIME2 NOT NULL DEFAULT GETDATE()
);
GO

-- ============================================================
-- 菜品表
-- ============================================================
IF OBJECT_ID('tb_dish', 'U') IS NOT NULL
    DROP TABLE tb_dish;
GO

CREATE TABLE tb_dish (
    id BIGINT PRIMARY KEY IDENTITY(1,1),
    name NVARCHAR(100) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    category_id BIGINT NOT NULL,
    description NVARCHAR(500),
    image_url NVARCHAR(200),
    stock INT NOT NULL DEFAULT 0,
    sales INT NOT NULL DEFAULT 0,
    status INT NOT NULL DEFAULT 1,
    create_time DATETIME2 NOT NULL DEFAULT GETDATE(),
    update_time DATETIME2 NOT NULL DEFAULT GETDATE(),
    FOREIGN KEY (category_id) REFERENCES tb_category(id)
);
GO

-- ============================================================
-- 购物车表
-- ============================================================
IF OBJECT_ID('tb_cart_item', 'U') IS NOT NULL
    DROP TABLE tb_cart_item;
GO

CREATE TABLE tb_cart_item (
    id BIGINT PRIMARY KEY IDENTITY(1,1),
    user_id BIGINT NOT NULL,
    dish_id BIGINT NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    price DECIMAL(10,2) NOT NULL,
    create_time DATETIME2 NOT NULL DEFAULT GETDATE(),
    update_time DATETIME2 NOT NULL DEFAULT GETDATE(),
    FOREIGN KEY (user_id) REFERENCES tb_user(id),
    FOREIGN KEY (dish_id) REFERENCES tb_dish(id)
);
GO

-- ============================================================
-- 订单表
-- ============================================================
IF OBJECT_ID('tb_order', 'U') IS NOT NULL
    DROP TABLE tb_order;
GO

CREATE TABLE tb_order (
    id BIGINT PRIMARY KEY IDENTITY(1,1),
    order_no NVARCHAR(50) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    total_amount DECIMAL(10,2) NOT NULL,
    status INT NOT NULL DEFAULT 0,
    address NVARCHAR(200) NOT NULL,
    receiver_name NVARCHAR(50) NOT NULL,
    receiver_phone NVARCHAR(20) NOT NULL,
    remark NVARCHAR(200),
    payment_method NVARCHAR(20) NOT NULL,
    pay_time DATETIME2,
    delivery_time DATETIME2,
    estimated_delivery_time DATETIME2,
    create_time DATETIME2 NOT NULL DEFAULT GETDATE(),
    update_time DATETIME2 NOT NULL DEFAULT GETDATE(),
    FOREIGN KEY (user_id) REFERENCES tb_user(id)
);
GO

-- ============================================================
-- 订单明细表
-- ============================================================
IF OBJECT_ID('tb_order_item', 'U') IS NOT NULL
    DROP TABLE tb_order_item;
GO

CREATE TABLE tb_order_item (
    id BIGINT PRIMARY KEY IDENTITY(1,1),
    order_id BIGINT NOT NULL,
    dish_id BIGINT NOT NULL,
    dish_name NVARCHAR(100) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    quantity INT NOT NULL,
    subtotal DECIMAL(10,2) NOT NULL,
    image_url NVARCHAR(200),
    create_time DATETIME2 NOT NULL DEFAULT GETDATE(),
    FOREIGN KEY (order_id) REFERENCES tb_order(id)
);
GO

-- ============================================================
-- 地址表
-- ============================================================
IF OBJECT_ID('tb_address', 'U') IS NOT NULL
    DROP TABLE tb_address;
GO

CREATE TABLE tb_address (
    id BIGINT PRIMARY KEY IDENTITY(1,1),
    user_id BIGINT NOT NULL,
    receiver_name NVARCHAR(50) NOT NULL,
    receiver_phone NVARCHAR(20) NOT NULL,
    address NVARCHAR(200) NOT NULL,
    is_default BIT NOT NULL DEFAULT 0,
    created_at DATETIME2 NOT NULL DEFAULT GETDATE(),
    updated_at DATETIME2 NOT NULL DEFAULT GETDATE(),
    FOREIGN KEY (user_id) REFERENCES tb_user(id)
);
GO

-- ============================================================
-- 创建索引
-- ============================================================
CREATE INDEX idx_user_username ON tb_user(username);
CREATE INDEX idx_dish_category_id ON tb_dish(category_id);
CREATE INDEX idx_dish_status ON tb_dish(status);
CREATE INDEX idx_cart_user_id ON tb_cart_item(user_id);
CREATE INDEX idx_cart_dish_id ON tb_cart_item(dish_id);
CREATE INDEX idx_order_user_id ON tb_order(user_id);
CREATE INDEX idx_order_order_no ON tb_order(order_no);
CREATE INDEX idx_order_status ON tb_order(status);
CREATE INDEX idx_order_item_order_id ON tb_order_item(order_id);
CREATE INDEX idx_address_user_id ON tb_address(user_id);
CREATE INDEX idx_address_is_default ON tb_address(is_default);
GO

-- ============================================================
-- 客服聊天功能 - 数据库脚本
-- ============================================================
IF OBJECT_ID('tb_customer_service_message', 'U') IS NOT NULL
DROP TABLE tb_customer_service_message;
GO

CREATE TABLE tb_customer_service_message (
    id BIGINT PRIMARY KEY IDENTITY(1,1),
    session_id NVARCHAR(100) NOT NULL,
    sender_id BIGINT NOT NULL,
    sender_name NVARCHAR(50) NOT NULL,
    sender_role NVARCHAR(20) NOT NULL,
    receiver_id BIGINT,
    message_type NVARCHAR(20) NOT NULL DEFAULT 'text',
    content NVARCHAR(MAX) NOT NULL,
    is_read INT NOT NULL DEFAULT 0,
    create_time DATETIME2 NOT NULL DEFAULT GETDATE()
);
GO

-- 索引
CREATE INDEX idx_session_id   ON tb_customer_service_message(session_id);
CREATE INDEX idx_sender_id    ON tb_customer_service_message(sender_id);
CREATE INDEX idx_create_time  ON tb_customer_service_message(create_time);
GO

PRINT '客服消息表创建完成！';
GO


-- ============================================================
-- 插入初始数据
-- ============================================================

-- 插入管理员账号 (密码: admin123)
INSERT INTO tb_user (username, password, role, phone, email, status, create_time, update_time)
VALUES ('admin', '$2a$10$jug.9nloJm5rZiq.9JvmRuA8enrqDsjnDGymPPJ.j4xNQpjqAbuai', 'admin', '13800138000', 'admin@example.com', 1, GETDATE(), GETDATE());
GO

-- 插入测试用户 (密码: user123)
INSERT INTO tb_user (username, password, role, phone, email, status, create_time, update_time)
VALUES ('user001', '$2a$10$1ZGn8xqDGWi8YeC3bXt7sOLF0/Wu6qGhpq2bZHK/TV34xv6RmsFi6', 'user', '13800138001', 'user001@example.com', 1, GETDATE(), GETDATE());
GO

-- 插入分类数据 (使用 N 前缀确保正确处理 Unicode 字符)
INSERT INTO tb_category (name, sort, description, status, create_time, update_time)
VALUES 
(N'热菜', 1, N'各式热菜', 1, GETDATE(), GETDATE()),
(N'凉菜', 2, N'各式凉菜', 1, GETDATE(), GETDATE()),
(N'主食', 3, N'米饭面食', 1, GETDATE(), GETDATE()),
(N'汤品', 4, N'营养汤品', 1, GETDATE(), GETDATE()),
(N'饮品', 5, N'各式饮品', 1, GETDATE(), GETDATE()),
(N'甜点', 6, N'精致甜点', 1, GETDATE(), GETDATE()),
(N'餐具', 7, N'一次性餐具', 1, GETDATE(), GETDATE());
GO

-- 插入菜品数据
INSERT INTO tb_dish (name, price, category_id, description, image_url, stock, sales, status, create_time, update_time)
VALUES 
(N'宫保鸡丁', 38.00, 1, N'经典川菜，鸡肉鲜嫩，花生酥脆', '/images/宫保鸡丁.jpg', 100, 58, 1, GETDATE(), GETDATE()),
(N'鱼香肉丝', 32.00, 1, N'酸甜可口，色泽红亮', '/images/鱼香肉丝.jpg', 100, 45, 1, GETDATE(), GETDATE()),
(N'麻婆豆腐', 28.00, 1, N'麻辣鲜香，豆腐嫩滑', '/images/麻婆豆腐.jpg', 100, 72, 1, GETDATE(), GETDATE()),
(N'糖醋里脊', 45.00, 1, N'外酥里嫩，酸甜适中', '/images/糖醋里脊.jpg', 100, 38, 1, GETDATE(), GETDATE()),
(N'酸辣土豆丝', 18.00, 2, N'爽脆可口，酸辣开胃', '/images/酸辣土豆丝.jpg', 100, 65, 1, GETDATE(), GETDATE()),
(N'凉拌黄瓜', 15.00, 2, N'清爽解腻，夏日必备', '/images/凉拌黄瓜.jpg', 100, 42, 1, GETDATE(), GETDATE()),
(N'皮蛋豆腐', 20.00, 2, N'咸香滑嫩，营养丰富', '/images/皮蛋豆腐.jpg', 100, 28, 1, GETDATE(), GETDATE()),
(N'白米饭', 3.00, 3, N'东北优质大米', '/images/白米饭.jpg', 200, 156, 1, GETDATE(), GETDATE()),
(N'炒饭', 15.00, 3, N'粒粒分明，香味浓郁', '/images/炒饭.jpg', 100, 89, 1, GETDATE(), GETDATE()),
(N'手工水饺', 25.00, 3, N'皮薄馅大，鲜香美味', '/images/手工水饺.jpg', 100, 54, 1, GETDATE(), GETDATE()),
(N'番茄蛋汤', 18.00, 4, N'酸甜可口，营养丰富', '/images/番茄蛋汤.jpg', 100, 37, 1, GETDATE(), GETDATE()),
(N'紫菜蛋汤', 15.00, 4, N'清淡鲜美，补碘佳品', '/images/紫菜蛋汤.jpg', 100, 41, 1, GETDATE(), GETDATE()),
(N'可乐', 6.00, 5, N'冰爽可乐', '/images/可乐.jpg', 200, 128, 1, GETDATE(), GETDATE()),
(N'雪碧', 6.00, 5, N'清凉雪碧', '/images/雪碧.jpg', 200, 115, 1, GETDATE(), GETDATE()),
(N'酸奶', 8.00, 5, N'浓郁酸奶', '/images/酸奶.jpg', 150, 98, 1, GETDATE(), GETDATE()),
(N'蛋挞', 12.00, 6, N'外酥里嫩，奶香浓郁', '/images/蛋挞.jpg', 100, 76, 1, GETDATE(), GETDATE()),
(N'提拉米苏', 28.00, 6, N'意式经典甜品', '/images/提拉米苏.jpg', 50, 34, 1, GETDATE(), GETDATE()),
(N'抹茶蛋糕', 22.00, 6, N'清新抹茶香，口感细腻', '/images/抹茶蛋糕.jpg', 80, 52, 1, GETDATE(), GETDATE()),
(N'一次性餐具', 0.01, 7, N'一次性餐具（筷子/勺子/纸巾）', '/images/一次性餐具.png', 100, 99, 1, GETDATE(), GETDATE());
GO

--插入地址数据
INSERT INTO tb_address (user_id, receiver_name, receiver_phone, address, is_default, created_at, updated_at)
VALUES
(2, N'Mickey', N'13255677788', N'黑龙江省黑河市爱辉区光明小区2单元101', 1, '2025-12-20 21:26:43.1366667', '2025-12-19 20:00:21.376533'),
(2, N'Donald', N'15541236868', N'北京市朝阳区三里屯街道花园小区4单元602', 0, '2025-12-20 21:29:22.6833333', '2025-12-20 21:29:22.6833333');
GO

--插入订单数据
INSERT INTO tb_order
(order_no, user_id, total_amount, status, address, receiver_name, receiver_phone, remark, payment_method, pay_time, delivery_time, estimated_delivery_time, create_time, update_time)
VALUES
    ('ORD20251210101215000001', 2, 38.00, 1, N'北京市朝阳区三里屯街道花园小区4单元602', N'Donald', N'15541236868', N'', 'alipay',  '2025-12-10 10:13:05.1200000', NULL, NULL, '2025-12-10 10:12:15.0000000', '2025-12-10 10:13:05.1200000'),
    ('ORD20251210184530000002', 2, 32.00, 1, N'黑龙江省黑河市爱辉区光明小区2单元101', N'Mickey', N'13255677788', N'', 'wechat',  '2025-12-10 18:46:02.4300000', NULL, NULL, '2025-12-10 18:45:30.0000000', '2025-12-10 18:46:02.4300000'),
    ('ORD20251211113042000003', 2, 28.00, 0, N'北京市朝阳区三里屯街道花园小区4单元602', N'Donald', N'15541236868', N'', 'alipay',  NULL, NULL, NULL, '2025-12-11 11:30:42.0000000', '2025-12-11 11:30:42.0000000'),
    ('ORD20251211195518000004', 2, 45.00, 1, N'黑龙江省黑河市爱辉区光明小区2单元101', N'Mickey', N'13255677788', N'', 'alipay',  '2025-12-11 19:56:11.7800000', NULL, NULL, '2025-12-11 19:55:18.0000000', '2025-12-11 19:56:11.7800000'),
    ('ORD20251212120807000005', 2, 18.00, 1, N'北京市朝阳区三里屯街道花园小区4单元602', N'Donald', N'15541236868', N'', 'wechat',  '2025-12-12 12:08:49.6600000', NULL, NULL, '2025-12-12 12:08:07.0000000', '2025-12-12 12:08:49.6600000'),
    ('ORD20251212193025000006', 2, 15.00, 0, N'黑龙江省黑河市爱辉区光明小区2单元101', N'Mickey', N'13255677788', N'', 'wechat',  NULL, NULL, NULL, '2025-12-12 19:30:25.0000000', '2025-12-12 19:30:25.0000000'),
    ('ORD20251213110355000007', 2, 20.00, 1, N'北京市朝阳区三里屯街道花园小区4单元602', N'Donald', N'15541236868', N'', 'alipay',  '2025-12-13 11:04:33.9100000', NULL, NULL, '2025-12-13 11:03:55.0000000', '2025-12-13 11:04:33.9100000'),
    ('ORD20251213184512000008', 2, 3.00,  1, N'黑龙江省黑河市爱辉区光明小区2单元101', N'Mickey', N'13255677788', N'', 'wechat',  '2025-12-13 18:45:40.2500000', NULL, NULL, '2025-12-13 18:45:12.0000000', '2025-12-13 18:45:40.2500000'),
    ('ORD20251214121233000009', 2, 15.00, 1, N'北京市朝阳区三里屯街道花园小区4单元602', N'Donald', N'15541236868', N'', 'alipay',  '2025-12-14 12:13:22.3400000', NULL, NULL, '2025-12-14 12:12:33.0000000', '2025-12-14 12:13:22.3400000'),
    ('ORD20251214194045000010', 2, 25.00, 1, N'黑龙江省黑河市爱辉区光明小区2单元101', N'Mickey', N'13255677788', N'', 'wechat',  '2025-12-14 19:41:30.1700000', NULL, NULL, '2025-12-14 19:40:45.0000000', '2025-12-14 19:41:30.1700000'),
    ('ORD20251215110801000011', 2, 18.00, 0, N'北京市朝阳区三里屯街道花园小区4单元602', N'Donald', N'15541236868', N'', 'alipay',  NULL, NULL, NULL, '2025-12-15 11:08:01.0000000', '2025-12-15 11:08:01.0000000'),
    ('ORD20251215195826000012', 2, 15.00, 1, N'黑龙江省黑河市爱辉区光明小区2单元101', N'Mickey', N'13255677788', N'', 'wechat',  '2025-12-15 19:59:10.5400000', NULL, NULL, '2025-12-15 19:58:26.0000000', '2025-12-15 19:59:10.5400000'),
    ('ORD20251216122218000013', 2, 6.00,  1, N'北京市朝阳区三里屯街道花园小区4单元602', N'Donald', N'15541236868', N'', 'alipay',  '2025-12-16 12:23:00.1100000', NULL, NULL, '2025-12-16 12:22:18.0000000', '2025-12-16 12:23:00.1100000'),
    ('ORD20251217184635000014', 2, 70.00, 1, N'黑龙江省黑河市爱辉区光明小区2单元101', N'Mickey', N'13255677788', N'组合订单：热菜+饮品+甜点', 'alipay', '2025-12-17 18:47:28.8800000', NULL, NULL, '2025-12-17 18:46:35.0000000', '2025-12-17 18:47:28.8800000'),
    ('ORD20251219103012000015', 2, 0.01,  1, N'北京市朝阳区三里屯街道花园小区4单元602', N'Donald', N'15541236868', N'一次性餐具', 'wechat', '2025-12-19 10:31:02.9900000', NULL, NULL, '2025-12-19 10:30:12.0000000', '2025-12-19 10:31:02.9900000');
GO

--插入订单明细数据
INSERT INTO tb_order_item (order_id, dish_id, dish_name, price, quantity, subtotal, image_url, create_time)
VALUES
-- order_id=1 total=38.00
(1,  1,  N'宫保鸡丁',   38.00, 1, 38.00, '/images/宫保鸡丁.jpg',   '2025-12-10 10:12:15.0000000'),
-- order_id=2 total=32.00
(2,  2,  N'鱼香肉丝',   32.00, 1, 32.00, '/images/鱼香肉丝.jpg',   '2025-12-10 18:45:30.0000000'),
-- order_id=3 total=28.00
(3,  17, N'提拉米苏',   28.00, 1, 28.00, '/images/提拉米苏.jpg',   '2025-12-11 11:30:42.0000000'),
-- order_id=4 total=45.00
(4,  4,  N'糖醋里脊',   45.00, 1, 45.00, '/images/糖醋里脊.jpg',   '2025-12-11 19:55:18.0000000'),
-- order_id=5 total=18.00
(5,  11, N'番茄蛋汤',   18.00, 1, 18.00, '/images/番茄蛋汤.jpg',   '2025-12-12 12:08:07.0000000'),
-- order_id=6 total=15.00
(6,  12, N'紫菜蛋汤',   15.00, 1, 15.00, '/images/紫菜蛋汤.jpg',   '2025-12-12 19:30:25.0000000'),
-- order_id=7 total=20.00
(7,  7,  N'皮蛋豆腐',   20.00, 1, 20.00, '/images/皮蛋豆腐.jpg',   '2025-12-13 11:03:55.0000000'),
-- order_id=8 total=3.00
(8,  8,  N'白米饭',      3.00, 1,  3.00, '/images/白米饭.jpg',      '2025-12-13 18:45:12.0000000'),
-- order_id=9 total=15.00
(9,  9,  N'炒饭',       15.00, 1, 15.00, '/images/炒饭.jpg',       '2025-12-14 12:12:33.0000000'),
-- order_id=10 total=25.00
(10, 10, N'手工水饺',   25.00, 1, 25.00, '/images/手工水饺.jpg',   '2025-12-14 19:40:45.0000000'),
-- order_id=11 total=18.00
(11, 5,  N'酸辣土豆丝', 18.00, 1, 18.00, '/images/酸辣土豆丝.jpg', '2025-12-15 11:08:01.0000000'),
-- order_id=12 total=15.00
(12, 6,  N'凉拌黄瓜',   15.00, 1, 15.00, '/images/凉拌黄瓜.jpg',   '2025-12-15 19:58:26.0000000'),
-- order_id=13 total=6.00
(13, 13, N'可乐',        6.00, 1,  6.00, '/images/可乐.jpg',        '2025-12-16 12:22:18.0000000'),
-- order_id=14 total=70.00（用“组合”看起来合理：28 + 22 + 12 + 8 = 70）
(14, 17, N'提拉米苏',   28.00, 1, 28.00, '/images/提拉米苏.jpg',   '2025-12-17 18:46:35.0000000'),
(14, 18, N'抹茶蛋糕',   22.00, 1, 22.00, '/images/抹茶蛋糕.jpg',   '2025-12-17 18:46:35.0000000'),
(14, 16, N'蛋挞',       12.00, 1, 12.00, '/images/蛋挞.jpg',       '2025-12-17 18:46:35.0000000'),
(14, 15, N'酸奶',        8.00, 1,  8.00, '/images/酸奶.jpg',        '2025-12-17 18:46:35.0000000'),
-- order_id=15 total=0.01
(15, 19, N'一次性餐具',  0.01, 1,  0.01, '/images/一次性餐具.png', '2025-12-19 10:30:12.0000000');
GO

PRINT '数据库初始化完成！';
GO
