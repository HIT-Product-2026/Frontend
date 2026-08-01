package com.pando.app.features.auth.ui.login

import android.view.View
import android.view.WindowManager
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.pando.app.R
import com.pando.app.core.base.BaseBottomSheet
import com.pando.app.core.base.BaseVM
import com.pando.app.core.state.UiState
import com.pando.app.databinding.FragmentLoginBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginFragment : BaseBottomSheet<FragmentLoginBinding>(FragmentLoginBinding::inflate) {
    private val viewModel: LoginViewModel by viewModels()

    override fun initView() {
        dialog?.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    }

    override fun initActionView() {
        binding.registerButton.setOnClickListener {
            findNavController().navigate(R.id.action_loginBottomSheet_to_registerBottomSheet)
        }

        binding.loginButton.setOnClickListener {
            login()
        }

        binding.forgotPasswordBtn.setOnClickListener {
            findNavController().navigate(R.id.action_loginBottomSheet_to_forgotPasswordFragment)
        }

        binding.passwordET.setOnEditorActionListener { _, i, _ ->
            if (i == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT ||
                i == android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            ) {

                login()

                true
            } else {
                false
            }
        }

        binding.emailET.doOnTextChanged { _, _, _, _ ->
            binding.emailLayout.error = null
            viewModel.clearResult()
        }

        binding.passwordET.doOnTextChanged { _, _, _, _ ->
            binding.passwordLayout.error = null
            viewModel.clearResult()
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is UiState.Idle -> {

                        }

                        is UiState.Loading -> {
                            binding.loginButton.isEnabled = false
                            binding.loginText.visibility = View.GONE
                            binding.loginProgressBar.visibility = View.VISIBLE
                        }

                        is UiState.Success -> {
                            binding.loginText.visibility = View.VISIBLE
                            binding.loginProgressBar.visibility = View.GONE
                        }

                        is UiState.Error -> {
                            binding.loginButton.isEnabled = true
                            binding.loginText.visibility = View.VISIBLE
                            binding.loginProgressBar.visibility = View.GONE

                            binding.emailLayout.error = null
                            binding.passwordLayout.error = null

                            val emailText = binding.emailET.text.toString()
                            val passwordText = binding.passwordET.text.toString()
                            val isEmailValid =
                                android.util.Patterns.EMAIL_ADDRESS.matcher(emailText).matches()

                            if (emailText.isEmpty()) {
                                binding.emailLayout.error = "Vui lòng nhập email"
                            } else if (!isEmailValid) {
                                binding.emailLayout.error = "Vui lòng nhập đúng định dạng email"
                            }

                            if (passwordText.isEmpty()) {
                                binding.passwordLayout.error = "Vui lòng nhập mật khẩu"
                            }

                            if (isEmailValid && passwordText.isNotEmpty()) {
                                binding.emailLayout.error = null
                                binding.passwordLayout.error = state.message
                            }
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.event.collect { event ->
                    when (event) {
                        is BaseVM.ViewModelEvent.ShowSnackbar -> {
                            Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG).show()
                        }

                        is BaseVM.ViewModelEvent.Navigate -> {
                            val navOptions = NavOptions.Builder()
                                .setPopUpTo(R.id.startFragment, true)
                                .build()

                            findNavController().navigate(event.actionId, null, navOptions)
                        }
                    }
                }
            }
        }
    }

    private fun login() {
        val email = binding.emailET.text.toString()
        val password = binding.passwordET.text.toString()
        viewModel.login(email, password)
    }
}