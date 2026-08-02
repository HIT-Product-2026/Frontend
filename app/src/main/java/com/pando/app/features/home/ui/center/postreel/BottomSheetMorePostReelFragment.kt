package com.pando.app.features.home.ui.center.postreel

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import com.pando.app.core.base.BaseBottomSheet
import com.pando.app.databinding.FragmentBottomSheetMorePostReelBinding
import java.util.UUID

class BottomSheetMorePostReelFragment : BaseBottomSheet<FragmentBottomSheetMorePostReelBinding>(FragmentBottomSheetMorePostReelBinding::inflate) {
    companion object {
        private const val ARG_POST_ID = "post_id"
        private const val ARG_IMAGE_URL = "image_url"

        fun newInstance(postId: UUID, imageUrl: String) =
            BottomSheetMorePostReelFragment().apply {
                arguments = bundleOf(
                    ARG_POST_ID to postId.toString(),
                    ARG_IMAGE_URL to imageUrl
                )
            }
    }

    private val postId: String
        get() = requireArguments().getString(ARG_POST_ID) ?: error("Thiếu postId")

    private val imageUrl: String
        get() = requireArguments().getString(ARG_IMAGE_URL) ?: error("Thiếu imageUrl")

    override fun initView() {
    }

    override fun initActionView() {
        binding.saveImage.setOnClickListener {
            val downloadManager = requireContext()
                .getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

            val request = DownloadManager.Request(imageUrl.toUri())
                .setTitle("Ảnh Reel")
                .setDescription("Đang tải ảnh...")
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                .setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_PICTURES,
                    "Pando/reel_$postId.jpg"
                )

            downloadManager.enqueue(request)

            dismiss()
        }
    }
}