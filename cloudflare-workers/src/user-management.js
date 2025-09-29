/**
 * 用户管理模块 - GDPR合规
 * 处理用户数据删除和导出请求
 */

import { corsHeaders } from './cors';
import { sanitizeData } from './privacy';

/**
 * 处理用户管理请求
 */
export async function handleUserManagement(request, env, action) {
  try {
    const requestData = await request.json();
    
    if (!requestData || !requestData.user_id) {
      return new Response(JSON.stringify({
        success: false,
        error: 'User ID is required'
      }), {
        status: 400,
        headers: { 'Content-Type': 'application/json', ...corsHeaders }
      });
    }

    const userId = sanitizeData.userId(requestData.user_id);
    const verificationToken = requestData.verification_token;

    // 简单的验证机制（生产环境应使用更强的验证）
    if (!verificationToken || !await verifyUserToken(env, userId, verificationToken)) {
      return new Response(JSON.stringify({
        success: false,
        error: 'Invalid verification token'
      }), {
        status: 403,
        headers: { 'Content-Type': 'application/json', ...corsHeaders }
      });
    }

    let result;
    switch (action) {
      case 'delete':
        result = await deleteUserData(env, userId);
        break;
      case 'export':
        result = await exportUserData(env, userId);
        break;
      default:
        throw new Error('Invalid action');
    }

    return new Response(JSON.stringify(result), {
      headers: { 'Content-Type': 'application/json', ...corsHeaders }
    });

  } catch (error) {
    console.error('Error in user management:', error);
    
    return new Response(JSON.stringify({
      success: false,
      error: 'Internal server error'
    }), {
      status: 500,
      headers: { 'Content-Type': 'application/json', ...corsHeaders }
    });
  }
}

/**
 * 验证用户令牌（简单实现）
 */
async function verifyUserToken(env, userId, token) {
  // 简单的令牌验证：基于用户ID和时间戳的哈希
  // 生产环境应使用更安全的JWT或其他验证机制
  
  try {
    const [timestamp, hash] = token.split('.');
    const expectedHash = await generateUserHash(userId, timestamp);
    
    // 检查令牌是否在有效期内（24小时）
    const tokenTime = parseInt(timestamp);
    const now = Math.floor(Date.now() / 1000);
    const isValid = (now - tokenTime) < (24 * 60 * 60) && hash === expectedHash;
    
    return isValid;
  } catch (e) {
    return false;
  }
}

/**
 * 生成用户哈希
 */
async function generateUserHash(userId, timestamp) {
  const data = `${userId}:${timestamp}:${env.USER_SECRET || 'default-secret'}`;
  const encoder = new TextEncoder();
  const dataBuffer = encoder.encode(data);
  const hashBuffer = await crypto.subtle.digest('SHA-256', dataBuffer);
  const hashArray = new Uint8Array(hashBuffer);
  return Array.from(hashArray).map(b => b.toString(16).padStart(2, '0')).join('').substring(0, 16);
}

/**
 * 删除用户数据（GDPR 删除权）
 */
async function deleteUserData(env, userId) {
  if (!env.DB) {
    throw new Error('Database not configured');
  }

  try {
    // 开始事务
    await env.DB.prepare('BEGIN TRANSACTION').run();

    // 记录删除操作
    await env.DB.prepare(`
      INSERT INTO system_logs (log_id, level, message, user_id, timestamp, metadata)
      VALUES (?, 'INFO', 'User data deletion requested', ?, ?, ?)
    `).bind(
      crypto.randomUUID(),
      userId,
      Math.floor(Date.now() / 1000),
      JSON.stringify({ action: 'delete', reason: 'GDPR_request' })
    ).run();

    // 删除用户相关的所有数据
    const deletionResults = {};

    // 删除消息
    const messagesResult = await env.DB.prepare(`
      DELETE FROM messages WHERE user_id = ?
    `).bind(userId).run();
    deletionResults.messages = messagesResult.changes;

    // 删除对话
    const conversationsResult = await env.DB.prepare(`
      DELETE FROM conversations WHERE user_id = ?
    `).bind(userId).run();
    deletionResults.conversations = conversationsResult.changes;

    // 删除使用统计
    const statsResult = await env.DB.prepare(`
      DELETE FROM usage_stats WHERE user_id = ?
    `).bind(userId).run();
    deletionResults.usage_stats = statsResult.changes;

    // 删除研究事件
    const eventsResult = await env.DB.prepare(`
      DELETE FROM research_events WHERE user_id = ?
    `).bind(userId).run();
    deletionResults.research_events = eventsResult.changes;

    // 删除用户记录
    const userResult = await env.DB.prepare(`
      DELETE FROM users WHERE user_id = ?
    `).bind(userId).run();
    deletionResults.user = userResult.changes;

    // 提交事务
    await env.DB.prepare('COMMIT').run();

    // 记录删除完成
    await env.DB.prepare(`
      INSERT INTO system_logs (log_id, level, message, timestamp, metadata)
      VALUES (?, 'INFO', 'User data deletion completed', ?, ?)
    `).bind(
      crypto.randomUUID(),
      Math.floor(Date.now() / 1000),
      JSON.stringify({ 
        action: 'delete_completed',
        user_id_hash: sanitizeData.hashString(userId),
        deleted_records: deletionResults
      })
    ).run();

    return {
      success: true,
      message: 'User data deleted successfully',
      deleted_records: deletionResults
    };

  } catch (error) {
    // 回滚事务
    await env.DB.prepare('ROLLBACK').run();
    throw error;
  }
}

/**
 * 导出用户数据（GDPR 数据可携带权）
 */
async function exportUserData(env, userId) {
  if (!env.DB) {
    throw new Error('Database not configured');
  }

  try {
    const exportData = {
      export_timestamp: new Date().toISOString(),
      user_id: userId,
      data: {}
    };

    // 导出用户基本信息
    const user = await env.DB.prepare(`
      SELECT * FROM users WHERE user_id = ?
    `).bind(userId).first();
    
    if (user) {
      exportData.data.user_profile = {
        created_at: new Date(user.created_at * 1000).toISOString(),
        last_active: new Date(user.last_active * 1000).toISOString(),
        total_messages: user.total_messages,
        total_tokens: user.total_tokens,
        app_version: user.app_version
        // 不导出device_info（可能包含敏感信息）
      };
    }

    // 导出对话列表
    const conversations = await env.DB.prepare(`
      SELECT conversation_id, title, created_at, updated_at, message_count,
             total_tokens_in, total_tokens_out, is_archived
      FROM conversations WHERE user_id = ?
      ORDER BY created_at DESC
    `).bind(userId).all();

    exportData.data.conversations = conversations.results.map(conv => ({
      conversation_id: conv.conversation_id,
      title: conv.title,
      created_at: new Date(conv.created_at * 1000).toISOString(),
      updated_at: new Date(conv.updated_at * 1000).toISOString(),
      message_count: conv.message_count,
      total_tokens_in: conv.total_tokens_in,
      total_tokens_out: conv.total_tokens_out,
      is_archived: conv.is_archived
    }));

    // 导出消息统计（不包含实际内容，只有元数据）
    const messages = await env.DB.prepare(`
      SELECT message_id, conversation_id, is_from_user, timestamp,
             tokens_in, tokens_out, model_name, is_voice_input,
             response_time_ms, error_message
      FROM messages WHERE user_id = ?
      ORDER BY timestamp DESC
      LIMIT 1000
    `).bind(userId).all();

    exportData.data.messages = messages.results.map(msg => ({
      message_id: msg.message_id,
      conversation_id: msg.conversation_id,
      is_from_user: msg.is_from_user,
      timestamp: new Date(msg.timestamp * 1000).toISOString(),
      tokens_in: msg.tokens_in,
      tokens_out: msg.tokens_out,
      model_name: msg.model_name,
      is_voice_input: msg.is_voice_input,
      response_time_ms: msg.response_time_ms,
      error_message: msg.error_message
      // 不导出content_hash（隐私保护）
    }));

    // 导出使用统计
    const usageStats = await env.DB.prepare(`
      SELECT date, messages_sent, tokens_consumed, api_calls, errors, avg_response_time_ms
      FROM usage_stats WHERE user_id = ?
      ORDER BY date DESC
    `).bind(userId).all();

    exportData.data.usage_statistics = usageStats.results;

    // 导出研究事件（脱敏后）
    const researchEvents = await env.DB.prepare(`
      SELECT event_type, timestamp, session_id
      FROM research_events WHERE user_id = ?
      ORDER BY timestamp DESC
      LIMIT 500
    `).bind(userId).all();

    exportData.data.research_events = researchEvents.results.map(event => ({
      event_type: event.event_type,
      timestamp: new Date(event.timestamp * 1000).toISOString(),
      session_id: event.session_id
      // 不导出event_data（可能包含敏感信息）
    }));

    // 记录导出操作
    await env.DB.prepare(`
      INSERT INTO system_logs (log_id, level, message, user_id, timestamp, metadata)
      VALUES (?, 'INFO', 'User data export completed', ?, ?, ?)
    `).bind(
      crypto.randomUUID(),
      userId,
      Math.floor(Date.now() / 1000),
      JSON.stringify({ 
        action: 'export',
        exported_records: {
          conversations: exportData.data.conversations.length,
          messages: exportData.data.messages.length,
          usage_stats: exportData.data.usage_statistics.length,
          research_events: exportData.data.research_events.length
        }
      })
    ).run();

    return {
      success: true,
      message: 'User data exported successfully',
      data: exportData
    };

  } catch (error) {
    console.error('Error exporting user data:', error);
    throw error;
  }
}
