package com.pando.app.features.home.ui.center.postreel

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
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
import androidx.recyclerview.widget.RecyclerView
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
import com.pando.app.features.home.ui.center.CenterFragment
import com.pando.app.features.shared.AvatarViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs

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
        setupPostReel()
        setupNestedPagerGesture()
        setupKeyboardInsets()

        postReelAdapter.submitList(
            DataPostReelItem.data.toList()
        )
    }

    override fun initActionView() {
        binding.btnCapture.setOnClickListener {
            (parentFragment as? CenterFragment)?.openCamera()
        }

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

    @SuppressLint("ClickableViewAccessibility")
    private fun setupNestedPagerGesture() {
        val reelRecyclerView =
            binding.postReelViewPager.getChildAt(0) as RecyclerView

        val touchSlop =
            ViewConfiguration.get(requireContext()).scaledTouchSlop

        var startX = 0f
        var startY = 0f

        reelRecyclerView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y

                    // Ban đầu ưu tiên pager Reel.
                    binding.postReelViewPager.parent
                        .requestDisallowInterceptTouchEvent(true)
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.x - startX
                    val deltaY = event.y - startY

                    val isVerticalGesture =
                        abs(deltaY) > abs(deltaX) &&
                                abs(deltaY) > touchSlop

                    if (isVerticalGesture) {
                        val isSwipingDown = deltaY > 0
                        val isAtFirstReel =
                            binding.postReelViewPager.currentItem == 0

                        val shouldParentHandle =
                            isSwipingDown && isAtFirstReel

                        binding.postReelViewPager.parent
                            .requestDisallowInterceptTouchEvent(
                                !shouldParentHandle
                            )
                    }
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    binding.postReelViewPager.parent
                        .requestDisallowInterceptTouchEvent(false)
                }
            }

            // Không tự tiêu thụ touch, RecyclerView vẫn xử lý swipe.
            false
        }
    }

    override fun onPause() {
        binding.postReelViewPager.setCurrentItem(0, false)
        super.onPause()
    }
}