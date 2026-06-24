package com.pando.app.features.auth.ui.verifyotp

import androidx.navigation.fragment.findNavController
import com.pando.app.R
import com.pando.app.core.base.BaseFragment
import com.pando.app.databinding.FragmentVerifyOtpBinding

class VerifyOtpFragment : BaseFragment<FragmentVerifyOtpBinding>(FragmentVerifyOtpBinding::inflate) {
    override fun initData() {
    }

    override fun initView() {
    }

    override fun initActionView() {
        binding.backButton.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.verifyOtpBtn.setOnClickListener {
            val isRegister = arguments?.getString("is_register") ?: ""

            if (isRegister == "true") {

            } else {
                findNavController().navigate(R.id.action_verifyOtpFragment_to_resetPasswordFragment)
            }
        }
    }
}