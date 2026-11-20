package br.uff.hist.chat

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ChatModule {
    @Binds abstract fun bindChatSocketFactory(impl: OkHttpChatSocketFactory): ChatSocketFactory

    @Binds abstract fun bindAppForegroundSignal(impl: ProcessLifecycleForegroundSignal): AppForegroundSignal
}
