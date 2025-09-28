package com.psychat.app.data.remote.api

import com.psychat.app.data.remote.dto.AnthropicRequest
import com.psychat.app.data.remote.dto.AnthropicResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface AnthropicApiService {
    
    @POST("chat")
    suspend fun sendMessage(
        @Body request: AnthropicRequest
    ): Response<AnthropicResponse>
}
