package com.pando.app.core.network.socket

object SocketConstants {
    const val BASE_URL = "wss://lockly-api.duckdns.org/ws"

    object Chat {
        const val SEND_TEXT_DESTINATION = "/app/chat.sendText"
        const val TOPIC_CONVERSATION = "/topic/conversation"
        const val USER_QUEUE_CONVERSATIONS = "/user/queue/conversation"
    }
}