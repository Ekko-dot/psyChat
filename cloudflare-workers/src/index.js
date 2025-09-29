/**
 * PsyChat Cloudflare Workers 代理服务
 * 功能：
 * 1. /chat - Anthropic API 代理（原有功能）
 * 2. /log-batch - 批量数据收集
 * 3. /delete-user - 用户数据删除（GDPR合规）
 * 4. /export-user - 用户数据导出（GDPR合规）
 */

import { handleChatRequest } from './chat-proxy';
import { handleLogBatch } from './data-collection';
import { handleUserManagement } from './user-management';
import { rateLimiter } from './rate-limiter';
import { corsHeaders, handleCORS } from './cors';

export default {
  async fetch(request, env, ctx) {
    // 处理 CORS 预检请求
    if (request.method === 'OPTIONS') {
      return handleCORS();
    }

    const url = new URL(request.url);
    const path = url.pathname;

    try {
      // 基础安全检查
      const clientIP = request.headers.get('CF-Connecting-IP');
      const userAgent = request.headers.get('User-Agent');
      
      // 简单的安全过滤
      if (!userAgent || userAgent.includes('bot') || userAgent.includes('crawler')) {
        return new Response('Forbidden', { status: 403 });
      }

      // 路由处理
      switch (path) {
        case '/chat':
          // 原有的 Anthropic 代理功能
          return await handleChatRequest(request, env, clientIP);

        case '/log-batch':
          // 新增：批量数据收集
          if (request.method !== 'POST') {
            return new Response('Method not allowed', { status: 405 });
          }
          
          // 应用速率限制
          const rateLimitResult = await rateLimiter.check(clientIP, env);
          if (!rateLimitResult.allowed) {
            return new Response('Rate limit exceeded', { 
              status: 429,
              headers: {
                'Retry-After': rateLimitResult.retryAfter.toString(),
                ...corsHeaders
              }
            });
          }

          return await handleLogBatch(request, env, clientIP);

        case '/delete-user':
          // GDPR 合规：删除用户数据
          if (request.method !== 'POST') {
            return new Response('Method not allowed', { status: 405 });
          }
          return await handleUserManagement(request, env, 'delete');

        case '/export-user':
          // GDPR 合规：导出用户数据
          if (request.method !== 'POST') {
            return new Response('Method not allowed', { status: 405 });
          }
          return await handleUserManagement(request, env, 'export');

        case '/health':
          // 健康检查端点
          return new Response(JSON.stringify({
            status: 'healthy',
            timestamp: Date.now(),
            version: '2.0.0'
          }), {
            headers: { 'Content-Type': 'application/json', ...corsHeaders }
          });

        default:
          return new Response('Not Found', { status: 404 });
      }

    } catch (error) {
      console.error('Worker error:', error);
      
      // 记录系统错误到 D1
      try {
        await logSystemError(env, error, clientIP, path);
      } catch (logError) {
        console.error('Failed to log error:', logError);
      }

      return new Response('Internal Server Error', { 
        status: 500,
        headers: corsHeaders
      });
    }
  },

  // Cron 触发器：定时清理过期数据
  async scheduled(controller, env, ctx) {
    console.log('Running scheduled cleanup task');
    
    try {
      // 清理过期数据
      await cleanupExpiredData(env);
      
      // 生成每日统计报告
      await generateDailyStats(env);
      
      console.log('Scheduled cleanup completed successfully');
    } catch (error) {
      console.error('Scheduled cleanup failed:', error);
      await logSystemError(env, error, 'system', 'cron-cleanup');
    }
  }
};

/**
 * 记录系统错误到 D1
 */
async function logSystemError(env, error, clientIP, path) {
  if (!env.DB) return;

  const logEntry = {
    log_id: crypto.randomUUID(),
    level: 'ERROR',
    message: error.message || 'Unknown error',
    user_id: null,
    timestamp: Math.floor(Date.now() / 1000),
    metadata: JSON.stringify({
      stack: error.stack,
      clientIP: clientIP,
      path: path,
      userAgent: error.userAgent || 'unknown'
    })
  };

  try {
    await env.DB.prepare(`
      INSERT INTO system_logs (log_id, level, message, user_id, timestamp, metadata)
      VALUES (?, ?, ?, ?, ?, ?)
    `).bind(
      logEntry.log_id,
      logEntry.level,
      logEntry.message,
      logEntry.user_id,
      logEntry.timestamp,
      logEntry.metadata
    ).run();
  } catch (dbError) {
    console.error('Failed to log to D1:', dbError);
  }
}

/**
 * 清理过期数据
 */
async function cleanupExpiredData(env) {
  if (!env.DB) return;

  const now = Math.floor(Date.now() / 1000);
  const thirtyDaysAgo = now - (30 * 24 * 60 * 60);
  const ninetyDaysAgo = now - (90 * 24 * 60 * 60);
  const sevenDaysAgo = now - (7 * 24 * 60 * 60);

  // 清理过期消息（90天）
  await env.DB.prepare(`
    DELETE FROM messages WHERE timestamp < ?
  `).bind(ninetyDaysAgo).run();

  // 清理过期研究事件（30天）
  await env.DB.prepare(`
    DELETE FROM research_events WHERE timestamp < ?
  `).bind(thirtyDaysAgo).run();

  // 清理过期系统日志（7天）
  await env.DB.prepare(`
    DELETE FROM system_logs WHERE timestamp < ?
  `).bind(sevenDaysAgo).run();

  console.log('Expired data cleanup completed');
}

/**
 * 生成每日统计报告
 */
async function generateDailyStats(env) {
  if (!env.DB) return;

  const today = new Date().toISOString().split('T')[0]; // YYYY-MM-DD
  const todayStart = Math.floor(new Date(today).getTime() / 1000);
  const todayEnd = todayStart + (24 * 60 * 60);

  // 统计今日活跃用户
  const activeUsers = await env.DB.prepare(`
    SELECT COUNT(DISTINCT user_id) as count
    FROM messages 
    WHERE timestamp >= ? AND timestamp < ?
  `).bind(todayStart, todayEnd).first();

  // 统计今日消息数
  const messageCount = await env.DB.prepare(`
    SELECT COUNT(*) as count
    FROM messages 
    WHERE timestamp >= ? AND timestamp < ?
  `).bind(todayStart, todayEnd).first();

  // 统计今日token使用量
  const tokenUsage = await env.DB.prepare(`
    SELECT 
      SUM(tokens_in) as tokens_in,
      SUM(tokens_out) as tokens_out
    FROM messages 
    WHERE timestamp >= ? AND timestamp < ?
  `).bind(todayStart, todayEnd).first();

  // 记录统计信息
  await env.DB.prepare(`
    INSERT OR REPLACE INTO system_logs (log_id, level, message, timestamp, metadata)
    VALUES (?, 'INFO', 'Daily stats', ?, ?)
  `).bind(
    crypto.randomUUID(),
    Math.floor(Date.now() / 1000),
    JSON.stringify({
      date: today,
      active_users: activeUsers.count,
      messages: messageCount.count,
      tokens_in: tokenUsage.tokens_in || 0,
      tokens_out: tokenUsage.tokens_out || 0
    })
  ).run();

  console.log(`Daily stats generated for ${today}`);
}
