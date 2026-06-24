package com.pando.app.features.auth.ui.start

import androidx.navigation.fragment.findNavController
import com.pando.app.R
import com.pando.app.core.base.BaseFragment
import com.pando.app.databinding.FragmentStartBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class StartFragment : BaseFragment<FragmentStartBinding>(FragmentStartBinding::inflate) {
    override fun initData() {
    }

    override fun initView() {
        findNavController().currentBackStackEntry?.savedStateHandle?.getLiveData<Boolean>("is_verified")
            ?.observe(viewLifecycleOwner) { isVerified ->
                if (isVerified == true) {

                    findNavController().currentBackStackEntry?.savedStateHandle?.remove<Boolean>("is_verified")

                    findNavController().navigate(R.id.action_startFragment_to_loginFragment)
                }
            }
    }

    override fun initActionView() {
        binding.loginButton.setOnClickListener {
            findNavController().navigate(R.id.action_startFragment_to_loginFragment)
        }

        binding.registerButton.setOnClickListener {
            findNavController().navigate(R.id.action_startFragment_to_registerFragment)
        }
    }
}