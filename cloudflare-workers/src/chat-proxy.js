/**
 * 聊天代理模块
 * 处理原有的 /chat 端点功能
 */

import { corsHeaders } from './cors';

/**
 * 处理聊天请求（原有功能保持不变）
 */
export async function handleChatRequest(request, env, clientIP) {
  try {
    const requestData = await request.json();
    
    // 构建发送到 Anthropic 的请求
    const anthropicRequest = {
      model: "claude-3-7-sonnet-20250219",
      max_tokens: 4096,
      messages: requestData.messages || []
    };

    // 调用 Anthropic API
    const response = await fetch('https://api.anthropic.com/v1/messages', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'x-api-key': env.ANTHROPIC_API_KEY,
        'anthropic-version': '2023-06-01'
      },
      body: JSON.stringify(anthropicRequest)
    });

    if (!response.ok) {
      const errorText = await response.text();
      console.error('Anthropic API error:', response.status, errorText);
      
      return new Response(JSON.stringify({
        error: 'API request failed',
        status: response.status
      }), {
        status: response.status,
        headers: { 'Content-Type': 'application/json', ...corsHeaders }
      });
    }

    const responseData = await response.json();
    
    // 记录API调用（用于统计）
    if (env.DB) {
      try {
        await logApiCall(env, clientIP, requestData, responseData);
      } catch (logError) {
        console.error('Failed to log API call:', logError);
      }
    }

    return new Response(JSON.stringify(responseData), {
      headers: { 'Content-Type': 'application/json', ...corsHeaders }
    });

  } catch (error) {
    console.error('Chat proxy error:', error);
    
    return new Response(JSON.stringify({
      error: 'Internal server error',
      message: error.message
    }), {
      status: 500,
      headers: { 'Content-Type': 'application/json', ...corsHeaders }
    });
  }
}

/**
 * 记录API调用统计
 */
async function logApiCall(env, clientIP, request, response) {
  const timestamp = Math.floor(Date.now() / 1000);
  
  // 计算token使用量
  const tokensIn = estimateTokens(JSON.stringify(request.messages));
  const tokensOut = response.usage?.output_tokens || estimateTokens(JSON.stringify(response.content));
  
  // 记录到系统日志
  await env.DB.prepare(`
    INSERT INTO system_logs (log_id, level, message, timestamp, metadata)
    VALUES (?, 'INFO', 'API call', ?, ?)
  `).bind(
    crypto.randomUUID(),
    timestamp,
    JSON.stringify({
      client_ip: clientIP.substring(0, 10) + 'xxx', // IP脱敏
      tokens_in: tokensIn,
      tokens_out: tokensOut,
      model: request.model || 'claude-3-7-sonnet-20250219',
      response_time: Date.now() - (timestamp * 1000)
    })
  ).run();
}

/**
 * 简单的token估算
 */
function estimateTokens(text) {
  if (!text) return 0;
  // 粗略估算：英文约4字符=1token，中文约1.5字符=1token
  const chineseChars = (text.match(/[\u4e00-\u9fff]/g) || []).length;
  const otherChars = text.length - chineseChars;
  
  return Math.ceil(chineseChars / 1.5 + otherChars / 4);
}
