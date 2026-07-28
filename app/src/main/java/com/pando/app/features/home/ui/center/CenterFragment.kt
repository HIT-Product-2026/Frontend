package com.pando.app.features.home.ui.center

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.pando.app.R
import com.pando.app.core.base.BaseFragment
import com.pando.app.core.extensions.loadAvatar
import com.pando.app.core.session.UserSession
import com.pando.app.databinding.FragmentCenterBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CenterFragment : BaseFragment<FragmentCenterBinding>(FragmentCenterBinding::inflate) {
    companion object {
        const val PAGE_MAP = 0
        const val PAGE_CAMERA = 1
        const val PAGE_POST_REEL = 2
    }

    @Inject
    lateinit var userSession: UserSession

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.verticalViewPager.apply {
            adapter = CenterPagerAdapter(this@CenterFragment)
            orientation = ViewPager2.ORIENTATION_VERTICAL

            if (savedInstanceState == null) {
                setCurrentItem(PAGE_MAP, false)
            }

            isUserInputEnabled = false
        }
    }

    override fun initData() {
    }

    override fun initView() {
        loadCurrentUser()
    }

    override fun initActionView() {
        binding.verticalViewPager.registerOnPageChangeCallback(object :
            ViewPager2.OnPageChangeCallback() {

            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)

                binding.verticalViewPager.isUserInputEnabled = position != PAGE_MAP
                binding.topBar.isVisible = position != PAGE_MAP
            }
        })

        binding.chatBtn.setOnClickListener {
            findNavController().navigate(R.id.action_centerFragment_to_chatMenuFragment)
        }

        binding.friendBtn.setOnClickListener {
            findNavController().navigate(R.id.action_centerFragment_to_friendFragment)
        }

        binding.profileIcon.setOnClickListener {
            findNavController().navigate(R.id.action_centerFragment_to_settingFragment)
        }
    }

    fun openCamera() {
        binding.verticalViewPager.setCurrentItem(PAGE_CAMERA, true)
    }

    fun openPostReel() {
        binding.verticalViewPager.setCurrentItem(PAGE_POST_REEL, true)
    }

    private fun loadCurrentUser() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userSession.currentUser.collect { user ->
                    binding.profileIcon.loadAvatar(user?.avatar)
                }
            }
        }
    }
}