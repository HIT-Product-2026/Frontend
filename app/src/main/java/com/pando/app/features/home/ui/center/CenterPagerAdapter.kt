package com.pando.app.features.home.ui.center

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.pando.app.features.home.ui.center.camera.CameraFragment
import com.pando.app.features.home.ui.center.map.MapFragment
import com.pando.app.features.home.ui.center.postreel.PostReelFragment

class CenterPagerAdapter (
    fragment: Fragment
) : FragmentStateAdapter(fragment){
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            CenterFragment.PAGE_MAP -> MapFragment()
            CenterFragment.PAGE_CAMERA -> CameraFragment()
            CenterFragment.PAGE_POST_REEL -> PostReelFragment()

            else -> error("Invalid vertical page: $position")
        }
    }

    override fun getItemCount(): Int = 3
}
