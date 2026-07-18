package com.pando.app.features.auth.ui.register

import android.text.InputType
import android.view.View
import android.view.WindowManager
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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
    private lateinit var email : String

    override fun initView() {
        dialog?.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    }

    override fun initActionView() {
        binding.loginButton.setOnClickListener {
            findNavController().navigate(R.id.action_registerBottomSheet_to_loginBottomSheet)
        }

        binding.registerButton.setOnClickListener {
            register()
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

        binding.confirmPasswordET.setOnEditorActionListener { _, i, _ ->
            if (i == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT ||
                i == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {

                register()

                true
            } else {
                false
            }
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
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
                            val isEmailValid = android.util.Patterns.EMAIL_ADDRESS.matcher(emailText).matches()

                            if (emailText.isEmpty()) {
                                binding.emailLayout.error = "Vui lòng nhập email"
                            } else if (!isEmailValid) {
                                binding.emailLayout.error = "Vui lòng nhập đúng định dạng email"
                            }

                            if (passwordText.isEmpty()) {
                                binding.passwordLayout.error = "Vui lòng nhập mật khẩu"
                            }

                            if (confirmPasswordText.isEmpty()) {
                                binding.confirmPasswordLayout.error = "Vui lòng nhập mật khẩu"
                            }

                            if (isEmailValid && passwordText.isNotEmpty() && confirmPasswordText.isNotEmpty()) {
                                binding.emailLayout.error = null
                                binding.passwordLayout.error = null
                                binding.confirmPasswordLayout.error = state.message
                            }
                        }
                    }
                }
            }
        }
    }

    private fun register() {
        email = binding.emailET.text.toString()
        val password = binding.passwordET.text.toString()
        val confirmPassword = binding.confirmPasswordET.text.toString()
        viewModel.register(email, password, confirmPassword)
    }
}