# PsyChat - AI 聊天助手

一个专为老年用户设计的AI聊天应用，支持语音输入和与Anthropic Claude的对话。

## 项目特点

- **语音优先**: 支持语音输入，方便不便打字的老年用户
- **本地存储**: 使用Room数据库本地保存所有对话记录
- **云端同步**: 集成Anthropic API进行AI对话
- **现代架构**: 采用MVVM + UDF架构，使用Jetpack Compose UI

## 技术栈

- **UI**: Jetpack Compose + Material 3
- **架构**: MVVM + 单向数据流 (UDF)
- **依赖注入**: Hilt
- **数据库**: Room
- **网络**: Retrofit + OkHttp
- **并发**: Kotlin 协程
- **后台任务**: WorkManager

## 项目结构

```text
app/
├── data/           # 数据层
│   ├── local/      # Room数据库
│   ├── remote/     # API服务
│   └── repository/ # 数据仓库
├── domain/         # 领域模型
│   └── model/      # 数据模型
├── ui/             # UI层
│   ├── chat/       # 聊天界面
│   └── theme/      # 主题配置
└── common/         # 通用组件
    └── di/         # 依赖注入模块
```

## 当前状态

✅ **Step 0**: 项目环境搭建完成
✅ **Step 1**: MVVM架构和Hilt依赖注入设置完成
✅ Room数据库配置完成
✅ 基础UI界面实现

## 下一步

- 集成语音识别功能
- 完善错误处理和重试机制
- 添加数据同步功能
- 实现隐私合规功能

## 运行项目

1. 在Android Studio中打开项目
2. 确保已安装Android SDK API 34
3. 配置Anthropic API密钥
4. 运行应用

## API配置

在使用前需要配置Anthropic API密钥：

- 在`ChatRepositoryImpl.kt`中替换`YOUR_API_KEY`
- 建议使用安全的密钥存储方案

## work manager
```text
用户发送消息
    ↓
检查网络状态
    ├─有网络→ 直接发送API
    └─无网络→ 加入SyncTask队列
         ↓
    WorkManager监听网络
         ↓
    网络恢复→批量同步→清理任务
```

![img.png](img.png)