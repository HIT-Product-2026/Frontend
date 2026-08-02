package com.pando.app.core.network.sse

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SseModule {

    @Provides
    @Singleton
    @SseOkHttpClient
    fun provideSseOkHttpClient(@Named("AuthenticatedClient") okHttpClient: OkHttpClient): OkHttpClient {
        return okHttpClient
            .newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }
}