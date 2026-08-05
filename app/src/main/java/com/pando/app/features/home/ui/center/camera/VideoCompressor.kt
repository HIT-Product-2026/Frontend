package com.pando.app.features.home.ui.center.camera

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Singleton
@OptIn(markerClass = [UnstableApi::class])
class VideoCompressor @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    suspend fun compress(sourceFile: File): File? {
        val outputFile = File(
            context.cacheDir,
            "compressed_${UUID.randomUUID()}.mp4"
        )

        val compressedFile = withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine<File?> { continuation ->
                lateinit var transformer: Transformer

                val listener = object : Transformer.Listener {
                    override fun onCompleted(
                        composition: androidx.media3.transformer.Composition,
                        exportResult: ExportResult
                    ) {
                        if (outputFile.exists() && outputFile.length() > 0L) {
                            continuation.resume(outputFile)
                        } else {
                            continuation.resume(null)
                        }
                    }

                    override fun onError(
                        composition: androidx.media3.transformer.Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        outputFile.delete()
                        continuation.resume(null)
                    }
                }

                try {
                    val encoderFactory = DefaultEncoderFactory.Builder(context)
                        .setRequestedVideoEncoderSettings(
                            VideoEncoderSettings.Builder()
                                .setBitrate(VIDEO_BITRATE)
                                .build()
                        )
                        .setRequestedAudioEncoderSettings(
                            AudioEncoderSettings.Builder()
                                .setBitrate(AUDIO_BITRATE)
                                .build()
                        )
                        .build()

                    transformer = Transformer.Builder(context)
                        .setVideoMimeType(MimeTypes.VIDEO_H264)
                        .setAudioMimeType(MimeTypes.AUDIO_AAC)
                        .setEncoderFactory(encoderFactory)
                        .addListener(listener)
                        .build()

                    continuation.invokeOnCancellation {
                        transformer.cancel()
                        outputFile.delete()
                    }

                    val editedMediaItem = EditedMediaItem.Builder(
                        MediaItem.fromUri(sourceFile.toURI().toString())
                    )
                        .setEffects(
                            Effects(
                                emptyList(),
                                listOf(Presentation.createForHeight(TARGET_HEIGHT))
                            )
                        )
                        .build()

                    transformer.start(editedMediaItem, outputFile.absolutePath)
                } catch (_: Exception) {
                    outputFile.delete()
                    continuation.resume(null)
                }
            }
        }

        if (compressedFile == null || compressedFile.length() >= sourceFile.length()) {
            compressedFile?.delete()
            return null
        }

        return compressedFile
    }

    private companion object {
        private const val TARGET_HEIGHT = 720
        private const val VIDEO_BITRATE = 2_000_000
        private const val AUDIO_BITRATE = 128_000
    }
}
