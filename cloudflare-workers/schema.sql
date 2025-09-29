-- PsyChat 数据收集 D1 数据库表结构
-- 支持 500-1000 用户的结构化日志存储

-- 用户表（伪匿名化）
CREATE TABLE IF NOT EXISTS users (
    user_id TEXT PRIMARY KEY,           -- 客户端生成的UUID（伪匿名）
    created_at INTEGER NOT NULL,       -- Unix时间戳
    last_active INTEGER NOT NULL,      -- 最后活跃时间
    device_info TEXT,                  -- 设备信息（脱敏后）
    app_version TEXT,                  -- 应用版本
    total_messages INTEGER DEFAULT 0,  -- 总消息数
    total_tokens INTEGER DEFAULT 0     -- 总token使用量
);

-- 对话表
CREATE TABLE IF NOT EXISTS conversations (
    conversation_id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    title TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    message_count INTEGER DEFAULT 0,
    total_tokens_in INTEGER DEFAULT 0,
    total_tokens_out INTEGER DEFAULT 0,
    is_archived BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

-- 消息表（核心数据）
CREATE TABLE IF NOT EXISTS messages (
    message_id TEXT PRIMARY KEY,
    conversation_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    content_hash TEXT,                 -- 内容哈希（隐私保护）
    is_from_user BOOLEAN NOT NULL,
    timestamp INTEGER NOT NULL,
    tokens_in INTEGER,
    tokens_out INTEGER,
    model_name TEXT,
    is_voice_input BOOLEAN DEFAULT FALSE,
    asr_audio_path TEXT,              -- 音频文件路径（如果有）
    response_time_ms INTEGER,         -- 响应时间
    error_message TEXT,               -- 错误信息（如果有）
    FOREIGN KEY (conversation_id) REFERENCES conversations(conversation_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

-- 使用统计表
CREATE TABLE IF NOT EXISTS usage_stats (
    stat_id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    date TEXT NOT NULL,               -- YYYY-MM-DD 格式
    messages_sent INTEGER DEFAULT 0,
    tokens_consumed INTEGER DEFAULT 0,
    api_calls INTEGER DEFAULT 0,
    errors INTEGER DEFAULT 0,
    avg_response_time_ms INTEGER DEFAULT 0,
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    UNIQUE(user_id, date)
);

-- 研究事件表（用户行为分析）
CREATE TABLE IF NOT EXISTS research_events (
    event_id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    event_type TEXT NOT NULL,         -- 'message_sent', 'conversation_created', 'app_opened' 等
    event_data TEXT,                  -- JSON格式的事件数据
    timestamp INTEGER NOT NULL,
    session_id TEXT,                  -- 会话ID
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

-- 系统日志表（错误和性能监控）
CREATE TABLE IF NOT EXISTS system_logs (
    log_id TEXT PRIMARY KEY,
    level TEXT NOT NULL,              -- 'INFO', 'WARN', 'ERROR'
    message TEXT NOT NULL,
    user_id TEXT,
    timestamp INTEGER NOT NULL,
    metadata TEXT                     -- JSON格式的额外信息
);

-- 创建索引优化查询性能
CREATE INDEX IF NOT EXISTS idx_messages_user_timestamp ON messages(user_id, timestamp);
CREATE INDEX IF NOT EXISTS idx_messages_conversation ON messages(conversation_id);
CREATE INDEX IF NOT EXISTS idx_conversations_user ON conversations(user_id);
CREATE INDEX IF NOT EXISTS idx_usage_stats_user_date ON usage_stats(user_id, date);
CREATE INDEX IF NOT EXISTS idx_research_events_user_type ON research_events(user_id, event_type);
CREATE INDEX IF NOT EXISTS idx_system_logs_timestamp ON system_logs(timestamp);

-- 创建清理过期数据的视图
CREATE VIEW IF NOT EXISTS expired_data AS
SELECT 
    'messages' as table_name,
    message_id as record_id,
    timestamp
FROM messages 
WHERE timestamp < (strftime('%s', 'now') - 7776000) -- 90天前
UNION ALL
SELECT 
    'research_events' as table_name,
    event_id as record_id,
    timestamp
FROM research_events 
WHERE timestamp < (strftime('%s', 'now') - 2592000) -- 30天前
UNION ALL
SELECT 
    'system_logs' as table_name,
    log_id as record_id,
    timestamp
FROM system_logs 
WHERE timestamp < (strftime('%s', 'now') - 604800); -- 7天前
