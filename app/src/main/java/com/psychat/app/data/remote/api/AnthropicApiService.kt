package com.psychat.app.data.remote.api

import com.psychat.app.data.remote.dto.AnthropicRequest
import com.psychat.app.data.remote.dto.AnthropicResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface AnthropicApiService {
    
    @POST("v1/messages")
    suspend fun sendMessage(
        @Header("x-api-key") apiKey: String,
        @Header("anthropic-version") version: String = "2023-06-01",
        @Header("content-type") contentType: String = "application/json",
        @Body request: AnthropicRequest
    ): Response<AnthropicResponse>
}
