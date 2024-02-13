package com.enkod.enkodpushlibrary

import com.google.gson.JsonObject

data class Product(
    var id: String,
    var categoryId: String?,
    var count: Int?,
    var price: String?,
    var picture: String?
)

data class Order(
    var id: String?,
    var productId: String?,
    var count: Int?,
    var price: String?,
    var picture: String?,
    var sum: Double?
)

data class PageUrl(
    val url: String
)

data class SessionIdResponse(
    var isActive: Boolean = false,
    var scriptSettings: Any? = null,
    var session_id: String = ""
)

data class PersonResponse(
    var cart: JsonObject,
    var favourite: JsonObject,
    var session: String,
)

data class UpdateTokenResponse(
    var sessionId: String = "",
    var token: String = ""
)



data class GetTokenResponse(
    var token: String = "",
    var sessionId: String = "",
    var os: String = ""
)

data class PushClickBody(
    val sessionId: String,
    val personId: Int,
    val messageId: Int,
    val intent: Int,
    val url: String?
)

data class SubscribeBody(
    val sessionId: String,
    val token: String,
    val os: String? = null
)