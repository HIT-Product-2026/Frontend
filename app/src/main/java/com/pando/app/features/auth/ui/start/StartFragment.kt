package com.pando.app.features.auth.ui.start

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.pando.app.databinding.FragmentStartBinding
import com.pando.app.features.auth.ui.login.LoginFragment
import com.pando.app.features.auth.ui.register.RegisterFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class StartFragment : Fragment() {

    private var _binding: FragmentStartBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStartBinding.inflate(inflater, container, false)
        // Inflate the layout for this fragment
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.loginButton.setOnClickListener {
            val loginBottomSheet = LoginFragment()
            loginBottomSheet.show(parentFragmentManager, "LoginBottomSheetFragment")
        }

        binding.registerButton.setOnClickListener {
            val loginBottomSheet = RegisterFragment()
            loginBottomSheet.show(parentFragmentManager, "RegisterBottomSheetFragment")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}