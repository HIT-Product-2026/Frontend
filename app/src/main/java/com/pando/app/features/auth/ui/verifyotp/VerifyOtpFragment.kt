package com.pando.app.features.auth.ui.verifyotp

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.addCallback
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.pando.app.R
import com.pando.app.core.base.BaseFragment
import com.pando.app.core.extensions.showShortToast
import com.pando.app.core.state.UiState
import com.pando.app.databinding.FragmentVerifyOtpBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
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
        val isRegister = args.isRegister == "true"
        binding.backButton.setOnClickListener {
            navigateBack(email, isRegister)
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            navigateBack(email, isRegister)
        }

        binding.sendEmailAgain.setOnClickListener {
            viewModel.resendOtp(email, isRegister)
        }

        binding.pinViewOtp.doOnTextChanged { text, _, _, _ ->
            val otp = binding.pinViewOtp.text.toString()
            if (text != null && text.length == 6) {
                hideKeyboard()
                if (isRegister) {
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
                if (isRegister) {
                    viewModel.registerVerify(email, otp)
                } else {
                    viewModel.registerForgotPassword(email, otp)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        when (state) {
                            is UiState.Idle -> {
                                binding.verifyOtpButton.isEnabled = true
                                binding.verifyOtpText.visibility = View.VISIBLE
                                binding.verifyOtpProgressBar.visibility = View.GONE
                            }

                            is UiState.Loading -> {
                                binding.verifyOtpButton.isEnabled = false
                                binding.verifyOtpText.visibility = View.GONE
                                binding.verifyOtpProgressBar.visibility = View.VISIBLE
                            }

                            is UiState.Success -> {
                                binding.verifyOtpText.visibility = View.VISIBLE
                                binding.verifyOtpProgressBar.visibility = View.GONE
                                viewModel.clearResult()

                                when (val result = state.data) {
                                    is VerifyOtpResult.RegisterSuccess -> {
                                        requireContext().showShortToast(R.string.register_success)
                                        findNavController().popBackStack(R.id.startFragment, false)
                                    }

                                    is VerifyOtpResult.ForgotPasswordSuccess -> {
                                        requireContext().showShortToast(R.string.otp_verified_success)
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
                                binding.pinViewOtp.error = state.message
                            }
                        }
                    }
                }

                launch {
                    combine(
                        viewModel.resendState,
                        viewModel.resendCooldownSeconds
                    ) { state, seconds -> state to seconds }
                        .collect { (state, seconds) ->
                            renderResendState(state, seconds)
                        }
                }
            }
        }
    }

    private fun renderResendState(state: ResendOtpState, seconds: Int) {
        val isLoading = state is ResendOtpState.Loading
        binding.sendEmailAgain.isEnabled = seconds == 0 && !isLoading
        binding.sendEmailAgain.alpha = if (binding.sendEmailAgain.isEnabled) 1f else 0.5f
        binding.sendEmailAgain.text = when {
            isLoading -> getString(R.string.otp_resending)
            seconds > 0 -> getString(R.string.otp_resend_countdown, seconds)
            else -> getString(R.string.otp_resend)
        }

        when (state) {
            ResendOtpState.Success -> {
                binding.pinViewOtp.setText("")
                binding.pinViewOtp.error = null
                requireContext().showShortToast(R.string.otp_resent_success)
                viewModel.consumeResendResult()
            }

            is ResendOtpState.Error -> {
                requireContext().showShortToast(state.message)
                viewModel.consumeResendResult()
            }

            ResendOtpState.Idle,
            ResendOtpState.Loading -> Unit
        }
    }

    private fun navigateBack(email: String, isRegister: Boolean) {
        if (isRegister) {
            viewModel.clearPendingRegistration(email)
        }
        findNavController().popBackStack()
    }

    fun Fragment.hideKeyboard() {
        val view = activity?.currentFocus ?: View(activity)
        val imm = activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)

        view.clearFocus()
    }
}
