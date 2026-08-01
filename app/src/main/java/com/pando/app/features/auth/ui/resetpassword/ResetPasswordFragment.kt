package com.pando.app.features.auth.ui.resetpassword

import android.view.View
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.snackbar.Snackbar
import com.pando.app.R
import com.pando.app.core.base.BaseFragment
import com.pando.app.core.state.UiState
import com.pando.app.databinding.FragmentResetPasswordBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ResetPasswordFragment :
    BaseFragment<FragmentResetPasswordBinding>(FragmentResetPasswordBinding::inflate) {
    private val args : ResetPasswordFragmentArgs by navArgs()
    private val viewModel: ResetPasswordViewModel by viewModels()

    override fun initData() {
    }

    override fun initView() {
    }

    override fun initActionView() {
        binding.backButton.setOnClickListener {
            findNavController().popBackStack()
        }

        val email = args.receiveEmail

        binding.resetPasswordButton.setOnClickListener {
            val password = binding.newPassword.text.toString()
            val confirmPassword = binding.confirmPassword.text.toString()
            viewModel.resetPassword(email, password, confirmPassword)
        }

        binding.newPassword.doOnTextChanged { _, _, _, _ ->
            binding.newPasswordLayout.error = null
            viewModel.clearResult()
        }

        binding.confirmPassword.doOnTextChanged { _, _, _, _ ->
            binding.confirmPasswordLayout.error = null
            viewModel.clearResult()
        }

        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is UiState.Idle -> {

                    }
                    is UiState.Loading -> {
                        binding.resetPasswordButton.isEnabled = false
                        binding.resetPasswordText.visibility = View.GONE
                        binding.resetPasswordProgressBar.visibility = View.VISIBLE
                    }
                    is UiState.Success -> {
                        binding.resetPasswordText.visibility = View.VISIBLE
                        binding.resetPasswordProgressBar.visibility = View.GONE
                        Snackbar.make(
                            binding.root,
                            "Thay đổi mật khẩu thành công!",
                            Snackbar.LENGTH_SHORT
                        ).show()

                        findNavController().getBackStackEntry(R.id.startFragment)
                            .savedStateHandle
                            .set("is_verified", true)
                        findNavController().popBackStack(R.id.startFragment, false)
                    }
                    is UiState.Error -> {
                        binding.resetPasswordButton.isEnabled = true
                        binding.resetPasswordText.visibility = View.VISIBLE
                        binding.resetPasswordProgressBar.visibility = View.GONE

                        binding.newPasswordLayout.error = null
                        binding.confirmPasswordLayout.error = null

                        val passwordText = binding.newPassword.text.toString()
                        val confirmPasswordText = binding.confirmPassword.text.toString()

                        if (passwordText.isEmpty()) {
                            binding.newPasswordLayout.error = "Vui lòng nhập mật khẩu"
                        }
                        if (confirmPasswordText.isEmpty()) {
                            binding.confirmPasswordLayout.error = "Vui lòng nhập mật khẩu"
                        }
                        if (passwordText.isNotEmpty() && confirmPasswordText.isNotEmpty()) {
                            binding.newPasswordLayout.error = ""
                            binding.confirmPasswordLayout.error = state.message
                        }
                    }
                }
            }
        }
    }
}