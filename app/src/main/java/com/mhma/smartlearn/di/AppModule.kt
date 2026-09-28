package com.mhma.smartlearn.di

import android.content.Context
import androidx.room.Room
import com.mhma.smartlearn.data.local.AppDatabase
import com.mhma.smartlearn.data.local.dao.CourseDao
import com.mhma.smartlearn.data.local.dao.DeadlineDao
import com.mhma.smartlearn.data.local.dao.ResourceDao
import com.mhma.smartlearn.data.local.dao.SectionDao
import com.mhma.smartlearn.data.network.MoodleUrls
import com.mhma.smartlearn.data.network.SessionCookieInterceptor
import com.mhma.smartlearn.data.network.SessionManager
import com.mhma.smartlearn.data.network.scraper.MoodleParser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(session: SessionManager): OkHttpClient =
        OkHttpClient.Builder()
            // Redirects are followed by MoodleClient so it can catch bounces to the login page.
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(SessionCookieInterceptor(session))
            .build()

    @Provides
    @Singleton
    fun provideParser(): MoodleParser = MoodleParser(MoodleUrls.BASE)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            // Everything in here is a cache of the portal; losing it just means one more refresh.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideCourseDao(db: AppDatabase): CourseDao = db.courseDao()

    @Provides
    fun provideSectionDao(db: AppDatabase): SectionDao = db.sectionDao()

    @Provides
    fun provideResourceDao(db: AppDatabase): ResourceDao = db.resourceDao()

    @Provides
    fun provideDeadlineDao(db: AppDatabase): DeadlineDao = db.deadlineDao()
}
