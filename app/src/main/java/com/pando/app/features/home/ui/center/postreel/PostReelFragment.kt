package com.pando.app.features.home.ui.center.postreel

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.pando.app.core.base.BaseAdapter
import com.pando.app.core.base.BaseDiffCallBack
import com.pando.app.core.base.BaseFragment
import com.pando.app.core.extensions.loadAvatar
import com.pando.app.core.session.UserSession
import com.pando.app.core.state.UiState
import com.pando.app.databinding.FragmentPostReelBinding
import com.pando.app.databinding.ItemPostReelBinding
import com.pando.app.features.home.data.model.entity.DataPostReelItem
import com.pando.app.features.home.data.model.entity.PostReelItemModel
import com.pando.app.features.shared.AvatarViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class PostReelFragment : BaseFragment<FragmentPostReelBinding>(FragmentPostReelBinding::inflate) {
    private var avatarMap: Map<UUID, String> = emptyMap()
    private var imageMap: Map<UUID, String> = emptyMap()
    private val postReelViewModel: PostReelViewModel by viewModels()
    private val avatarViewModel: AvatarViewModel by activityViewModels()

    @Inject
    lateinit var userSession: UserSession
    private val postReelAdapter: BaseAdapter<PostReelItemModel, ItemPostReelBinding> by lazy {
        BaseAdapter(
            ItemPostReelBinding::inflate,
            BaseDiffCallBack()
        ) { itemBinding, item ->

            val image = imageMap[item.id]

            Glide.with(this)
                .load(image)
                .into(itemBinding.imgCaptured)

            if (image == null) {
                postReelViewModel.loadPost(item.id)
            }

            bindAvatar(itemBinding.profileIcon, item.user.id)

            itemBinding.nameTV.text = item.user.displayName.ifEmpty { item.user.username }

            itemBinding.captionTV.apply {
                text = item.caption
                visibility = if (item.caption?.isBlank() == true) View.GONE else View.VISIBLE
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
        loadCurrentUser()
        setupPostReel()
        setupKeyboardInsets()

        postReelAdapter.submitList(
            DataPostReelItem.data.toList()
        )
    }

    override fun initActionView() {
        binding.SendMessageBtn.setOnClickListener {
            binding.bottomLayout.visibility = View.VISIBLE
            binding.sendMessageET.requestFocus()

            val inputMethodManager = requireContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

            inputMethodManager.showSoftInput(
                binding.sendMessageET,
                InputMethodManager.SHOW_IMPLICIT
            )
        }

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
                                when (val result = state.data) {
                                    is PostEvent.GetPostEvent -> {
                                        postReelAdapter.submitList(DataPostReelItem.data.toList())
                                    }

                                    is PostEvent.SendImagePost -> {

                                    }
                                }
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

    private fun loadCurrentUser() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userSession.currentUser.collect { user ->
                    binding.profileIcon.loadAvatar(user?.avatar)
                }
            }
        }
    }

    override fun onDestroyView() {
        binding.postReelViewPager
            .unregisterOnPageChangeCallback(pageChangeCallback)

        super.onDestroyView()
    }

    private fun setupKeyboardInsets() {

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())

            binding.bottomLayout.updatePadding(bottom = imeInsets.bottom)

            val isKeyboardVisible =
                windowInsets.isVisible(WindowInsetsCompat.Type.ime())

            if (!isKeyboardVisible) {
                binding.sendMessageET.clearFocus()
                binding.sendMessageET.text?.clear()
                binding.bottomLayout.visibility = View.GONE
            }

            windowInsets
        }

        ViewCompat.requestApplyInsets(binding.root)
    }
}