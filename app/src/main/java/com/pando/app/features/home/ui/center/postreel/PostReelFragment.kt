package com.pando.app.features.home.ui.center.postreel

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.Toast
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.pando.app.MainViewModel
import com.pando.app.core.base.BaseAdapter
import com.pando.app.core.base.BaseDiffCallBack
import com.pando.app.core.base.BaseFragment
import com.pando.app.core.extensions.formatDateTime
import com.pando.app.core.extensions.loadAvatar
import com.pando.app.core.session.UserSession
import com.pando.app.core.state.SocketConnectionState
import com.pando.app.core.state.UiState
import com.pando.app.databinding.FragmentPostReelBinding
import com.pando.app.databinding.ItemPostReelBinding
import com.pando.app.features.home.data.model.entity.PostReelItemModel
import com.pando.app.features.home.data.model.entity.enumEntity.NsfwStatus
import com.pando.app.features.home.data.model.entity.enumEntity.NsfwViewDecision
import com.pando.app.features.home.data.model.entity.enumEntity.TypePost
import com.pando.app.features.home.ui.center.CenterFragment
import com.pando.app.features.shared.AvatarViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Period
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class PostReelFragment : BaseFragment<FragmentPostReelBinding>(FragmentPostReelBinding::inflate) {
    private var avatarMap: Map<UUID, String> = emptyMap()
    private val postReelViewModel: PostReelViewModel by viewModels()
    private val avatarViewModel: AvatarViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    @Inject
    lateinit var userSession: UserSession
    private var isSocketConnected = false
    private var hasLoadedInitialData = false
    private var lastRenderedFirstPostId: UUID? = null
    private var activeNsfwDialogPostId: UUID? = null
    private var videoPreviewPlayer: ExoPlayer? = null
    private var activeVideoPostId: UUID? = null
    private var activeVideoView: PlayerView? = null
    private val postReelAdapter: BaseAdapter<PostReelItemModel, ItemPostReelBinding> by lazy {
        BaseAdapter(
            ItemPostReelBinding::inflate,
            BaseDiffCallBack()
        ) { itemBinding, item ->
            val decision =
                postReelViewModel.nsfwDecisions.value[item.id] ?: NsfwViewDecision.UNDECIDED

            val shouldHideMedia =
                item.nsfw == NsfwStatus.TRUE && decision != NsfwViewDecision.ALLOWED

            val mediaUrl = item.imageUrl
            val province = item.locationName

            bindAvatar(itemBinding.profileIcon, item.user.id)

            itemBinding.nameTV.text = item.user.displayName.ifEmpty { item.user.username }

            itemBinding.captionTV.apply {
                text = item.caption
                isVisible = item.caption.orEmpty().isNotBlank()
            }

            itemBinding.tvLocation.text = province
            itemBinding.locationLayout.isVisible = !province.isNullOrBlank()

            itemBinding.timeTV.text = item.createdAt?.formatDateTime()

            if (item.type == null ) {
                if (activeVideoView === itemBinding.videoCaptured) {
                    releaseVideoPreview()
                }

                itemBinding.videoCaptured.player = null
                itemBinding.videoCaptured.isVisible = false
                itemBinding.imgCaptured.isVisible = !shouldHideMedia

                Glide.with(this)
                    .load(mediaUrl)
                    .into(itemBinding.imgCaptured)
            } else {
                when (item.type) {
                    TypePost.IMAGE -> {
                        if (activeVideoView === itemBinding.videoCaptured) {
                            releaseVideoPreview()
                        }

                        itemBinding.videoCaptured.player = null
                        itemBinding.videoCaptured.isVisible = false
                        itemBinding.imgCaptured.isVisible = !shouldHideMedia

                        Glide.with(this)
                            .load(mediaUrl)
                            .into(itemBinding.imgCaptured)
                    }

                    TypePost.VIDEO -> {
                        Glide.with(this).clear(itemBinding.imgCaptured)
                        itemBinding.imgCaptured.isVisible = false
                        itemBinding.videoCaptured.isVisible = !shouldHideMedia

                        if (shouldHideMedia) {
                            if (activeVideoPostId == item.id) {
                                releaseVideoPreview()
                            } else {
                                itemBinding.videoCaptured.player = null
                            }
                        } else if (isCurrentPost(item) && mediaUrl != null) {
                            playCapturedVideo(itemBinding, mediaUrl.toUri(), item.id)
                        } else if (activeVideoPostId != item.id) {
                            itemBinding.videoCaptured.player = null
                        }
                    }
                }
            }
        }
    }

    private val pageChangeCallback =
        object : ViewPager2.OnPageChangeCallback() {

            override fun onPageSelected(position: Int) {
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
                releaseVideoPreview()
                val itemCount = postReelAdapter.itemCount

                val currentReel = postReelAdapter.currentList
                    .getOrNull(position)
                    ?: return
                val shouldLoadNextPage = itemCount > 0 && position >= itemCount - 3

                if (shouldLoadNextPage) {
                    postReelViewModel.getPosts()
                }

                playCurrentVideo(position)

                if (currentReel.nsfw != NsfwStatus.TRUE) {
                    return
                }

                handleNsfwReel(currentReel, position)
            }
        }

    override fun initData() {
    }

    override fun initView() {
        setupPostReel()
        setupNestedPagerGesture()
        setupKeyboardInsets()

        submitPostReelsAndCheckCurrent()
    }

    override fun initActionView() {
        childFragmentManager.setFragmentResultListener(
            BottomSheetMorePostReelFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val action = bundle.getString(
                BottomSheetMorePostReelFragment.RESULT_ACTION
            )

            val postId = bundle
                .getString(BottomSheetMorePostReelFragment.RESULT_POST_ID)
                ?.let(UUID::fromString)
                ?: return@setFragmentResultListener

            if (action == BottomSheetMorePostReelFragment.ACTION_DELETE_POST) {
                postReelViewModel.deletePost(postId)
            }
        }

        binding.btnCapture.setOnClickListener {
            (parentFragment as? CenterFragment)?.openCamera()
        }

        binding.SendMessageBtn.setOnClickListener {
            setMessageComposerOpen(true)
            binding.bottomLayout.visibility = View.VISIBLE
            binding.sendMessageET.requestFocus()

            val inputMethodManager = requireContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

            inputMethodManager.showSoftInput(
                binding.sendMessageET,
                InputMethodManager.SHOW_IMPLICIT
            )
        }

        binding.btnMore.setOnClickListener {
            val position = binding.postReelViewPager.currentItem

            val currentReel = postReelAdapter.currentList
                .getOrNull(position)
                ?: return@setOnClickListener

            val imageUrl = currentReel.imageUrl ?: return@setOnClickListener
            val isOwner = currentReel.user.id == userSession.getCurrentUser()?.id

            BottomSheetMorePostReelFragment
                .newInstance(currentReel.id, imageUrl, isOwner)
                .show(childFragmentManager, "MorePostReel")
        }

        binding.sendBtn.setOnClickListener {
            if (isSocketConnected) {
                val message = binding.sendMessageET.text.toString().trim()
                val currentPosition = binding.postReelViewPager.currentItem

                val currentReel = postReelAdapter.currentList
                    .getOrNull(currentPosition)
                    ?: return@setOnClickListener

                val conversationId = currentReel.conversationId
                    ?: return@setOnClickListener

                val postImageUrl = currentReel.imageUrl
                    ?: return@setOnClickListener

                postReelViewModel.sendImagePost(conversationId, postImageUrl)
                postReelViewModel.sendMessage(conversationId, message)

                binding.sendMessageET.clearFocus()
                ViewCompat.getWindowInsetsController(binding.root)
                    ?.hide(WindowInsetsCompat.Type.ime())
                binding.bottomLayout.visibility = View.GONE
                setMessageComposerOpen(false)
            }
        }

        binding.btnGoThere.setOnClickListener {
            val currentPosition = binding.postReelViewPager.currentItem

            val currentReel = postReelAdapter.currentList
                .getOrNull(currentPosition)
                ?: return@setOnClickListener

            if (currentReel.latitude == null || currentReel.longitude == null) {
                Toast.makeText(
                    requireContext(),
                    "Người dùng này đã ẩn vị trí của mình",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            openMap(latitude = currentReel.latitude, longitude = currentReel.longitude)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    postReelViewModel.posts.collect { posts ->
                        val firstPostId = posts.firstOrNull()?.id
                        val shouldShowNewPost =
                            firstPostId != null &&
                                firstPostId != lastRenderedFirstPostId

                        lastRenderedFirstPostId = firstPostId
                        postReelAdapter.submitList(posts) {
                            if (shouldShowNewPost) {
                                binding.postReelViewPager.setCurrentItem(0, false)
                            }
                            checkCurrentNsfwReel()
                        }
                    }
                }

                launch {
                    postReelViewModel.connectionState
                        .collect { connectionState ->
                            isSocketConnected =
                                connectionState is SocketConnectionState.Connected
                    }
                }

                launch {
                    avatarViewModel.avatars.collect { avatars ->
                        avatarMap = avatars
                        refreshPostReelAdapter()
                    }
                }

                launch {
                    mainViewModel.nsfwStatuses.collect { statuses ->
                        syncNsfwStatuses(statuses)
                    }
                }
                launch {
                    userSession.currentUser
                        .map { it?.profile }
                        .distinctUntilChanged()
                        .collect { profile ->
                            if (profile != null) {
                                checkCurrentNsfwReel()
                            }
                        }
                }
                launch {
                    postReelViewModel.deletePostState.collect { state ->
                        when (state) {
                            is UiState.Loading -> {
                                binding.btnMore.isEnabled = false
                            }

                            is UiState.Success -> {
                                binding.btnMore.isEnabled = true

                                Toast.makeText(requireContext(), "Đã xóa bài viết", Toast.LENGTH_SHORT).show()

                                postReelViewModel.clearDeletePostState()
                            }

                            is UiState.Error -> {
                                binding.btnMore.isEnabled = true

                                Toast.makeText(requireContext(), state.message,Toast.LENGTH_SHORT).show()

                                postReelViewModel.clearDeletePostState()
                            }

                            UiState.Idle -> Unit
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (!hasLoadedInitialData) {
            hasLoadedInitialData = true

            postReelViewModel.getPosts()
        }

        binding.postReelViewPager.post {
            val posts = postReelViewModel.posts.value
            postReelAdapter.submitList(posts) {
                if (posts.isNotEmpty()) {
                    binding.postReelViewPager.setCurrentItem(0, false)
                    checkCurrentNsfwReel()
                    playCurrentVideo(0)
                } else {
                    releaseVideoPreview()
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
        (parentFragment as? CenterFragment)?.setPostReelMessageComposerOpen(false)
        releaseVideoPreview()
        binding.postReelViewPager.unregisterOnPageChangeCallback(pageChangeCallback)

        super.onDestroyView()
    }

    private fun setupKeyboardInsets() {
        var wasKeyboardVisible = false

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())

            binding.bottomLayout.updatePadding(bottom = imeInsets.bottom)

            val isKeyboardVisible =
                windowInsets.isVisible(WindowInsetsCompat.Type.ime())

            if (wasKeyboardVisible && !isKeyboardVisible) {
                binding.sendMessageET.clearFocus()
                binding.sendMessageET.text?.clear()
                binding.bottomLayout.visibility = View.GONE
                setMessageComposerOpen(false)
            }
            wasKeyboardVisible = isKeyboardVisible

            windowInsets
        }

        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun setMessageComposerOpen(isOpen: Boolean) {
        binding.postReelViewPager.isUserInputEnabled = !isOpen
        (parentFragment as? CenterFragment)?.setPostReelMessageComposerOpen(isOpen)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupNestedPagerGesture() {
        val reelRecyclerView = binding.postReelViewPager.getChildAt(0) as RecyclerView

        val touchSlop = ViewConfiguration.get(requireContext()).scaledTouchSlop

        var startX = 0f
        var startY = 0f

        reelRecyclerView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y

                    // Ban đầu ưu tiên pager Reel.
                    binding.postReelViewPager.parent.requestDisallowInterceptTouchEvent(true)
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.x - startX
                    val deltaY = event.y - startY

                    val isVerticalGesture = abs(deltaY) > abs(deltaX) && abs(deltaY) > touchSlop

                    if (isVerticalGesture) {
                        val isSwipingDown = deltaY > 0
                        val isAtFirstReel = binding.postReelViewPager.currentItem == 0

                        val shouldParentHandle = isSwipingDown && isAtFirstReel

                        binding.postReelViewPager.parent.requestDisallowInterceptTouchEvent(!shouldParentHandle)
                    }
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    binding.postReelViewPager.parent.requestDisallowInterceptTouchEvent(false)
                }
            }

            // Không tự tiêu thụ touch, RecyclerView vẫn xử lý swipe.
            false
        }
    }

    private fun refreshPostReelAdapter() {
        val recyclerView = binding.postReelViewPager.getChildAt(0) as? RecyclerView
            ?: return

        recyclerView.post {
            if (!recyclerView.isAttachedToWindow) return@post

            val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
                ?: return@post
            val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
            val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()

            if (firstVisiblePosition == RecyclerView.NO_POSITION ||
                lastVisiblePosition == RecyclerView.NO_POSITION ||
                postReelAdapter.itemCount == 0
            ) {
                return@post
            }

            val lastPosition = lastVisiblePosition.coerceAtMost(postReelAdapter.itemCount - 1)
            if (firstVisiblePosition <= lastPosition) {
                postReelAdapter.notifyItemRangeChanged(
                    firstVisiblePosition,
                    lastPosition - firstVisiblePosition + 1
                )
            }
        }
    }

    private fun openMap(latitude: Double, longitude: Double) {
        val uri = "geo:$latitude,$longitude?q=$latitude,$longitude".toUri()

        val intent = Intent(Intent.ACTION_VIEW, uri)

        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(requireContext(), "Thiết bị chưa có ứng dụng bản đồ", Toast.LENGTH_SHORT)
                .show()
        }
    }

    override fun onPause() {
        binding.postReelViewPager.setCurrentItem(0, false)
        releaseVideoPreview()
        super.onPause()
    }

    private fun syncNsfwStatuses(statuses: Map<UUID, NsfwStatus>) {
        postReelViewModel.updateNsfwStatuses(statuses)
    }

    private fun getCurrentUserAge(): Int? {
        val birthdayText = userSession.getCurrentUser()?.profile?.birthday ?: return null

        val birthday = runCatching {
            LocalDate.parse(birthdayText)
        }.getOrNull() ?: return null

        val today = LocalDate.now()

        if (birthday.isAfter(today)) {
            return null
        }

        return Period.between(birthday, today).years
    }

    private fun handleNsfwReel(post: PostReelItemModel, position: Int) {
        if (post.nsfw != NsfwStatus.TRUE) return

        val decision = postReelViewModel.nsfwDecisions.value[post.id] ?: NsfwViewDecision.UNDECIDED

        if (decision != NsfwViewDecision.UNDECIDED) {
            return
        }

        if (activeNsfwDialogPostId == post.id) return

        val profile = userSession.getCurrentUser()?.profile ?: return
        val age = getCurrentUserAge()

        if (age == null) {
            showUnverifiedAgeWarning(post.id)
            return
        }

        if (age < 18) {
            showUnderageWarning(post.id, position)
        } else {
            showAdultNsfwWarning(post.id, position)
        }
    }

    private fun showUnderageWarning(id: UUID, position: Int) {
        activeNsfwDialogPostId = id
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Nội dung bị giới hạn")
            .setMessage("Bạn chưa đủ 18 tuổi để xem nội dung này.")
            .setPositiveButton("Tôi đã hiểu") { _, _ ->
                postReelViewModel.updateNsfwDecision(id, NsfwViewDecision.UNDERAGE)
                postReelAdapter.notifyItemChanged(position)
            }
            .setCancelable(false)
            .create()

        dialog.setOnDismissListener {
            if (activeNsfwDialogPostId == id) {
                activeNsfwDialogPostId = null
            }
        }
        dialog.show()
    }

    private fun showAdultNsfwWarning(id: UUID, position: Int) {
        activeNsfwDialogPostId = id
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Nội dung nhạy cảm")
            .setMessage(
                "Ảnh này có thể chứa nội dung NSFW. " +
                        "Bạn có muốn tiếp tục xem không?"
            )
            .setPositiveButton("Tiếp tục xem") { _, _ ->
                postReelViewModel.updateNsfwDecision(id, NsfwViewDecision.ALLOWED)
                postReelAdapter.notifyItemChanged(position)
            }
            .setNegativeButton("Không xem") { _, _ ->
                postReelViewModel.updateNsfwDecision(id, NsfwViewDecision.DENIED)
                postReelAdapter.notifyItemChanged(position)
            }
            .setCancelable(false)
            .create()

        dialog.setOnDismissListener {
            if (activeNsfwDialogPostId == id) {
                activeNsfwDialogPostId = null
            }
        }
        dialog.show()
    }

    private fun showUnverifiedAgeWarning(id: UUID) {
        activeNsfwDialogPostId = id
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Chưa xác minh độ tuổi")
            .setMessage("Bạn cần cập nhật ngày sinh trong hồ sơ trước khi xem nội dung này.")
            .setPositiveButton("Tôi đã hiểu", null)
            .setCancelable(false)
            .create()

        dialog.setOnDismissListener {
            if (activeNsfwDialogPostId == id) {
                activeNsfwDialogPostId = null
            }
        }
        dialog.show()
    }

    private fun submitPostReelsAndCheckCurrent() {
        postReelAdapter.submitList(postReelViewModel.posts.value) {
            checkCurrentNsfwReel()
        }
    }

    private fun checkCurrentNsfwReel() {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return

        val position = binding.postReelViewPager.currentItem
        val currentReel = postReelAdapter.currentList.getOrNull(position) ?: return

        handleNsfwReel(currentReel, position)
    }

    private fun isCurrentPost(item: PostReelItemModel): Boolean {
        return postReelAdapter.currentList
            .getOrNull(binding.postReelViewPager.currentItem)
            ?.id == item.id
    }

    private fun playCurrentVideo(position: Int) {
        val item = postReelAdapter.currentList.getOrNull(position) ?: run {
            releaseVideoPreview()
            return
        }

        if (item.type != TypePost.VIDEO) {
            releaseVideoPreview()
            return
        }

        val decision = postReelViewModel.nsfwDecisions.value[item.id]
            ?: NsfwViewDecision.UNDECIDED
        val shouldHideMedia =
            item.nsfw == NsfwStatus.TRUE && decision != NsfwViewDecision.ALLOWED
        val mediaUrl = item.imageUrl

        if (shouldHideMedia || mediaUrl == null) {
            releaseVideoPreview()
            return
        }

        val recyclerView = binding.postReelViewPager.getChildAt(0) as RecyclerView
        recyclerView.post {
            if (binding.postReelViewPager.currentItem != position) {
                return@post
            }

            val itemView = recyclerView
                .findViewHolderForAdapterPosition(position)
                ?.itemView
                ?: return@post

            playCapturedVideo(
                itemBinding = ItemPostReelBinding.bind(itemView),
                videoUri = mediaUrl.toUri(),
                postId = item.id
            )
        }
    }

    private fun playCapturedVideo(itemBinding: ItemPostReelBinding, videoUri: Uri, postId: UUID) {
        if (activeVideoPostId == postId && videoPreviewPlayer != null) {
            if (activeVideoView !== itemBinding.videoCaptured) {
                activeVideoView?.player = null
                activeVideoView = itemBinding.videoCaptured
                itemBinding.videoCaptured.player = videoPreviewPlayer
            }
            return
        }

        releaseVideoPreview()

        videoPreviewPlayer = ExoPlayer.Builder(requireContext())
            .build()
            .also { player ->
                itemBinding.videoCaptured.player = player
                player.setMediaItem(MediaItem.fromUri(videoUri))
                player.repeatMode = Player.REPEAT_MODE_ONE
                player.prepare()
                player.playWhenReady = true
            }

        activeVideoPostId = postId
        activeVideoView = itemBinding.videoCaptured
    }

    private fun releaseVideoPreview() {
        activeVideoView?.player = null
        videoPreviewPlayer?.release()
        videoPreviewPlayer = null
        activeVideoView = null
        activeVideoPostId = null
    }
}
