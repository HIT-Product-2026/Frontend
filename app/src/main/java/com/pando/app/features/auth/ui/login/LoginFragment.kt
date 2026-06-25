package com.pando.app.features.auth.ui.login

import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.pando.app.R
import com.pando.app.core.base.BaseBottomSheet
import com.pando.app.core.ui.UiState
import com.pando.app.databinding.FragmentLoginBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginFragment : BaseBottomSheet<FragmentLoginBinding>(FragmentLoginBinding::inflate) {
    private val viewModel: LoginViewModel by viewModels()

    override fun initView() {
    }

    override fun initActionView() {
        binding.registerButton.setOnClickListener {
            findNavController().navigate(R.id.action_loginBottomSheet_to_registerBottomSheet)
        }

        binding.loginButton.setOnClickListener {
            val email = binding.emailET.text.toString()
            val password = binding.passwordET.text.toString()
            viewModel.login(email, password)
        }

        binding.forgotPasswordBtn.setOnClickListener {
            findNavController().navigate(R.id.action_loginBottomSheet_to_forgotPasswordFragment)
        }

        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is UiState.Idle -> {

                    }
                    is UiState.Loading -> {

                    }
                    is UiState.Success -> {
                        binding.loginButton.text = "Done"
                    }
                    is UiState.Error -> {

                    }
                }
            }
        }
    }
}