# 🚀 PsyChat 部署指南

## Step 2: Anthropic 代理服务部署

### ✅ **DoD (Definition of Done)**

- [x] **安全架构**: API密钥不在客户端暴露
- [x] **代理服务**: Cloudflare Workers 转发请求
- [x] **风控机制**: 基础限流和错误处理
- [x] **流式支持**: SSE 流式响应
- [x] **监控日志**: 请求耗时、错误码、匿名用户ID
- [x] **CORS支持**: 跨域请求处理
- [x] **测试脚本**: 完整的API测试

## 🔧 **部署步骤**

### 1. 部署代理服务

```bash
# 进入代理服务目录
cd proxy-service

# 安装 Wrangler CLI
npm install -g wrangler

# 登录 Cloudflare
wrangler login

# 安装依赖
npm install

# 设置 API 密钥
wrangler secret put ANTHROPIC_API_KEY
# 输入您的 Anthropic API 密钥

# 部署到 Cloudflare Workers
npm run deploy
```

### 2. 获取代理服务URL

部署成功后，您会得到类似这样的URL：
```
https://psychat-anthropic-proxy.your-subdomain.workers.dev
```

### 3. 更新Android应用配置

在 `NetworkModule.kt` 中更新代理服务URL：

```kotlin
private const val PROXY_BASE_URL = "https://your-actual-proxy-url.workers.dev/"
```

### 4. 测试代理服务

```bash
# 使用测试脚本
cd proxy-service
./test.sh
# 或手动测试
curl -X POST "https://your-proxy-url.workers.dev/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [{"role": "user", "content": "Hello!"}],
    "max_tokens": 100
  }'
```

## 🔒 **安全优势**

### ✅ **客户端安全**
- **无API密钥暴露**: 客户端代码中完全没有敏感信息
- **无法逆向**: 即使APK被反编译也无法获取API密钥
- **统一管理**: 所有API调用通过代理服务统一管理

### ✅ **服务端安全**
- **密钥隔离**: API密钥只存在于Cloudflare Workers环境变量
- **访问控制**: 可以添加认证、限流等安全机制
- **审计日志**: 所有请求都有完整的日志记录

## 📊 **监控和日志**

### 内置监控功能
- **请求耗时**: 记录每个请求的处理时间
- **错误码统计**: 统计各种HTTP状态码
- **匿名用户ID**: 基于IP和User-Agent生成匿名标识
- **请求量统计**: 监控API使用情况

### 查看日志
```bash
# 实时日志
wrangler tail

# Cloudflare 控制台
# https://dash.cloudflare.com/workers
```

## 🎯 **性能优化**

### ✅ **已实现**
- **全球边缘节点**: Cloudflare 200+ 数据中心
- **零冷启动**: Workers 无服务器架构
- **流式传输**: 支持实时响应流
- **智能缓存**: 合理的缓存策略

### 🔄 **可扩展功能**
- **KV存储**: 持久化限流和缓存
- **Durable Objects**: 有状态的会话管理
- **Analytics**: 详细的使用分析
- **自定义域名**: 品牌化API端点

## 🧪 **测试验证**

### 基础功能测试
- ✅ 正常对话请求
- ✅ 多轮对话
- ✅ 错误处理
- ✅ CORS预检
- ✅ 流式响应

### 安全测试
- ✅ 无效请求拦截
- ✅ 参数验证
- ✅ 限流机制
- ✅ 错误信息脱敏

## 📱 **Android应用更新**

### ✅ **已完成的修改**
1. **网络配置**: 更新为代理服务URL
2. **API接口**: 简化为只需要请求体
3. **移除密钥**: 不再需要客户端API密钥配置
4. **错误处理**: 适配代理服务的错误格式

### 🔄 **构建和测试**
```bash
# 重新构建Android应用
./gradlew clean build

# 安装到设备
./gradlew installDebug

# 测试对话功能
```

## 🎉 **部署完成检查清单**

- [ ] Cloudflare Workers 部署成功
- [ ] API密钥正确配置
- [ ] 代理服务测试通过
- [ ] Android应用更新代理URL
- [ ] 端到端测试成功
- [ ] 监控日志正常
- [ ] 错误处理验证

## 🚨 **生产环境建议**

1. **自定义域名**: 使用自己的域名而不是workers.dev
2. **访问控制**: 添加API密钥或JWT认证
3. **监控告警**: 设置错误率和延迟告警
4. **备份方案**: 准备多个代理服务实例
5. **成本控制**: 监控Anthropic API使用量

---

