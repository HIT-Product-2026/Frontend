package com.pando.app.features.home.ui.center.postreel

import android.app.DownloadManager
import android.content.Context
import android.os.Bundle
import android.os.Environment
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.pando.app.core.base.BaseBottomSheet
import com.pando.app.databinding.FragmentBottomSheetMorePostReelBinding
import java.util.UUID

class BottomSheetMorePostReelFragment : BaseBottomSheet<FragmentBottomSheetMorePostReelBinding>(FragmentBottomSheetMorePostReelBinding::inflate) {
    companion object {
        private const val ARG_POST_ID = "post_id"
        private const val ARG_IMAGE_URL = "image_url"
        private const val ARG_IS_OWNER = "is_owner"
        const val REQUEST_KEY = "more_post_reel_request"
        const val RESULT_ACTION = "action"
        const val RESULT_POST_ID = "post_id"
        const val ACTION_DELETE_POST = "delete_post"

        fun newInstance(postId: UUID, imageUrl: String, isOwner: Boolean) =
            BottomSheetMorePostReelFragment().apply {
                arguments = bundleOf(
                    ARG_POST_ID to postId.toString(),
                    ARG_IMAGE_URL to imageUrl,
                    ARG_IS_OWNER to isOwner
                )
            }
    }

    private val postId: String
        get() = requireArguments().getString(ARG_POST_ID) ?: error("Thiếu postId")

    private val imageUrl: String
        get() = requireArguments().getString(ARG_IMAGE_URL) ?: error("Thiếu imageUrl")

    override fun initView() {
        binding.removeImage.isVisible = requireArguments().getBoolean(ARG_IS_OWNER)
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

        binding.removeImage.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Xóa bài viết?")
                .setMessage(
                    "Bài viết sẽ bị xóa vĩnh viễn và không thể khôi phục. " +
                            "Bạn có chắc chắn muốn tiếp tục không?"
                )
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa") { _, _ ->
                    parentFragmentManager.setFragmentResult(
                        REQUEST_KEY,
                        Bundle().apply {
                            putString(RESULT_ACTION, ACTION_DELETE_POST)
                            putString(RESULT_POST_ID, postId)
                        }
                    )

                    dismiss()
                }
                .show()
        }
    }
}
