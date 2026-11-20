package br.uff.hist.core.session

import br.uff.hist.core.network.TokenProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
abstract class SessionModule {
    @Binds abstract fun bindSessionStore(impl: DataStoreSessionStore): SessionStore

    @Binds abstract fun bindTokenProvider(impl: DataStoreSessionStore): TokenProvider

    companion object {
        @Provides @Singleton @ApplicationScope
        fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
