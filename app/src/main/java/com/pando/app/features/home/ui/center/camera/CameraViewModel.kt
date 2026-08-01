package com.pando.app.features.home.ui.center.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.pando.app.core.base.BaseVM
import com.pando.app.core.network.api.ApiResponse
import com.pando.app.core.utils.DataResult
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream

sealed interface CameraViewMode {
    object Capture : CameraViewMode                // Chế độ live preview để chụp
    data class Send(val photoFile: File) :
        CameraViewMode  // Chế độ hiển thị ảnh vừa chụp kèm nút Gửi
}

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    @param:ApplicationContext private val context: Context
) : BaseVM<ApiResponse<PostResponse>>() {
    private val _cameraViewMode = MutableStateFlow<CameraViewMode>(CameraViewMode.Capture)
    val cameraViewMode: StateFlow<CameraViewMode> = _cameraViewMode.asStateFlow()

    fun setSendMode(photoFile: File) {
        _cameraViewMode.value = CameraViewMode.Send(photoFile)
    }

    fun setCaptureMode() {
        _cameraViewMode.value = CameraViewMode.Capture
    }

    fun sendPost(caption: String?, longitude: Double?, latitude: Double?) {
        val currentMode = _cameraViewMode.value
        if (currentMode !is CameraViewMode.Send) return

        val originFile = currentMode.photoFile

        getData {
            val normalizedFile = normalizeExif(originFile)
//            val normalizedFile = originFile

            val uploadFile = try {
                Compressor.compress(context, normalizedFile) {
                    resolution(1280, 720)
                    quality(80)
                    format(Bitmap.CompressFormat.JPEG)
                    size(2_097_152)
                }

            } catch (e: Exception) {
                normalizedFile
            }

            val result = mediaRepository.sendPost(caption, longitude, latitude, uploadFile)

            if (originFile.exists() && originFile.absolutePath != uploadFile.absolutePath) {
                originFile.delete()
            }
            if (uploadFile.exists()) {
                uploadFile.delete()
            }

            if (
                normalizedFile.absolutePath != originFile.absolutePath &&
                normalizedFile.absolutePath != uploadFile.absolutePath
            ) {
                normalizedFile.delete()
            }

            if (result is DataResult.Success) {
                setCaptureMode()
            }

            result
        }
    }

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