package com.pando.app.core.network.api

import com.pando.app.core.data.api.AuthApi
import com.pando.app.core.data.api.ConversationApi
import com.pando.app.core.data.api.FriendshipApi
import com.pando.app.core.data.api.PostApi
import com.pando.app.core.data.api.ProfileApi
import com.pando.app.core.data.api.UserApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ProvideApiModule {

    @Provides
    @Singleton
    fun provideAuthApi(@Named("AuthRetrofit") retrofit: Retrofit): AuthApi {
        return retrofit.create(AuthApi::class.java)
    }

    @Provides
    @Singleton
    fun provideUserApi(@Named("MainRetrofit") retrofit: Retrofit): UserApi {
        return retrofit.create(UserApi::class.java)
    }

    @Provides
    @Singleton
    fun providePostApi(@Named("MainRetrofit") retrofit: Retrofit): PostApi {
        return retrofit.create(PostApi::class.java)
    }

    @Provides
    @Singleton
    fun provideFriendshipApi(@Named("MainRetrofit") retrofit: Retrofit): FriendshipApi {
        return retrofit.create(FriendshipApi::class.java)
    }

    @Provides
    @Singleton
    fun provideConversationApi(@Named("MainRetrofit") retrofit: Retrofit): ConversationApi {
        return retrofit.create(ConversationApi::class.java)
    }

    @Provides
    @Singleton
    fun provideProfileApi(@Named("MainRetrofit") retrofit: Retrofit): ProfileApi {
        return retrofit.create(ProfileApi::class.java)
    }
}