package com.psychat.app.data.remote.api

import com.psychat.app.data.remote.dto.AnthropicRequest
import com.psychat.app.data.remote.dto.AnthropicResponse
import com.psychat.app.data.remote.dto.BatchSyncRequest
import com.psychat.app.data.remote.dto.BatchSyncResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Anthropic API服务接口
 * 统一处理与代理服务的通信
 */
interface AnthropicApiService {
    
    /**
     * 发送消息到Anthropic API
     */
    @POST("chat")
    suspend fun sendMessage(
        @Body request: AnthropicRequest,
        @Header("Content-Type") contentType: String = "application/json"
    ): Response<AnthropicResponse>
    
    /**
     * 批量同步数据到Cloudflare Workers
     */
    @POST("log-batch")
    suspend fun logBatch(
        @Body request: BatchSyncRequest,
        @Header("Content-Type") contentType: String = "application/json"
    ): Response<BatchSyncResponse>
}
