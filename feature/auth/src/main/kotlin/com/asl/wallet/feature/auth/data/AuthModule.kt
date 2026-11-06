package com.asl.wallet.feature.auth.data

import com.asl.wallet.core.common.session.CurrentUser
import com.asl.wallet.feature.auth.session.SessionManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.Multibinds
import com.asl.wallet.core.common.session.SignOutCleanup
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {
    @Binds @Singleton abstract fun repository(impl: AuthRepositoryImpl): AuthRepository
    @Binds @Singleton abstract fun localStore(impl: DataStoreAuthLocalStore): AuthLocalStore
    @Binds @Singleton abstract fun currentUser(impl: SessionManager): CurrentUser

    /** Declared so the set exists even when no feature contributes a cleanup. */
    @Multibinds abstract fun signOutCleanups(): Set<SignOutCleanup>

    companion object {
        @Provides @Singleton
        fun authApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)
    }
}
