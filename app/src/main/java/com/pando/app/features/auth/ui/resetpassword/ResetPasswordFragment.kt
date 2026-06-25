package com.pando.app.features.auth.ui.resetpassword

import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.pando.app.R
import com.pando.app.core.base.BaseFragment
import com.pando.app.core.ui.UiState
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

        binding.resetPasswordBtn.setOnClickListener {
            val password = binding.newPassword.text.toString()
            val confirmPassword = binding.confirmPassword.text.toString()
            viewModel.resetPassword(email, password, confirmPassword)
        }

        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is UiState.Idle -> {

                    }
                    is UiState.Loading -> {

                    }
                    is UiState.Success -> {
                        findNavController().getBackStackEntry(R.id.startFragment)
                            .savedStateHandle
                            .set("is_verified", true)
                        findNavController().popBackStack(R.id.startFragment, false)
                    }
                    is UiState.Error -> {

                    }
                }
            }
        }
    }
}