package com.pando.app.features.auth.ui.register

import android.os.Bundle
import androidx.navigation.fragment.findNavController
import com.pando.app.R
import com.pando.app.core.base.BaseBottomSheet
import com.pando.app.databinding.FragmentRegisterBinding

class RegisterFragment : BaseBottomSheet<FragmentRegisterBinding>(FragmentRegisterBinding::inflate) {
    override fun initView() {
    }

    override fun initActionView() {
        binding.loginButton.setOnClickListener {
            findNavController().popBackStack()
            findNavController().navigate(R.id.action_startFragment_to_loginFragment)
        }

        binding.registerButton.setOnClickListener {
            val bundle = Bundle().apply {
                putString("is_register", "true")
            }
            findNavController().navigate(R.id.action_registerBottomSheet_to_verifyOtpFragment, bundle)
        }
    }
}