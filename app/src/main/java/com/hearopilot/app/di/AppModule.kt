package com.hearopilot.app.di

import android.content.Context
import com.arm.aichat.InferenceEngine
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
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
        modelDownloadManager: ModelDownloadManager
    ): OfflineRecognizer {
        // Get STT model path from downloaded files
        val sttModelPath = modelDownloadManager.getSttModelPath()
            ?: throw IllegalStateException("STT model not downloaded. Please download the model first.")

        // 1. 定义中文 Zipformer 双语模型的目录名（解压后的文件夹名）
        val modelDir = "$sttModelPath/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20"

        // 2. 手动构建 Transducer 配置，路径必须匹配 tar.bz2 内部目录结构
        val transducerConfig = OfflineTransducerModelConfig(
            encoder = "$modelDir/exp/encoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx",
            decoder = "$modelDir/exp/decoder-epoch-99-avg-1-chunk-16-left-128.onnx",
            joiner = "$modelDir/exp/joiner-epoch-99-avg-1-chunk-16-left-128.onnx"
        )

        // 3. 构建 Model 配置
        val modelConfig = OfflineModelConfig(
            transducer = transducerConfig,
            tokens = "$modelDir/data/lang_char/tokens.txt",
            // 关键：中文 Zipformer 离线模型不需要 modelType，设为空字符串
            modelType = "",
            // Fixed 2 threads for STT to avoid CPU contention with the LLM engine
            numThreads = 2
        )

        Log.i("AppModule", "STT: Using Zipformer Bilingual Zh-En model")
        Log.i("AppModule", "STT: ${modelConfig.numThreads} threads (fixed)")

        // 4. 创建识别器配置
        val config = OfflineRecognizerConfig(modelConfig = modelConfig)

        // 5. 创建识别器 (不使用 AssetManager，直接从文件系统加载)
        return OfflineRecognizer(
            assetManager = null,
            config = config
        )
    }

    @Provides
    fun provideVad(
        @ApplicationContext context: Context,
        settingsRepository: SettingsRepository
    ): Vad {
        // Read VAD parameters from settings
        val settings = runBlocking { settingsRepository.getSettings().first() }

        // Initialize using existing SimulateStreamingAsr logic with custom parameters
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
