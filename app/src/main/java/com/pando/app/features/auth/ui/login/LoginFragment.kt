package com.pando.app.features.auth.ui.login

import androidx.navigation.fragment.findNavController
import com.pando.app.R
import com.pando.app.core.base.BaseBottomSheet
import com.pando.app.databinding.FragmentLoginBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LoginFragment : BaseBottomSheet<FragmentLoginBinding>(FragmentLoginBinding::inflate) {
    override fun initView() {
    }

    override fun initActionView() {
        binding.registerButton.setOnClickListener {
            findNavController().popBackStack()
            findNavController().navigate(R.id.action_startFragment_to_registerFragment)
        }

        binding.forgotPasswordBtn.setOnClickListener {
            findNavController().navigate(R.id.action_loginBottomSheet_to_forgotPasswordFragment)
        }
    }
}