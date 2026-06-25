package com.pando.app.features.auth.ui.forgotpassword

import android.os.Bundle
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.pando.app.R
import com.pando.app.core.base.BaseFragment
import com.pando.app.core.ui.UiState
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

        binding.sendEmailBtn.setOnClickListener {
            email = binding.emailET.text.toString()
            viewModel.forgotPassword(email)
        }

        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is UiState.Idle -> {

                    }
                    is UiState.Loading -> {

                    }
                    is UiState.Success -> {
                        val action = ForgotPasswordFragmentDirections.actionForgotPasswordFragmentToVerifyOtpFragment(
                            receiveEmail = email
                        )
                        findNavController().navigate(action)
                    }
                    is UiState.Error -> {

                    }
                }
            }
        }
    }

}