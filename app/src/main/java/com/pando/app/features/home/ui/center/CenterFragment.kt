package com.pando.app.features.home.ui.center

import android.os.Bundle
import android.view.View
import androidx.viewpager2.widget.ViewPager2
import com.pando.app.core.base.BaseFragment
import com.pando.app.databinding.FragmentCenterBinding

interface OnVerticalPageChangedListener {
    fun onVerticalPageChanged(position: Int)
}

class CenterFragment : BaseFragment<FragmentCenterBinding>(FragmentCenterBinding::inflate) {
    companion object {
        const val PAGE_MAP = 0
        const val PAGE_CAMERA = 1
        const val PAGE_POST_REEL = 2
    }

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
            }
        })
    }

    fun openCamera() {
        binding.verticalViewPager.setCurrentItem(PAGE_CAMERA, true)
    }

    fun openPostReel() {
        binding.verticalViewPager.setCurrentItem(PAGE_POST_REEL, true)
    }
}