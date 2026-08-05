package com.pando.app.features.home.ui.center.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.pando.app.core.base.BaseVM
import com.pando.app.core.network.api.ApiResponse
import com.pando.app.core.utils.DataResult
import com.pando.app.features.home.data.model.entity.enumEntity.TypePost
import com.pando.app.features.home.data.model.response.PostResponse
import com.pando.app.features.home.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import id.zelory.compressor.Compressor
import id.zelory.compressor.constraint.format
import id.zelory.compressor.constraint.quality
import id.zelory.compressor.constraint.resolution
import id.zelory.compressor.constraint.size
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

sealed interface CameraViewMode {
    data object Capture : CameraViewMode

    data class Send(val mediaFile: File, val type: TypePost) : CameraViewMode
}

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val videoCompressor: VideoCompressor,
    @param:ApplicationContext private val context: Context
) : BaseVM<ApiResponse<PostResponse>>() {
    private val _cameraViewMode = MutableStateFlow<CameraViewMode>(CameraViewMode.Capture)
    val cameraViewMode: StateFlow<CameraViewMode> = _cameraViewMode.asStateFlow()

    fun setSendMode(mediaFile: File, type: TypePost) {
        _cameraViewMode.value = CameraViewMode.Send(mediaFile, type)
    }

    fun setCaptureMode() {
        _cameraViewMode.value = CameraViewMode.Capture
    }

    fun sendPost(caption: String?, longitude: Double?, latitude: Double?) {
        val currentMode = _cameraViewMode.value
        if (currentMode !is CameraViewMode.Send) return

        val originFile = currentMode.mediaFile

        getData {
            val preparedMedia = withContext(Dispatchers.IO) {
                val normalizedFile = when (currentMode.type) {
                    TypePost.IMAGE -> normalizeExif(originFile)
                    TypePost.VIDEO -> originFile
                }

                val uploadFile = when (currentMode.type) {
                    TypePost.IMAGE -> {
                        try {
                            Compressor.compress(context, normalizedFile) {
                                resolution(1280, 720)
                                quality(80)
                                format(Bitmap.CompressFormat.JPEG)
                                size(2_097_152)
                            }
                        } catch (_: Exception) {
                            normalizedFile
                        }
                    }

                    TypePost.VIDEO -> {
                        videoCompressor.compress(originFile) ?: originFile
                    }
                }

                PreparedMedia(
                    normalizedFile = normalizedFile,
                    uploadFile = uploadFile
                )
            }

            val result = mediaRepository.sendPost(
                caption,
                longitude,
                latitude,
                preparedMedia.uploadFile,
                currentMode.type
            )

            if (result is DataResult.Success) {
                withContext(Dispatchers.IO) {
                    if (
                        originFile.exists() &&
                        originFile.absolutePath != preparedMedia.uploadFile.absolutePath
                    ) {
                        originFile.delete()
                    }
                    if (preparedMedia.uploadFile.exists()) {
                        preparedMedia.uploadFile.delete()
                    }

                    if (
                        preparedMedia.normalizedFile.absolutePath != originFile.absolutePath &&
                        preparedMedia.normalizedFile.absolutePath !=
                        preparedMedia.uploadFile.absolutePath
                    ) {
                        preparedMedia.normalizedFile.delete()
                    }
                }

                setCaptureMode()
            }

            result
        }
    }

    private data class PreparedMedia(
        val normalizedFile: File,
        val uploadFile: File
    )

    private fun normalizeExif(sourceFile: File): File {
        val exif = ExifInterface(sourceFile.absolutePath)

        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )

        if (
            orientation == ExifInterface.ORIENTATION_NORMAL ||
            orientation == ExifInterface.ORIENTATION_UNDEFINED
        ) {
            return sourceFile
        }

        // Decode có sampling để tránh OutOfMemory với ảnh camera dung lượng lớn
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(sourceFile.absolutePath, bounds)

        var sampleSize = 1
        while (
            bounds.outWidth / sampleSize > 2048 ||
            bounds.outHeight / sampleSize > 2048
        ) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }

        val sourceBitmap = BitmapFactory.decodeFile(
            sourceFile.absolutePath,
            options
        ) ?: return sourceFile

        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
                    setScale(-1f, 1f)
                }

                ExifInterface.ORIENTATION_ROTATE_180 -> {
                    setRotate(180f)
                }

                ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                    setRotate(180f)
                    postScale(-1f, 1f)
                }

                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    setRotate(90f)
                    postScale(-1f, 1f)
                }

                ExifInterface.ORIENTATION_ROTATE_90 -> {
                    setRotate(90f)
                }

                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    setRotate(-90f)
                    postScale(-1f, 1f)
                }

                ExifInterface.ORIENTATION_ROTATE_270 -> {
                    setRotate(-90f)
                }
            }
        }

        val normalizedBitmap = Bitmap.createBitmap(
            sourceBitmap,
            0,
            0,
            sourceBitmap.width,
            sourceBitmap.height,
            matrix,
            true
        )

        val normalizedFile = File(
            context.cacheDir,
            "normalized_${System.currentTimeMillis()}.jpg"
        )

        FileOutputStream(normalizedFile).use { output ->
            normalizedBitmap.compress(
                android.graphics.Bitmap.CompressFormat.JPEG,
                95,
                output
            )
        }

        if (normalizedBitmap !== sourceBitmap) {
            sourceBitmap.recycle()
        }
        normalizedBitmap.recycle()

        // Ảnh mới đã được xoay trực tiếp vào pixel
        ExifInterface(normalizedFile.absolutePath).apply {
            setAttribute(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL.toString()
            )
            saveAttributes()
        }

        return normalizedFile
    }
}
