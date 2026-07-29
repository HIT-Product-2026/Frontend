package com.pando.app.core.network.api

object ApiConstants {
    const val BASE_URL = "https://lockly-api.duckdns.org"
    const val API_V1 = "/api/v1/"
    const val API_V2 = "/api/v2/"
    const val API_NOW = API_V2
    object Auth {
        const val LOGIN = "auth/login"
        const val REGISTER_SEND_OTP = "auth/register/send-otp"
        const val REGISTER_VERIFY_OTP = "auth/register/verify-otp"
        const val FORGOT_PASSWORD_SEND_OTP = "auth/forgot-password/send-otp"
        const val FORGOT_PASSWORD_VERIFY_OTP = "auth/forgot-password/verify-otp"
        const val RESET_PASSWORD = "auth/forgot-password/reset"
        const val REFRESH_TOKEN = "auth/refresh"
    }

    object Post {
        const val CREATE_POST = "post"
        const val GET_POST = "post"
        const val GET_POST_IMAGE = "post/{post_id}/image"
    }
    object User {
        const val SEND_FCM_TOKEN = "user/fcm-token"
        const val GET_USER_AVATAR = "user/{user_id}/avatar"
        const val UPDATE_DISPLAY_NAME = "user/display-name"
        const val UPDATE_AVATAR = "user/avatar"
    }

    object FriendShip {
        const val REQUEST_FRIEND = "friendship/request"
        const val GET_FRIEND_LIST = "friendship/friends"
        const val SEARCH_USER = "friendship/friendships/search"
        const val ACCEPT_FRIEND = "friendship/accept/{friendships_id}"
        const val REJECT_FRIEND = "friendship/reject/{friendships_id}"
        const val GET_SENT_REQUESTED_USERS = "friendship/friendships/requester"
        const val GET_RECEIVED_REQUESTED_USERS = "friendship/friendships/received"
        const val UNFRIEND = "friendship/unfriend/{friendId}"
    }

    object Conversation {
        const val GET_CONVERSATIONS = "conversations/conversations"
        const val GET_CONVERSATION_MESSAGES = "conversations/messages"
    }

    object Message {
        const val SEND_TEXT_MESSAGE = "message/text"
        const val SEND_IMAGE_MESSAGE = "message/image"
    }

    object Profile {
        const val UPDATE_PROFILE = "profile"
    }
}
