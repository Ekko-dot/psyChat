/**
 * Anthropic API 代理服务 - Cloudflare Workers
 * 安全地转发客户端请求到Anthropic API
 */

// 配置常量
const ANTHROPIC_API_URL = 'https://api.anthropic.com/v1/messages';
const ANTHROPIC_VERSION = '2023-06-01';

// CORS 头部
const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, Authorization',
  'Access-Control-Max-Age': '86400',
};

// 错误响应
const createErrorResponse = (message, status = 400) => {
  return new Response(JSON.stringify({ 
    error: { message, type: 'proxy_error' } 
  }), {
    status,
    headers: { 
      'Content-Type': 'application/json',
      ...CORS_HEADERS 
    }
  });
};

// 记录请求日志
const logRequest = (request, userId, startTime, status, error = null) => {
  const duration = Date.now() - startTime;
  console.log(JSON.stringify({
    timestamp: new Date().toISOString(),
    userId: userId || 'anonymous',
    method: request.method,
    url: request.url,
    status,
    duration,
    error,
    userAgent: request.headers.get('User-Agent')
  }));
};

// 生成匿名用户ID
const generateUserId = (request) => {
  const ip = request.headers.get('CF-Connecting-IP') || 'unknown';
  const userAgent = request.headers.get('User-Agent') || 'unknown';
  return btoa(`${ip}-${userAgent}`).substring(0, 16);
};

// 验证请求体
const validateRequest = (body) => {
  if (!body.messages || !Array.isArray(body.messages)) {
    throw new Error('messages 字段必须是数组');
  }
  
  if (body.messages.length === 0) {
    throw new Error('messages 不能为空');
  }
  
  // 验证消息格式
  for (const message of body.messages) {
    if (!message.role || !message.content) {
      throw new Error('每条消息必须包含 role 和 content 字段');
    }
    if (!['user', 'assistant'].includes(message.role)) {
      throw new Error('role 必须是 user 或 assistant');
    }
  }
  
  // 限制最大token数
  if (body.max_tokens && body.max_tokens > 4096) {
    throw new Error('max_tokens 不能超过 4096');
  }
};

// 基础风控检查
const rateLimit = async (userId, env) => {
  // 简单的内存限流（生产环境建议使用 KV 存储）
  const key = `rate_limit_${userId}`;
  const now = Date.now();
  const windowMs = 60 * 1000; // 1分钟窗口
  const maxRequests = 20; // 每分钟最多20次请求
  
  // 这里可以集成 Cloudflare KV 或 Durable Objects 做持久化限流
  // 当前仅做演示，实际部署时需要实现真正的限流逻辑
  
  return true; // 暂时允许所有请求
};

// 处理流式响应
const handleStreamResponse = async (response, writable) => {
  const reader = response.body.getReader();
  const writer = writable.getWriter();
  
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      
      await writer.write(value);
    }
  } finally {
    await writer.close();
  }
};

// 主处理函数
export default {
  async fetch(request, env, ctx) {
    const startTime = Date.now();
    const userId = generateUserId(request);
    
    // 处理 CORS 预检请求
    if (request.method === 'OPTIONS') {
      return new Response(null, { headers: CORS_HEADERS });
    }
    
    // 只允许 POST 请求
    if (request.method !== 'POST') {
      logRequest(request, userId, startTime, 405);
      return createErrorResponse('只支持 POST 请求', 405);
    }
    
    // 检查路径
    const url = new URL(request.url);
    if (url.pathname !== '/chat') {
      logRequest(request, userId, startTime, 404);
      return createErrorResponse('端点不存在', 404);
    }
    
    try {
      // 风控检查
      const rateLimitPassed = await rateLimit(userId, env);
      if (!rateLimitPassed) {
        logRequest(request, userId, startTime, 429, 'Rate limit exceeded');
        return createErrorResponse('请求过于频繁，请稍后再试', 429);
      }
      
      // 解析请求体
      let requestBody;
      try {
        requestBody = await request.json();
      } catch (e) {
        logRequest(request, userId, startTime, 400, 'Invalid JSON');
        return createErrorResponse('请求体必须是有效的 JSON');
      }
      
      // 验证请求
      validateRequest(requestBody);
      
      // 构建发送给 Anthropic 的请求
      const anthropicRequest = {
        model: requestBody.model || 'claude-3-7-sonnet-20250219',
        max_tokens: Math.min(requestBody.max_tokens || 1024, 4096),
        messages: requestBody.messages,
        stream: requestBody.stream || false
      };
      
      // 检查 API Key
      if (!env.ANTHROPIC_API_KEY) {
        logRequest(request, userId, startTime, 500, 'Missing API key');
        return createErrorResponse('服务配置错误', 500);
      }
      
      // 发送请求到 Anthropic
      const anthropicResponse = await fetch(ANTHROPIC_API_URL, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'x-api-key': env.ANTHROPIC_API_KEY,
          'anthropic-version': ANTHROPIC_VERSION,
        },
        body: JSON.stringify(anthropicRequest)
      });
      
      // 记录成功请求
      logRequest(request, userId, startTime, anthropicResponse.status);
      
      // 处理流式响应
      if (requestBody.stream && anthropicResponse.ok) {
        return new Response(anthropicResponse.body, {
          status: anthropicResponse.status,
          headers: {
            'Content-Type': 'text/event-stream',
            'Cache-Control': 'no-cache',
            'Connection': 'keep-alive',
            ...CORS_HEADERS
          }
        });
      }
      
      // 处理普通响应
      const responseData = await anthropicResponse.text();
      
      return new Response(responseData, {
        status: anthropicResponse.status,
        headers: {
          'Content-Type': 'application/json',
          ...CORS_HEADERS
        }
      });
      
    } catch (error) {
      logRequest(request, userId, startTime, 500, error.message);
      console.error('代理服务错误:', error);
      return createErrorResponse(error.message || '服务器内部错误', 500);
    }
  }
};
