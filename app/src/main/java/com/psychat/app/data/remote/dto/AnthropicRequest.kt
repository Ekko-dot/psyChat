package com.psychat.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class AnthropicRequest(
    @SerializedName("model")
    val model: String = "claude-3-sonnet-20240229",
    @SerializedName("max_tokens")
    val maxTokens: Int = 1024,
    @SerializedName("messages")
    val messages: List<AnthropicMessage>
)

data class AnthropicMessage(
    @SerializedName("role")
    val role: String, // "user" or "assistant"
    @SerializedName("content")
    val content: String
)

data class AnthropicResponse(
    @SerializedName("content")
    val content: List<AnthropicContent>,
    @SerializedName("id")
    val id: String,
    @SerializedName("model")
    val model: String,
    @SerializedName("role")
    val role: String,
    @SerializedName("stop_reason")
    val stopReason: String?,
    @SerializedName("stop_sequence")
    val stopSequence: String?,
    @SerializedName("type")
    val type: String,
    @SerializedName("usage")
    val usage: AnthropicUsage
)

data class AnthropicContent(
    @SerializedName("text")
    val text: String,
    @SerializedName("type")
    val type: String
)

data class AnthropicUsage(
    @SerializedName("input_tokens")
    val inputTokens: Int,
    @SerializedName("output_tokens")
    val outputTokens: Int
)
