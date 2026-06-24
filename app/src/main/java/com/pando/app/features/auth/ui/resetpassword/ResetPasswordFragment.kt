package com.pando.app.features.auth.ui.resetpassword

import androidx.navigation.fragment.findNavController
import com.pando.app.R
import com.pando.app.core.base.BaseFragment
import com.pando.app.databinding.FragmentResetPasswordBinding
import com.pando.app.features.auth.ui.start.StartFragment

class ResetPasswordFragment : BaseFragment<FragmentResetPasswordBinding>(FragmentResetPasswordBinding::inflate) {
    override fun initData() {
    }

    override fun initView() {
    }

    override fun initActionView() {
        binding.backButton.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.resetPasswordBtn.setOnClickListener {
            findNavController().getBackStackEntry(R.id.startFragment)
                .savedStateHandle
                .set("is_verified", true)
            findNavController().popBackStack(R.id.startFragment, false)
        }
    }
}