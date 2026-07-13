package com.pando.app.features.home.ui.setting

import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.pando.app.R
import com.pando.app.core.base.BaseFragment
import com.pando.app.core.network.TokenManager
import com.pando.app.databinding.FragmentSettingBinding
import com.pando.app.features.home.data.model.entity.DataFriendItem
import com.pando.app.features.home.data.model.entity.DataReceivedRequestItem
import com.pando.app.features.home.data.model.entity.DataSearchItem
import com.pando.app.features.home.data.model.entity.DataSentRequestItem
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject

@AndroidEntryPoint
class SettingFragment : BaseFragment<FragmentSettingBinding>(FragmentSettingBinding::inflate) {
    @Inject
    lateinit var tokenManager: TokenManager

    override fun initData() {
    }

    override fun initView() {
    }

    override fun initActionView() {
        binding.backButton.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.logoutBtn.setOnClickListener {
            tokenManager.clear()

            DataSentRequestItem.apply {
                data.clear()
                total = 0
            }

            DataFriendItem.apply {
                data.clear()
                total = 0
            }

            DataReceivedRequestItem.apply {
                data.clear()
                total = 0
            }

            DataSearchItem.apply {
                data.clear()
                total = 0
            }

            val navOptions = NavOptions.Builder()
                .setPopUpTo(R.id.nav_graph, true)
                .build()

            findNavController().navigate(
                R.id.startFragment,
                null,
                navOptions
            )
        }
    }

}