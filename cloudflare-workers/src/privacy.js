/**
 * 隐私保护和数据脱敏模块
 * 确保用户数据的隐私和安全
 */

/**
 * 数据脱敏工具
 */
export const sanitizeData = {
  /**
   * 脱敏用户ID（保持格式但移除可识别信息）
   */
  userId(userId) {
    if (!userId || typeof userId !== 'string') {
      return 'anonymous_' + crypto.randomUUID().substring(0, 8);
    }
    
    // 如果是UUID格式，保持UUID格式但重新生成
    const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
    if (uuidRegex.test(userId)) {
      return userId; // UUID本身就是伪匿名的
    }
    
    // 其他格式转换为哈希
    return 'user_' + this.hashString(userId).substring(0, 16);
  },

  /**
   * 脱敏设备信息
   */
  deviceInfo(deviceInfo) {
    if (!deviceInfo || typeof deviceInfo !== 'string') {
      return null;
    }

    try {
      const info = JSON.parse(deviceInfo);
      
      // 移除敏感信息，保留有用的统计信息
      return JSON.stringify({
        platform: info.platform || 'unknown',
        osVersion: this.generalizeVersion(info.osVersion),
        appVersion: info.appVersion,
        deviceModel: this.generalizeDeviceModel(info.deviceModel),
        screenSize: this.generalizeScreenSize(info.screenSize),
        // 移除：IMEI, MAC地址, 序列号等
      });
    } catch (e) {
      // 如果不是JSON，进行基础脱敏
      return deviceInfo
        .replace(/\b\d{15}\b/g, '[IMEI]')  // 移除IMEI
        .replace(/\b([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})\b/g, '[MAC]') // 移除MAC地址
        .substring(0, 200); // 限制长度
    }
  },

  /**
   * 版本号泛化（保留主要版本信息）
   */
  generalizeVersion(version) {
    if (!version) return 'unknown';
    
    // 提取主要版本号 (例: "14.5.1" -> "14.x")
    const match = version.match(/^(\d+)/);
    return match ? `${match[1]}.x` : 'unknown';
  },

  /**
   * 设备型号泛化
   */
  generalizeDeviceModel(model) {
    if (!model) return 'unknown';
    
    // 泛化设备型号，保留品牌和系列信息
    const generalizations = {
      'iPhone': /iPhone\d+/,
      'iPad': /iPad/,
      'Samsung': /SM-[A-Z]\d+/,
      'Pixel': /Pixel \d+/,
      'OnePlus': /OnePlus \d+/
    };

    for (const [general, pattern] of Object.entries(generalizations)) {
      if (pattern.test(model)) {
        return general;
      }
    }

    return 'other';
  },

  /**
   * 屏幕尺寸泛化
   */
  generalizeScreenSize(screenSize) {
    if (!screenSize) return 'unknown';
    
    try {
      const { width, height } = JSON.parse(screenSize);
      const maxDimension = Math.max(width, height);
      
      if (maxDimension >= 2000) return 'large';
      if (maxDimension >= 1500) return 'medium';
      return 'small';
    } catch (e) {
      return 'unknown';
    }
  },

  /**
   * 字符串哈希化
   */
  hashString(str) {
    let hash = 0;
    for (let i = 0; i < str.length; i++) {
      const char = str.charCodeAt(i);
      hash = ((hash << 5) - hash) + char;
      hash = hash & hash; // 转换为32位整数
    }
    return Math.abs(hash).toString(16);
  }
};

/**
 * 内容哈希化（用于消息内容的隐私保护）
 */
export function hashContent(content) {
  if (!content || typeof content !== 'string') {
    return null;
  }

  // 使用简单的哈希算法（生产环境建议使用更强的哈希）
  const hash = sanitizeData.hashString(content);
  
  // 添加内容长度和基本特征（用于分析但不泄露内容）
  const features = {
    length: content.length,
    wordCount: content.split(/\s+/).length,
    hasNumbers: /\d/.test(content),
    hasSpecialChars: /[!@#$%^&*(),.?":{}|<>]/.test(content),
    language: detectLanguage(content)
  };

  return JSON.stringify({
    hash: hash,
    features: features
  });
}

/**
 * 简单的语言检测
 */
function detectLanguage(text) {
  // 简单的中英文检测
  const chineseChars = (text.match(/[\u4e00-\u9fff]/g) || []).length;
  const totalChars = text.length;
  
  if (chineseChars / totalChars > 0.3) {
    return 'zh';
  } else if (/^[a-zA-Z\s\d\p{P}]+$/u.test(text)) {
    return 'en';
  }
  
  return 'mixed';
}

/**
 * IP地址脱敏
 */
export function sanitizeIP(ip) {
  if (!ip) return 'unknown';
  
  // IPv4 脱敏：保留前两段
  const ipv4Match = ip.match(/^(\d+\.\d+)\.\d+\.\d+$/);
  if (ipv4Match) {
    return `${ipv4Match[1]}.x.x`;
  }
  
  // IPv6 脱敏：保留前缀
  if (ip.includes(':')) {
    const parts = ip.split(':');
    return `${parts[0]}:${parts[1]}::x`;
  }
  
  return 'unknown';
}

/**
 * 敏感信息检测和移除
 */
export function removeSensitiveInfo(text) {
  if (!text || typeof text !== 'string') {
    return text;
  }

  return text
    // 移除邮箱地址
    .replace(/\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b/g, '[EMAIL]')
    // 移除电话号码
    .replace(/\b\d{3}-?\d{3}-?\d{4}\b/g, '[PHONE]')
    // 移除身份证号
    .replace(/\b\d{17}[\dXx]\b/g, '[ID_CARD]')
    // 移除信用卡号
    .replace(/\b\d{4}[\s-]?\d{4}[\s-]?\d{4}[\s-]?\d{4}\b/g, '[CARD]')
    // 移除URL
    .replace(/https?:\/\/[^\s]+/g, '[URL]');
}

/**
 * 数据最小化原则：只保留必要的字段
 */
export function minimizeData(data, allowedFields) {
  if (!data || typeof data !== 'object') {
    return data;
  }

  const minimized = {};
  for (const field of allowedFields) {
    if (data.hasOwnProperty(field)) {
      minimized[field] = data[field];
    }
  }

  return minimized;
}
