# PsyChat Anthropic 代理服务

安全的 Anthropic API 代理服务，部署在 Cloudflare Workers 上。

## 🚀 快速部署

### 1. 安装依赖
```bash
cd proxy-service
npm install
```

### 2. 登录 Cloudflare
```bash
npx wrangler login
```

### 3. 设置 API 密钥
```bash
npx wrangler secret put ANTHROPIC_API_KEY
# 输入您的 Anthropic API 密钥
```

### 4. 部署服务
```bash
npm run deploy
```

## 📡 API 接口

### POST /chat

**请求体:**
```json
{
  "model": "claude-3-7-sonnet-20250219",
  "messages": [
    {
      "role": "user", 
      "content": "Hello!"
    }
  ],
  "max_tokens": 1024,
  "stream": false
}
```

**响应:**
```json
{
  "content": [
    {
      "type": "text",
      "text": "Hello! How can I help you today?"
    }
  ],
  "id": "msg_123",
  "model": "claude-3-7-sonnet-20250219",
  "role": "assistant",
  "stop_reason": "end_turn",
  "type": "message",
  "usage": {
    "input_tokens": 10,
    "output_tokens": 25
  }
}
```

## 🔒 安全特性

- ✅ **API 密钥隐藏**: 客户端无法访问真实 API 密钥
- ✅ **CORS 支持**: 支持跨域请求
- ✅ **请求验证**: 验证请求格式和参数
- ✅ **基础限流**: 防止滥用（可扩展）
- ✅ **错误处理**: 统一的错误响应格式
- ✅ **日志记录**: 请求日志和性能监控

## 🧪 本地测试

```bash
# 启动本地开发服务器
npm run dev

# 测试 API
curl -X POST http://localhost:8787/chat \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [{"role": "user", "content": "Hello!"}],
    "max_tokens": 100
  }'
```

## 📊 监控和日志

```bash
# 查看实时日志
npm run logs

# 查看 Cloudflare 控制台
# https://dash.cloudflare.com/workers
```

## 🔧 配置选项

### 环境变量
- `ANTHROPIC_API_KEY`: Anthropic API 密钥 (必需)
- `ENVIRONMENT`: 环境标识 (production/development)

### 限流配置
- 默认: 每分钟 20 次请求
- 可通过 KV 存储实现持久化限流

### 支持的模型
- `claude-3-7-sonnet-20250219` (默认)
- `claude-3-5-sonnet-20241022`
- `claude-3-5-haiku-20241022`
- `claude-3-opus-20240229`

## 🚨 生产环境建议

1. **自定义域名**: 配置自己的域名而不是 workers.dev
2. **KV 存储**: 启用 KV 存储实现持久化限流
3. **监控告警**: 设置 Cloudflare 告警规则
4. **访问控制**: 添加 API 密钥或 JWT 验证
5. **日志分析**: 集成日志分析服务

## 📈 性能优化

- ✅ **边缘计算**: Cloudflare 全球边缘节点
- ✅ **零冷启动**: Workers 无服务器架构
- ✅ **流式传输**: 支持 SSE 流式响应
- ✅ **缓存优化**: 合理的缓存策略

## 🔄 更新部署

```bash
# 更新代码后重新部署
npm run deploy

# 查看部署状态
npx wrangler deployments list
```
