package com.psychat.app.data.repository

import com.psychat.app.domain.model.Conversation
import com.psychat.app.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    
    /**
     * 实时观察指定对话的所有消息
     * 用于UI响应式更新，当消息发生变化时自动刷新界面
     * 
     * @param conversationId 对话ID
     * @return Flow<List<Message>> 消息列表的响应式流
     * 
     * 调用位置: ChatViewModel.observeMessages() - 创建/加载对话时调用
     */
    fun observeMessages(conversationId: String): Flow<List<Message>>
    
    /**
     * 实时观察所有活跃对话列表
     * 用于对话列表界面的响应式更新
     * 
     * @return Flow<List<Conversation>> 对话列表的响应式流
     * 
     * 状态: 未使用 - 当前项目为单对话模式，暂不需要对话列表功能
     */
    fun observeConversations(): Flow<List<Conversation>>
    
    /**
     * 创建新对话
     * 生成唯一对话ID并保存到数据库
     * 
     * @param title 对话标题
     * @return String 新创建的对话ID
     * 
     * 调用位置: ChatViewModel.createNewConversation() - 应用启动时自动创建
     */
    suspend fun createConversation(title: String): String
    
    /**
     * 发送消息的完整流程（原有方法）
     * 包含: 保存用户消息 → 调用API → 保存AI回复 → 更新对话状态
     * 
     * @param conversationId 对话ID
     * @param content 消息内容
     * @param isVoiceInput 是否为语音输入
     * @return Result<Message> 成功返回AI回复消息，失败返回异常
     * 
     * 调用位置: ChatViewModel.sendMessage() - 原有的复杂发送逻辑
     * 注意: 现在有了简化的send(text)方法，建议使用新方法
     */
    suspend fun sendMessage(
        conversationId: String,
        content: String,
        isVoiceInput: Boolean = false
    ): Result<Message>
    
    /**
     * 获取指定对话的详细信息
     * 用于对话详情查看或编辑功能
     * 
     * @param conversationId 对话ID
     * @return Conversation? 对话信息，不存在时返回null
     * 
     * 状态: 基本未使用 - 只在deleteConversation内部调用
     */
    suspend fun getConversation(conversationId: String): Conversation?
    
    /**
     * 删除对话及其所有相关消息
     * 会同时删除对话记录和该对话下的所有消息
     * 
     * @param conversationId 要删除的对话ID
     * 
     * 状态: 未使用 - 当前UI未提供删除功能
     */
    suspend fun deleteConversation(conversationId: String)
    
    // Step 3: 新增的简化方法
    /**
     * 发送消息到AI模型并获取回复（简化版本）
     * 只负责API调用，不处理本地数据库操作
     * 
     * 流程: 获取对话历史 → 构建请求 → 调用代理服务 → 返回AI回复
     * 
     * @param text 用户输入的文本
     * @param conversationId 对话ID
     * @return String AI的回复文本
     * @throws Exception 当API请求失败时抛出异常，交由上层处理
     * 
     * 调用位置: ChatViewModel.send() - Step 4的简化发送流程
     * 优势: 职责单一，便于测试和错误处理
     */
    suspend fun sendToModel(text: String, conversationId: String): String
    
    /**
     * 添加用户消息到本地数据库
     * 只负责数据库操作，不涉及网络请求
     * 
     * @param text 消息内容
     * @param conversationId 对话ID
     * 
     * 调用位置: ChatViewModel.send() - 在发送API请求前先保存用户消息
     * 特点: 立即保存，用户消息会立即显示在界面上
     */
    suspend fun addLocalUserMessage(text: String, conversationId: String)
    
    /**
     * 添加AI消息到本地数据库
     * 只负责数据库操作，通常在收到API响应后调用
     * 
     * @param text AI回复内容
     * @param conversationId 对话ID
     * 
     * 调用位置: ChatViewModel.send() - 收到AI回复后保存到数据库
     * 特点: 标记为已同步，表示来自服务端的数据
     */
    suspend fun addLocalAiMessage(text: String, conversationId: String)
}
