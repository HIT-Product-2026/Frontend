package com.pando.app.features.auth.ui.verifyotp

import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.pando.app.R
import com.pando.app.core.base.BaseFragment
import com.pando.app.core.ui.UiState
import com.pando.app.databinding.FragmentVerifyOtpBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class VerifyOtpFragment :
    BaseFragment<FragmentVerifyOtpBinding>(FragmentVerifyOtpBinding::inflate) {

    private val args: VerifyOtpFragmentArgs by navArgs()
    private val viewModel: VerifyOtpViewModel by viewModels()

    override fun initData() {
    }

    override fun initView() {
        val email = args.receiveEmail
        binding.emailTV.text = email
    }

    override fun initActionView() {
        val email = args.receiveEmail
        binding.backButton.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.verifyOtpBtn.setOnClickListener {
            val otp = binding.pinViewOtp.text.toString()

            val isRegister = args.isRegister
            if (isRegister == "true") {
                viewModel.registerVerify(email, otp)
            } else {
                viewModel.registerForgotPassword(email, otp)
            }
        }

        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is UiState.Idle -> {
                    }

                    is UiState.Loading -> {
                    }

                    is UiState.Success -> {
                        when (val result = state.data) {
                            is VerifyOtpResult.RegisterSuccess -> {
                                Toast.makeText(
                                    requireContext(),
                                    "Đăng ký thành công!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                findNavController().popBackStack(R.id.startFragment, false)
                            }

                            is VerifyOtpResult.ForgotPasswordSuccess -> {
                                Toast.makeText(
                                    requireContext(),
                                    "Xác thực OTP thành công!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                val action =
                                    VerifyOtpFragmentDirections.actionVerifyOtpFragmentToResetPasswordFragment(
                                        receiveEmail = email
                                    )
                                findNavController().navigate(action)
                            }
                        }
                    }

                    is UiState.Error -> {
                    }
                }
            }
        }
    }
}