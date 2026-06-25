package com.pando.app.features.auth.ui.register

import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.pando.app.R
import com.pando.app.core.base.BaseBottomSheet
import com.pando.app.core.ui.UiState
import com.pando.app.databinding.FragmentRegisterBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RegisterFragment : BaseBottomSheet<FragmentRegisterBinding>(FragmentRegisterBinding::inflate) {
    private val viewModel : RegisterViewModel by viewModels()
    override fun initView() {
    }

    override fun initActionView() {
        binding.loginButton.setOnClickListener {
            findNavController().navigate(R.id.action_registerBottomSheet_to_loginBottomSheet)
        }

        lateinit var email : String

        binding.registerButton.setOnClickListener {
            email = binding.emailET.text.toString()
            val password = binding.passwordET.text.toString()
            val confirmPassword = binding.confirmPasswordET.text.toString()
            viewModel.register(email, password, confirmPassword)
        }

        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is UiState.Idle -> {

                    }
                    is UiState.Loading -> {

                    }
                    is UiState.Success -> {
                        val action = RegisterFragmentDirections.actionRegisterBottomSheetToVerifyOtpFragment(
                            isRegister = "true",
                            receiveEmail = email
                        )
                        findNavController().navigate(action)
                    }
                    is UiState.Error -> {

                    }
                }
            }
        }
    }
}