package com.pando.app.features.home.ui.postreel

import android.view.View
import android.widget.ImageView
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.pando.app.R
import com.pando.app.core.base.BaseAdapter
import com.pando.app.core.base.BaseDiffCallBack
import com.pando.app.core.base.BaseFragment
import com.pando.app.core.extensions.loadAvatar
import com.pando.app.core.ui.UiState
import com.pando.app.databinding.FragmentPostReelBinding
import com.pando.app.databinding.ItemPostReelBinding
import com.pando.app.features.home.data.model.entity.DataPostReelItem
import com.pando.app.features.home.data.model.entity.PostReelItemModel
import com.pando.app.features.shared.AvatarViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.UUID

@AndroidEntryPoint
class PostReelFragment : BaseFragment<FragmentPostReelBinding>(FragmentPostReelBinding::inflate) {
    private var avatarMap: Map<UUID, ByteArray> = emptyMap()
    private var imageMap: Map<UUID, ByteArray> = emptyMap()
    private val postReelViewModel: PostReelViewModel by viewModels()
    private val avatarViewModel: AvatarViewModel by activityViewModels()
    private val postReelAdapter : BaseAdapter<PostReelItemModel, ItemPostReelBinding> by lazy {
        BaseAdapter(
            ItemPostReelBinding::inflate,
            BaseDiffCallBack()
        ) { itemBinding, item ->

            val image = imageMap[item.id]

            Glide.with(this)
                .load(image)
                .circleCrop()
                .into(itemBinding.imgCaptured)

            if (image == null) {
                postReelViewModel.loadPost(item.id)
            }

            bindAvatar(itemBinding.profileIcon, item.user.id)

            itemBinding.nameTV.text = item.user.displayName.ifEmpty { item.user.username }

            itemBinding.captionTV.apply {
                text = item.caption
                visibility = if (item.caption.isBlank()) View.GONE else View.VISIBLE
            }
        }
    }

    private val pageChangeCallback =
        object : ViewPager2.OnPageChangeCallback() {

            override fun onPageSelected(position: Int) {
                val itemCount = postReelAdapter.itemCount

                val shouldLoadNextPage =
                    itemCount > 0 && position >= itemCount - 3

                if (shouldLoadNextPage) {
                    postReelViewModel.getPosts()
                }
            }
        }
    
    override fun initData() {
        DataPostReelItem.reset()
        if (DataPostReelItem.data.isEmpty()) {
            postReelViewModel.getPosts()
        }
    }

    override fun initView() {
        setupPostReel()

        postReelAdapter.submitList(
            DataPostReelItem.data.toList()
        )
    }

    override fun initActionView() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    postReelViewModel.images.collect { images ->
                        imageMap = images
                        postReelAdapter.notifyDataSetChanged()
                    }
                }

                launch {
                    avatarViewModel.avatars.collect { avatars ->
                        avatarMap = avatars
                        postReelAdapter.notifyDataSetChanged()
                    }
                }

                launch {
                    postReelViewModel.uiState.collect { state ->
                        when (state) {
                            is UiState.Success -> {
                                postReelAdapter.submitList(DataPostReelItem.data.toList())
                            }

                            is UiState.Error -> {}

                            else -> {}
                        }
                    }
                }
            }
        }
    }

    private fun setupPostReel() {
        binding.postReelViewPager.apply {
            orientation = ViewPager2.ORIENTATION_VERTICAL
            adapter = postReelAdapter
            registerOnPageChangeCallback(pageChangeCallback)
        }
    }

    private fun bindAvatar(imageView: ImageView, userId: UUID) {
        val avatar = avatarMap[userId]

        imageView.loadAvatar(avatar)

        if (avatar == null) {
            avatarViewModel.loadAvatar(userId)
        }
    }

    override fun onDestroyView() {
        binding.postReelViewPager
            .unregisterOnPageChangeCallback(pageChangeCallback)

        super.onDestroyView()
    }
}