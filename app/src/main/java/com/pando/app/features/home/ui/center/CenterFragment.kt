package com.pando.app.features.home.ui.center

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.pando.app.R
import com.pando.app.core.base.BaseFragment
import com.pando.app.core.extensions.loadAvatar
import com.pando.app.core.location.LocationNavigationViewModel
import com.pando.app.core.session.UserSession
import com.pando.app.databinding.FragmentCenterBinding
import com.pando.app.features.shared.AvatarViewModel
import com.pando.app.features.widget.WidgetNavigationViewModel
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

    private val widgetNavigationViewModel: WidgetNavigationViewModel by activityViewModels()
    private val locationNavigationViewModel: LocationNavigationViewModel by activityViewModels()
    private val avatarViewModel: AvatarViewModel by activityViewModels()

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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    widgetNavigationViewModel.replyTarget.collect { shouldOpen ->
                        if (shouldOpen) {
                            binding.verticalViewPager.setCurrentItem(PAGE_POST_REEL, false)
                            widgetNavigationViewModel.handledTarget()
                        }
                    }
                }
                launch {
                    locationNavigationViewModel.focusCurrentLocation.collect { shouldFocus ->
                        if (shouldFocus) {
                            binding.verticalViewPager.setCurrentItem(PAGE_MAP, false)
                        }
                    }
                }
                launch {
                    userSession.currentUser.collect { user ->
                        if (user != null && user.avatar == null) {
                            avatarViewModel.loadAvatar(user.id)
                        }

                        binding.profileIcon.loadAvatar(user?.avatar)
                    }
                }
            }
        }
    }

    fun openCamera() {
        binding.verticalViewPager.setCurrentItem(PAGE_CAMERA, true)
    }

    fun openPostReel() {
        binding.verticalViewPager.setCurrentItem(PAGE_POST_REEL, true)
    }
}
