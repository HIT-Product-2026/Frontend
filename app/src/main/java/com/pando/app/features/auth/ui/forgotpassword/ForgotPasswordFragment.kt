package com.pando.app.features.auth.ui.forgotpassword

import androidx.navigation.fragment.findNavController
import com.pando.app.R
import com.pando.app.core.base.BaseFragment
import com.pando.app.databinding.FragmentForgotPasswordBinding

class ForgotPasswordFragment : BaseFragment<FragmentForgotPasswordBinding>(FragmentForgotPasswordBinding::inflate) {
    override fun initData() {
    }

    override fun initView() {
    }

    override fun initActionView() {
        binding.backButton.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.sendEmailBtn.setOnClickListener {
            findNavController().navigate(R.id.action_forgotPasswordFragment_to_verifyOtpFragment)
        }
    }

}