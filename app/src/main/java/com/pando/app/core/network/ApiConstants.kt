package com.pando.app.core.network

object ApiConstants {
    const val BASE_URL = "http://52.221.198.144:8080"
    const val API_V1 = "/api/v2/"

    object Auth {
        const val LOGIN = "auth/login"
        const val REGISTER_SEND_OTP = "auth/register/send-otp"
        const val REGISTER_VERIFY_OTP = "auth/register/verify-otp"
        const val FORGOT_PASSWORD_SEND_OTP = "auth/forgot-password/send-otp"
        const val FORGOT_PASSWORD_VERIFY_OTP = "auth/forgot-password/verify-otp"
        const val RESET_PASSWORD = "auth/forgot-password/reset"
    }

    object Post {
        const val CREATE_POST = "post"
    }
    object User {
        const val SEND_FCM_TOKEN = "user/fcm-token"
        const val GET_USER_AVATAR = "user/{user_id}/avatar"
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
    }
}
