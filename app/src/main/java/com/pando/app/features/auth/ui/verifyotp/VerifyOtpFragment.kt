package com.pando.app.features.auth.ui.verifyotp

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.snackbar.Snackbar
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
        val isRegister = args.isRegister
        binding.backButton.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.pinViewOtp.doOnTextChanged { text, _, _, _ ->
            val otp = binding.pinViewOtp.text.toString()
            if (text != null && text.length == 6) {
                hideKeyboard()
                if (isRegister == "true") {
                    viewModel.registerVerify(email, otp)
                } else {
                    viewModel.registerForgotPassword(email, otp)
                }
            }
        }

        binding.verifyOtpButton.setOnClickListener {
            val otp = binding.pinViewOtp.text.toString()

            if (otp.length < 6) {
                binding.pinViewOtp.error = "Nhập đủ 6 chữ số"
            } else {
                hideKeyboard()
                if (isRegister == "true") {
                    viewModel.registerVerify(email, otp)
                } else {
                    viewModel.registerForgotPassword(email, otp)
                }
            }
        }

        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is UiState.Idle -> {
                    }

                    is UiState.Loading -> {
                        binding.verifyOtpButton.isEnabled = false
                        binding.verifyOtpText.visibility = View.GONE
                        binding.verifyOtpProgressBar.visibility = View.VISIBLE
                    }

                    is UiState.Success -> {
                        binding.verifyOtpText.visibility = View.VISIBLE
                        binding.verifyOtpProgressBar.visibility = View.GONE

                        when (val result = state.data) {
                            is VerifyOtpResult.RegisterSuccess -> {
                                Snackbar.make(
                                    binding.root,
                                    "Đăng ký thành công!",
                                    Snackbar.LENGTH_SHORT
                                ).show()
                                findNavController().popBackStack(R.id.startFragment, false)
                            }

                            is VerifyOtpResult.ForgotPasswordSuccess -> {
                                Snackbar.make(
                                    binding.root,
                                    "Xác thực OTP thành công!",
                                    Snackbar.LENGTH_SHORT
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
                        binding.verifyOtpButton.isEnabled = true
                        binding.verifyOtpText.visibility = View.VISIBLE
                        binding.verifyOtpProgressBar.visibility = View.GONE
                    }
                }
            }
        }
    }

    fun Fragment.hideKeyboard() {
        val view = activity?.currentFocus ?: View(activity)
        val imm = activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)

        view.clearFocus()
    }
}