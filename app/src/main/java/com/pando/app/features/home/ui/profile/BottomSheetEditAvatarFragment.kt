package com.pando.app.features.home.ui.profile

import android.os.Bundle
import androidx.core.os.bundleOf
import com.pando.app.core.base.BaseBottomSheet
import com.pando.app.core.extensions.showComingSoon
import com.pando.app.databinding.FragmentBottomSheetEditAvatarBinding

class BottomSheetEditAvatarFragment
    :
    BaseBottomSheet<FragmentBottomSheetEditAvatarBinding>(FragmentBottomSheetEditAvatarBinding::inflate) {

    companion object {
        const val REQUEST_KEY = "edit_avatar_request"
        const val RESULT_KEY = "avatar_action"
        const val ACTION_CHOOSE_IMAGE = "choose_image"
    }

    override fun initView() {
    }

    override fun initActionView() {
        listOf(binding.captureImage, binding.removeImage).forEach { view ->
            view.setOnClickListener {
                requireContext().showComingSoon()
            }
        }

        binding.chooseImage.setOnClickListener {
            parentFragmentManager.setFragmentResult(
                REQUEST_KEY,
                Bundle().apply {
                    putString(RESULT_KEY, ACTION_CHOOSE_IMAGE)
                }
            )

            dismiss()
        }
    }
}
