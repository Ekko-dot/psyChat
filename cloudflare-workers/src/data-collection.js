/**
 * 数据收集处理模块
 * 处理 /log-batch 端点的批量数据上报
 */

import { corsHeaders } from './cors';
import { sanitizeData, hashContent } from './privacy';

/**
 * 处理批量日志上报
 */
export async function handleLogBatch(request, env, clientIP) {
  try {
    const requestData = await request.json();
    
    // 验证请求格式
    if (!requestData || !requestData.user_id || !requestData.batch) {
      return new Response(JSON.stringify({
        success: false,
        error: 'Invalid request format'
      }), {
        status: 400,
        headers: { 'Content-Type': 'application/json', ...corsHeaders }
      });
    }

    const { user_id, batch, device_info, app_version } = requestData;
    
    // 数据脱敏和验证
    const sanitizedUserId = sanitizeData.userId(user_id);
    const sanitizedDeviceInfo = sanitizeData.deviceInfo(device_info);
    
    // 使用 Queue 进行异步处理（如果配置了 Queue）
    if (env.LOG_QUEUE) {
      await env.LOG_QUEUE.send({
        user_id: sanitizedUserId,
        batch: batch,
        device_info: sanitizedDeviceInfo,
        app_version: app_version,
        client_ip: clientIP,
        timestamp: Date.now()
      });
      
      return new Response(JSON.stringify({
        success: true,
        message: 'Batch queued for processing'
      }), {
        headers: { 'Content-Type': 'application/json', ...corsHeaders }
      });
    }
    
    // 直接处理（如果没有配置 Queue）
    const result = await processBatchData(env, {
      user_id: sanitizedUserId,
      batch: batch,
      device_info: sanitizedDeviceInfo,
      app_version: app_version,
      client_ip: clientIP
    });

    return new Response(JSON.stringify(result), {
      headers: { 'Content-Type': 'application/json', ...corsHeaders }
    });

  } catch (error) {
    console.error('Error in handleLogBatch:', error);
    
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
 * 处理批量数据（核心逻辑）
 */
export async function processBatchData(env, data) {
  if (!env.DB) {
    throw new Error('Database not configured');
  }

  const { user_id, batch, device_info, app_version, client_ip } = data;
  const timestamp = Math.floor(Date.now() / 1000);
  
  let processedCount = 0;
  let errorCount = 0;
  const errors = [];

  try {
    // 开始事务
    await env.DB.prepare('BEGIN TRANSACTION').run();

    // 1. 更新或创建用户记录
    await upsertUser(env, user_id, device_info, app_version, timestamp);

    // 2. 处理批量数据
    for (const item of batch) {
      try {
        switch (item.type) {
          case 'message':
            await processMessage(env, user_id, item.data, timestamp);
            break;
          case 'conversation':
            await processConversation(env, user_id, item.data, timestamp);
            break;
          case 'usage_stat':
            await processUsageStat(env, user_id, item.data);
            break;
          case 'research_event':
            await processResearchEvent(env, user_id, item.data, timestamp);
            break;
          default:
            console.warn('Unknown batch item type:', item.type);
        }
        processedCount++;
      } catch (itemError) {
        console.error('Error processing batch item:', itemError);
        errors.push({
          type: item.type,
          error: itemError.message
        });
        errorCount++;
      }
    }

    // 提交事务
    await env.DB.prepare('COMMIT').run();

    return {
      success: true,
      processed: processedCount,
      errors: errorCount,
      details: errors.length > 0 ? errors : undefined
    };

  } catch (error) {
    // 回滚事务
    await env.DB.prepare('ROLLBACK').run();
    throw error;
  }
}

/**
 * 更新或创建用户记录
 */
async function upsertUser(env, userId, deviceInfo, appVersion, timestamp) {
  await env.DB.prepare(`
    INSERT INTO users (user_id, created_at, last_active, device_info, app_version)
    VALUES (?, ?, ?, ?, ?)
    ON CONFLICT(user_id) DO UPDATE SET
      last_active = ?,
      device_info = COALESCE(?, device_info),
      app_version = COALESCE(?, app_version)
  `).bind(
    userId, timestamp, timestamp, deviceInfo, appVersion,
    timestamp, deviceInfo, appVersion
  ).run();
}

/**
 * 处理消息数据
 */
async function processMessage(env, userId, messageData, timestamp) {
  const {
    message_id,
    conversation_id,
    content,
    is_from_user,
    tokens_in,
    tokens_out,
    model_name,
    is_voice_input,
    asr_audio_path,
    response_time_ms,
    error_message
  } = messageData;

  // 内容哈希化（隐私保护）
  const contentHash = content ? hashContent(content) : null;

  await env.DB.prepare(`
    INSERT OR REPLACE INTO messages (
      message_id, conversation_id, user_id, content_hash, is_from_user,
      timestamp, tokens_in, tokens_out, model_name, is_voice_input,
      asr_audio_path, response_time_ms, error_message
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
  `).bind(
    message_id, conversation_id, userId, contentHash, is_from_user,
    timestamp, tokens_in, tokens_out, model_name, is_voice_input,
    asr_audio_path, response_time_ms, error_message
  ).run();

  // 更新用户统计
  await env.DB.prepare(`
    UPDATE users SET 
      total_messages = total_messages + 1,
      total_tokens = total_tokens + COALESCE(?, 0) + COALESCE(?, 0)
    WHERE user_id = ?
  `).bind(tokens_in || 0, tokens_out || 0, userId).run();
}

/**
 * 处理对话数据
 */
async function processConversation(env, userId, conversationData, timestamp) {
  const {
    conversation_id,
    title,
    message_count,
    total_tokens_in,
    total_tokens_out,
    is_archived
  } = conversationData;

  await env.DB.prepare(`
    INSERT OR REPLACE INTO conversations (
      conversation_id, user_id, title, created_at, updated_at,
      message_count, total_tokens_in, total_tokens_out, is_archived
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
  `).bind(
    conversation_id, userId, title, timestamp, timestamp,
    message_count, total_tokens_in, total_tokens_out, is_archived
  ).run();
}

/**
 * 处理使用统计数据
 */
async function processUsageStat(env, userId, statData) {
  const {
    date,
    messages_sent,
    tokens_consumed,
    api_calls,
    errors,
    avg_response_time_ms
  } = statData;

  await env.DB.prepare(`
    INSERT OR REPLACE INTO usage_stats (
      stat_id, user_id, date, messages_sent, tokens_consumed,
      api_calls, errors, avg_response_time_ms
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
  `).bind(
    `${userId}_${date}`, userId, date, messages_sent, tokens_consumed,
    api_calls, errors, avg_response_time_ms
  ).run();
}

/**
 * 处理研究事件数据
 */
async function processResearchEvent(env, userId, eventData, timestamp) {
  const {
    event_id,
    event_type,
    event_data,
    session_id
  } = eventData;

  await env.DB.prepare(`
    INSERT OR REPLACE INTO research_events (
      event_id, user_id, event_type, event_data, timestamp, session_id
    ) VALUES (?, ?, ?, ?, ?, ?)
  `).bind(
    event_id, userId, event_type, JSON.stringify(event_data), timestamp, session_id
  ).run();
}
