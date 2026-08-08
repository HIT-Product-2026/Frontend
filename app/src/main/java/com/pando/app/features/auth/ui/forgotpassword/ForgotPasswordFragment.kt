package com.pando.app.features.auth.ui.forgotpassword

import android.text.InputType
import android.view.View
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.pando.app.R
import com.pando.app.core.base.BaseFragment
import com.pando.app.core.extensions.showShortToast
import com.pando.app.core.state.UiState
import com.pando.app.databinding.FragmentForgotPasswordBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.getValue

@AndroidEntryPoint
class ForgotPasswordFragment : BaseFragment<FragmentForgotPasswordBinding>(FragmentForgotPasswordBinding::inflate) {
    private val viewModel : FPViewModel by viewModels()

    override fun initData() {
    }

    override fun initView() {
    }

    override fun initActionView() {
        binding.backButton.setOnClickListener {
            findNavController().popBackStack()
        }

        lateinit var email : String

        binding.forgotPasswordButton.setOnClickListener {
            email = binding.emailET.text.toString()
            viewModel.forgotPassword(email)
        }

        binding.emailET.doOnTextChanged { _, _, _, _ ->
            binding.emailLayout.error = null
            viewModel.clearResult()
        }

        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is UiState.Idle -> {

                    }
                    is UiState.Loading -> {
                        binding.forgotPasswordButton.isEnabled = false
                        binding.forgotPasswordText.visibility = View.GONE
                        binding.forgotPasswordProgressBar.visibility = View.VISIBLE
                    }
                    is UiState.Success -> {
                        binding.forgotPasswordText.visibility = View.VISIBLE
                        binding.forgotPasswordProgressBar.visibility = View.GONE
                        requireContext().showShortToast(R.string.otp_sent_success)

                        val action = ForgotPasswordFragmentDirections.actionForgotPasswordFragmentToVerifyOtpFragment(
                            receiveEmail = email
                        )
                        findNavController().navigate(action)
                    }
                    is UiState.Error -> {
                        binding.forgotPasswordButton.isEnabled = true
                        binding.forgotPasswordText.visibility = View.VISIBLE
                        binding.forgotPasswordProgressBar.visibility = View.GONE

                        binding.emailLayout.error = null

                        val emailText = binding.emailET.text.toString()

                        if (emailText.isEmpty()) {
                            binding.emailLayout.error = "Vui lòng nhập email"
                        }
                        if (binding.emailET.inputType != InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS && emailText.isNotEmpty()) {
                            binding.emailLayout.error = "Vui lòng nhập đúng định dạng email"
                        }
                        if (emailText.isNotEmpty()) {
                            binding.emailLayout.error = state.message
                        }
                    }
                }
            }
        }
    }

}
