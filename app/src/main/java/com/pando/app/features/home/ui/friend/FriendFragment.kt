package com.pando.app.features.home.ui.friend

import androidx.navigation.fragment.findNavController
import com.pando.app.core.base.BaseFragment
import com.pando.app.databinding.FragmentFriendBinding

class FriendFragment : BaseFragment<FragmentFriendBinding>(FragmentFriendBinding::inflate) {
    override fun initData() {
    }

    override fun initView() {
    }

    override fun initActionView() {
        binding.backButton.setOnClickListener {
            findNavController().navigateUp()
        }
    }

}