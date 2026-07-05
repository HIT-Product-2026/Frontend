package com.pando.app.features.home.ui.camera

import android.content.Context
import android.graphics.Bitmap
import com.pando.app.core.base.BaseVM
import com.pando.app.core.network.ApiResponse
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

sealed interface CameraViewMode {
    object Capture : CameraViewMode                // Chế độ live preview để chụp
    data class Send(val photoFile: File) : CameraViewMode  // Chế độ hiển thị ảnh vừa chụp kèm nút Gửi
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

    fun sendPost(caption: String, longitude: Double?, latitude: Double?) {
        val currentMode = _cameraViewMode.value
        if (currentMode !is CameraViewMode.Send) return

        val originFile = currentMode.photoFile

        getData {
            val uploadFile = try {
                Compressor.compress(context, originFile) {
                    resolution(1280, 720)
                    quality(80)
                    format(Bitmap.CompressFormat.JPEG)
                    size(2_097_152)
                }
            } catch (e: Exception) {
                originFile
            }

            val result = mediaRepository.sendPost(caption, longitude, latitude, uploadFile)

            if (originFile.exists() && originFile.absolutePath != uploadFile.absolutePath) {
                originFile.delete()
            }
            if (uploadFile.exists()) {
                uploadFile.delete()
            }

            if (result is DataResult.Success) {
                setCaptureMode()
            }

            result
        }
    }

}