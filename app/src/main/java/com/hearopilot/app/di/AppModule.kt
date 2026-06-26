package com.hearopilot.app.di

import android.content.Context
import com.arm.aichat.InferenceEngine
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.Vad
import com.hearopilot.app.ui.SimulateStreamingAsr
import com.hearopilot.app.data.datasource.ModelDownloadManager
import com.hearopilot.app.domain.repository.SettingsRepository
import com.hearopilot.app.service.RecordingNotificationManager
import com.hearopilot.app.service.RecordingSessionManager
import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Singleton

/**
 * Hilt module providing application-level dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    fun provideOfflineRecognizer(
        @ApplicationContext context: Context,
        modelDownloadManager: ModelDownloadManager // 保留参数防止编译报错，但不再使用
    ): OfflineRecognizer {
        // 1. 获取应用的外部文件目录（Android/data/com.hearopilot.app/files/）
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val sttModelPath = java.io.File(baseDir, "stt_model").absolutePath

        // 2. 构建 Paraformer 配置，指向外部存储的路径
        val paraformerConfig = com.k2fsa.sherpa.onnx.ParaformerModelConfig().apply {
            encoder = "$sttModelPath/encoder.int8.onnx"
            decoder = "$sttModelPath/decoder.int8.onnx"
            // Paraformer 不需要 joiner
        }

        val modelConfig = com.k2fsa.sherpa.onnx.OfflineModelConfig().apply {
            this.paraformer = paraformerConfig
            tokens = "$sttModelPath/tokens.txt"
            numThreads = 2
        }

        // 3. assetManager = null 表示从文件系统加载（而不是 apk 内部）
        return OfflineRecognizer(
            assetManager = null,
            config = OfflineRecognizerConfig(modelConfig = modelConfig)
        )
    }

    @Provides
    fun provideVad(
        @ApplicationContext context: Context,
        settingsRepository: SettingsRepository
    ): Vad {
        val settings = runBlocking { settingsRepository.getSettings().first() }

        SimulateStreamingAsr.initVad(
            assetManager = context.assets,
            minSilenceDuration = settings.vadMinSilenceDuration,
            maxSpeechDuration = settings.vadMaxSpeechDuration,
            threshold = settings.vadThreshold
        )
        return SimulateStreamingAsr.vad
    }

    @Provides
    @Singleton
    fun provideInferenceEngine(
        @ApplicationContext context: Context
    ): InferenceEngine {
        return com.arm.aichat.AiChat.getInferenceEngine(context)
    }

    @Provides
    @Singleton
    fun provideRecordingSessionManager(): RecordingSessionManager {
        return RecordingSessionManager()
    }

    @Provides
    @Singleton
    fun provideRecordingNotificationManager(
        @ApplicationContext context: Context
    ): RecordingNotificationManager {
        return RecordingNotificationManager(context)
    }
}
