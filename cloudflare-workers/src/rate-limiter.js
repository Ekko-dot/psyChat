/**
 * 速率限制模块
 * 基于 Cloudflare KV 实现简单的速率限制
 */

export const rateLimiter = {
  /**
   * 检查速率限制
   * @param {string} clientIP - 客户端IP
   * @param {Object} env - 环境变量
   * @returns {Object} - {allowed: boolean, retryAfter: number}
   */
  async check(clientIP, env) {
    if (!env.RATE_LIMIT_KV) {
      // 如果没有配置KV，默认允许
      return { allowed: true, retryAfter: 0 };
    }

    const key = `rate_limit:${clientIP}`;
    const windowSize = 60; // 1分钟窗口
    const maxRequests = 100; // 每分钟最多100个请求
    
    try {
      // 获取当前计数
      const currentData = await env.RATE_LIMIT_KV.get(key, 'json');
      const now = Math.floor(Date.now() / 1000);
      
      if (!currentData) {
        // 首次请求
        await env.RATE_LIMIT_KV.put(key, JSON.stringify({
          count: 1,
          windowStart: now
        }), { expirationTtl: windowSize });
        
        return { allowed: true, retryAfter: 0 };
      }
      
      const { count, windowStart } = currentData;
      
      // 检查是否在同一时间窗口内
      if (now - windowStart < windowSize) {
        if (count >= maxRequests) {
          // 超过限制
          const retryAfter = windowSize - (now - windowStart);
          return { allowed: false, retryAfter };
        } else {
          // 增加计数
          await env.RATE_LIMIT_KV.put(key, JSON.stringify({
            count: count + 1,
            windowStart: windowStart
          }), { expirationTtl: windowSize - (now - windowStart) });
          
          return { allowed: true, retryAfter: 0 };
        }
      } else {
        // 新的时间窗口
        await env.RATE_LIMIT_KV.put(key, JSON.stringify({
          count: 1,
          windowStart: now
        }), { expirationTtl: windowSize });
        
        return { allowed: true, retryAfter: 0 };
      }
      
    } catch (error) {
      console.error('Rate limiter error:', error);
      // 出错时默认允许
      return { allowed: true, retryAfter: 0 };
    }
  }
};
