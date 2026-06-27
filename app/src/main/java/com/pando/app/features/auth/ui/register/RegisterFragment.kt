package com.pando.app.features.auth.ui.register

import android.text.InputType
import android.view.View
import androidx.core.widget.doOnTextChanged
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

        binding.emailET.doOnTextChanged { _, _, _, _ ->
            binding.emailLayout.error = null
            viewModel.clearResult()
        }

        binding.passwordET.doOnTextChanged { _, _, _, _ ->
            binding.passwordLayout.error = null
            viewModel.clearResult()
        }

        binding.confirmPasswordET.doOnTextChanged { _, _, _, _ ->
            binding.confirmPasswordLayout.error = null
            viewModel.clearResult()
        }

        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is UiState.Idle -> {

                    }
                    is UiState.Loading -> {
                        binding.registerButton.isEnabled = false
                        binding.registerText.visibility = View.GONE
                        binding.registerProgressBar.visibility = View.VISIBLE
                    }
                    is UiState.Success -> {
                        binding.registerText.visibility = View.VISIBLE
                        binding.registerProgressBar.visibility = View.GONE

                        val action = RegisterFragmentDirections.actionRegisterBottomSheetToVerifyOtpFragment(
                            isRegister = "true",
                            receiveEmail = email
                        )
                        findNavController().navigate(action)
                    }
                    is UiState.Error -> {
                        binding.registerButton.isEnabled = true
                        binding.registerText.visibility = View.VISIBLE
                        binding.registerProgressBar.visibility = View.GONE

                        binding.emailLayout.error = null
                        binding.passwordLayout.error = null
                        binding.confirmPasswordLayout.error = null

                        val emailText = binding.emailET.text.toString()
                        val passwordText = binding.passwordET.text.toString()
                        val confirmPasswordText = binding.confirmPasswordET.text.toString()

                        if (emailText.isEmpty()) {
                            binding.emailLayout.error = "Vui lòng nhập email"
                        }
                        if (passwordText.isEmpty()) {
                            binding.passwordLayout.error = "Vui lòng nhập mật khẩu"
                        }
                        if (confirmPasswordText.isEmpty()) {
                            binding.confirmPasswordLayout.error = "Vui lòng nhập mật khẩu"
                        }
                        if (binding.emailET.inputType != InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS && emailText.isNotEmpty()) {
                            binding.emailLayout.error = "Vui lòng nhập đúng định dạng email"
                        }
                        if (passwordText.isNotEmpty() && confirmPasswordText.isNotEmpty() && emailText.isNotEmpty()) {
                            binding.emailLayout.error = ""
                            binding.passwordLayout.error = ""
                            binding.confirmPasswordLayout.error = state.message
                        }
                    }
                }
            }
        }
    }
}