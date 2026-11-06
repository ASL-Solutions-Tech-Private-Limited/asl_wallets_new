package com.asl.wallet.feature.profile.data

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ProfileModule {
    @Binds @Singleton abstract fun repository(impl: ProfileRepositoryImpl): ProfileRepository
    /** Swap for the real implementation when the complaints/feedback API exists. */
    @Binds @Singleton abstract fun support(impl: PendingSupportRepository): SupportRepository

    companion object {
        @Provides @Singleton
        fun profileApi(retrofit: Retrofit): ProfileApi = retrofit.create(ProfileApi::class.java)
    }
}
