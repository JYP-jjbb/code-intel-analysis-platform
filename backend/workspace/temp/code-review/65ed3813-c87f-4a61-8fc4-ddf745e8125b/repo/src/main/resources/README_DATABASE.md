# 数据库文件说明

## 文件列表

### 1. init.sql
**用途**：全新数据库初始化脚本

**使用场景**：
- 第一次部署系统
- 测试环境重建
- 不需要保留现有数据

**包含内容**：
- 删除并重建所有表
- 创建完整的表结构（包含所有最新字段）
- 插入初始测试数据（admin用户、示例分类和菜品）

**⚠️ 警告**：执行此脚本会删除所有现有数据！

---

### 2. migration.sql
**用途**：数据库迁移脚本（增量更新）

**使用场景**：
- 系统升级
- 添加新功能需要新字段
- **需要保留现有数据**

**包含内容**：
- 为 `tb_user` 表添加默认地址相关字段
- 为 `tb_order` 表添加配送时间相关字段
- 智能检测，避免重复添加
- 数据验证和统计

**✅ 安全**：不会删除任何现有数据！

---

## 快速使用指南

### 场景一：全新部署（无现有数据）

```sql
-- 执行 init.sql
-- 这会创建完整的数据库结构和测试数据
```

### 场景二：系统升级（有现有数据）

```sql
-- 执行 migration.sql
-- 这会添加新字段，保留所有现有数据
```

---

## 数据库配置

确保 `application.properties` 中的配置正确：

```properties
# 数据库连接
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=online_ordering;encrypt=false
spring.datasource.username=your_username
spring.datasource.password=your_password
```

---

## 测试账号（init.sql 创建）

### 管理员账号
- 用户名：`admin`
- 密码：`admin123`
- 角色：管理员

### 普通用户账号
- 用户名：`user`
- 密码：`user123`
- 角色：普通用户

---

## 常见问题

### 无法连接数据库
1. 检查 SQL Server 服务是否启动
2. 检查端口 1433 是否开放
3. 检查用户名密码是否正确

### 执行脚本报错
1. 确认数据库已创建
2. 确认有足够的权限
3. 查看具体错误信息

---

更多详细说明，请查看项目根目录的 `DATABASE_MIGRATION_GUIDE.md`


