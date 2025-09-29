# 🚀 Cloudflare Workers 数据收集服务部署指南

## 📋 **概述**

扩展现有的 PsyChat Cloudflare Workers 代理服务，新增数据收集功能：

- ✅ **原有功能**: `/chat` - Anthropic API 代理
- 🆕 **数据收集**: `/log-batch` - 批量上报对话与研究事件  
- 🆕 **用户管理**: `/delete-user`, `/export-user` - GDPR合规
- 🆕 **存储架构**: D1 + Queues + KV 削峰解耦
- 🆕 **安全特性**: 鉴权、限流、CORS、数据脱敏

---

## 🛠️ **部署步骤**

### 1. **环境准备**

```bash
# 安装 Wrangler CLI
npm install -g wrangler

# 登录 Cloudflare
wrangler auth login

# 进入项目目录
cd /Users/euanh/psyChat/cloudflare-workers
```

### 2. **创建 D1 数据库**

```bash
# 创建 D1 数据库
wrangler d1 create psychat-logs

# 记录返回的 database_id，更新 wrangler.toml
# 初始化数据库表结构
wrangler d1 execute psychat-logs --file=./schema.sql
```

### 3. **创建 KV 命名空间**

```bash
# 创建 KV 存储（用于速率限制）
wrangler kv namespace create "RATE_LIMIT_KV"

# 记录返回的 id，更新 wrangler.toml
```

### 4. **创建 Queue（可选）**

```bash
# 创建消息队列（用于异步处理）
wrangler queues create psychat-log-queue
```

### 5. **配置环境变量**

```bash
# 设置 Anthropic API Key
wrangler secret put ANTHROPIC_API_KEY

# 设置用户验证密钥
wrangler secret put USER_SECRET
```

### 6. **更新配置文件**

编辑 `wrangler.toml`，填入实际的 ID：

```toml
[[d1_databases]]
binding = "DB"
database_name = "psychat-logs"
database_id = "your-actual-d1-database-id"

[[kv_namespaces]]
binding = "RATE_LIMIT_KV"
id = "your-actual-kv-namespace-id"
```

### 7. **部署服务**

```bash
# 部署到生产环境
npm run deploy

# 或部署到测试环境
npm run deploy:staging
```

---

## 📊 **API 端点说明**

### **原有端点**

#### `POST /chat`
- **功能**: Anthropic API 代理（保持不变）
- **请求**: `{ "messages": [...] }`
- **响应**: Anthropic API 响应

### **新增端点**

#### `POST /log-batch`
- **功能**: 批量数据收集
- **请求格式**:
```json
{
  "user_id": "uuid-string",
  "batch": [
    {
      "type": "message",
      "data": {
        "message_id": "msg-123",
        "conversation_id": "conv-456",
        "content": "用户消息内容",
        "is_from_user": true,
        "timestamp": 1640995200,
        "tokens_in": 10,
        "tokens_out": 25,
        "model_name": "claude-3-7-sonnet-20250219"
      }
    }
  ],
  "device_info": "{\"platform\":\"Android\"}",
  "app_version": "1.0.0"
}
```

#### `POST /delete-user`
- **功能**: 删除用户数据（GDPR合规）
- **请求**: `{ "user_id": "uuid", "verification_token": "token" }`

#### `POST /export-user`  
- **功能**: 导出用户数据（GDPR合规）
- **请求**: `{ "user_id": "uuid", "verification_token": "token" }`

#### `GET /health`
- **功能**: 健康检查
- **响应**: `{ "status": "healthy", "timestamp": 1640995200 }`

---

## 🗄️ **数据库架构**

### **核心表结构**

```sql
-- 用户表（伪匿名化）
users (user_id, created_at, last_active, device_info, app_version, total_messages, total_tokens)

-- 对话表
conversations (conversation_id, user_id, title, created_at, updated_at, message_count, total_tokens_in, total_tokens_out)

-- 消息表（内容哈希化）
messages (message_id, conversation_id, user_id, content_hash, is_from_user, timestamp, tokens_in, tokens_out, model_name)

-- 使用统计表
usage_stats (stat_id, user_id, date, messages_sent, tokens_consumed, api_calls, errors, avg_response_time_ms)

-- 研究事件表
research_events (event_id, user_id, event_type, event_data, timestamp, session_id)

-- 系统日志表
system_logs (log_id, level, message, user_id, timestamp, metadata)
```

### **数据保留策略**

- **消息记录**: 90天
- **研究事件**: 30天  
- **系统日志**: 7天
- **使用统计**: 永久保留（聚合数据）

---

## 🔒 **隐私与安全**

### **数据脱敏措施**

1. **用户ID**: UUID格式，伪匿名化
2. **消息内容**: 哈希化存储，不保存原文
3. **设备信息**: 泛化处理（如 iPhone14 → iPhone）
4. **IP地址**: 前缀保留（如 192.168.x.x）

### **安全特性**

- ✅ **速率限制**: 每分钟100请求/IP
- ✅ **CORS配置**: 支持跨域请求
- ✅ **输入验证**: 严格的请求格式检查
- ✅ **错误处理**: 不泄露敏感信息
- ✅ **访问日志**: 完整的操作审计

### **GDPR合规**

- ✅ **删除权**: `/delete-user` 完全删除用户数据
- ✅ **可携带权**: `/export-user` 导出用户数据
- ✅ **数据最小化**: 只收集必要的分析数据
- ✅ **透明度**: 清晰的数据收集说明

---

## 📈 **监控与维护**

### **日常监控**

```bash
# 查看实时日志
wrangler tail

# 检查数据库状态
wrangler d1 execute psychat-logs --command="SELECT COUNT(*) FROM messages"

# 查看KV使用情况
wrangler kv:key list --binding=RATE_LIMIT_KV
```

### **定期维护**

- **每日**: Cron自动清理过期数据
- **每周**: 检查错误日志和性能指标
- **每月**: 生成使用统计报告

### **性能优化**

- **批量处理**: 使用Queue削峰解耦
- **索引优化**: 关键查询字段建立索引
- **缓存策略**: KV存储热点数据
- **连接池**: D1自动管理连接

---

## 🔧 **故障排除**

### **常见问题**

1. **D1数据库连接失败**
   ```bash
   # 检查数据库状态
   wrangler d1 info psychat-logs
   ```

2. **速率限制过于严格**
   ```bash
   # 清理KV中的限制记录
   wrangler kv:key delete "rate_limit:IP" --binding=RATE_LIMIT_KV
   ```

3. **Queue消息堆积**
   ```bash
   # 检查队列状态
   wrangler queues list
   ```

### **调试模式**

```bash
# 本地开发模式
npm run dev

# 查看详细日志
wrangler tail --format=pretty
```

---

## 📊 **成本估算**

### **500-1000用户规模**

- **Workers**: ~$5/月（100万请求）
- **D1**: ~$1/月（1GB存储 + 100万读写）
- **KV**: ~$0.5/月（1GB存储 + 100万操作）
- **Queue**: ~$0.4/月（100万消息）

**总计**: ~$7/月

### **扩展性**

- **10K用户**: ~$25/月
- **100K用户**: ~$150/月
- **自动扩展**: 无需手动干预

---

## ✅ **部署检查清单**

- [ ] D1数据库创建并初始化
- [ ] KV命名空间创建
- [ ] Queue创建（可选）
- [ ] 环境变量配置
- [ ] wrangler.toml更新
- [ ] 代码部署成功
- [ ] 健康检查通过
- [ ] Android端配置更新
- [ ] 监控告警设置

**🎉 部署完成！现在您的PsyChat应用具备了企业级的数据收集和分析能力！**
