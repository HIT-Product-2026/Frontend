package com.pando.app.features.home.ui.profile

import androidx.core.os.bundleOf
import com.pando.app.core.base.BaseBottomSheet
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
        binding.chooseImage.setOnClickListener {
            parentFragmentManager.setFragmentResult(
                REQUEST_KEY,
                bundleOf(RESULT_KEY to ACTION_CHOOSE_IMAGE)
            )

            dismiss()
        }
    }
}