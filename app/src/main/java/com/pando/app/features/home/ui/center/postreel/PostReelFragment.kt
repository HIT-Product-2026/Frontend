package com.pando.app.features.home.ui.center.postreel

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
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
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
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
import com.pando.app.features.home.data.model.entity.DataPostReelItem
import com.pando.app.features.home.data.model.entity.PostReelItemModel
import com.pando.app.features.home.data.model.entity.enumEntity.NsfwStatus
import com.pando.app.features.home.data.model.entity.enumEntity.NsfwViewDecision
import com.pando.app.features.home.ui.center.CenterFragment
import com.pando.app.features.shared.AvatarViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
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
    private var imageMap: Map<UUID, String> = emptyMap()
    private var provinceMap: Map<UUID, String> = emptyMap()
    private val postReelViewModel: PostReelViewModel by viewModels()
    private val avatarViewModel: AvatarViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    @Inject
    lateinit var userSession: UserSession
    private var isSocketConnected = false
    private var hasLoadedInitialData = false
    private var activeNsfwDialogPostId: UUID? = null
    private val postReelAdapter: BaseAdapter<PostReelItemModel, ItemPostReelBinding> by lazy {
        BaseAdapter(
            ItemPostReelBinding::inflate,
            BaseDiffCallBack()
        ) { itemBinding, item ->
            val decision =
                postReelViewModel.nsfwDecisions.value[item.id] ?: NsfwViewDecision.UNDECIDED

            val shouldHideImage =
                item.nsfw == NsfwStatus.TRUE && decision != NsfwViewDecision.ALLOWED
            itemBinding.imgCaptured.isVisible = !shouldHideImage

            val image = imageMap[item.id]
            val province = provinceMap[item.id]

            val shouldLoadProvince =
                item.latitude != null && item.longitude != null && province == null
            if ((image == null || shouldLoadProvince) &&
                lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            ) {
                postReelViewModel.loadPost(
                    postId = item.id,
                    longitude = item.longitude,
                    latitude = item.latitude
                )
            }

            Glide.with(this)
                .load(image)
                .into(itemBinding.imgCaptured)

            bindAvatar(itemBinding.profileIcon, item.user.id)

            itemBinding.nameTV.text = item.user.displayName.ifEmpty { item.user.username }

            itemBinding.captionTV.apply {
                text = item.caption
                isVisible = item.caption.orEmpty().isNotBlank()
            }

            itemBinding.tvLocation.text = province
            itemBinding.locationLayout.isVisible = !province.isNullOrBlank()

            itemBinding.timeTV.text = item.createdAt?.formatDateTime()
        }
    }

    private val pageChangeCallback =
        object : ViewPager2.OnPageChangeCallback() {

            override fun onPageSelected(position: Int) {
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
                val itemCount = postReelAdapter.itemCount

                val currentReel = postReelAdapter.currentList
                    .getOrNull(position)
                    ?: return
                val shouldLoadNextPage = itemCount > 0 && position >= itemCount - 3

                if (shouldLoadNextPage) {
                    postReelViewModel.getPosts()
                }

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

            val imageUrl = imageMap[currentReel.id] ?: return@setOnClickListener
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

                val postImageUrl = imageMap[currentReel.id]
                    ?: return@setOnClickListener

                postReelViewModel.sendImagePost(conversationId, postImageUrl)
                postReelViewModel.sendMessage(conversationId, message)

                binding.sendMessageET.clearFocus()
                ViewCompat.getWindowInsetsController(binding.root)
                    ?.hide(WindowInsetsCompat.Type.ime())
                binding.bottomLayout.visibility = View.GONE
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
                    combine(
                        postReelViewModel.images,
                        postReelViewModel.provinceNames,
                        postReelViewModel.connectionState
                    ) { images, provinces, connectionState ->
                        Triple(images, provinces, connectionState)
                    }.collect { (images, provinces, connectionState) ->
                        imageMap = images
                        provinceMap = provinces

                        refreshPostReelAdapter()

                        when (connectionState) {
                            is SocketConnectionState.Connecting -> {}
                            is SocketConnectionState.Connected -> {
                                isSocketConnected = true
                            }

                            is SocketConnectionState.Disconnected -> {
                                isSocketConnected = false
                            }

                            is SocketConnectionState.Error -> {
                            }
                        }
                    }
                }

                launch {
                    avatarViewModel.avatars.collect { avatars ->
                        avatarMap = avatars
                        refreshPostReelAdapter()
                    }
                }

                launch {
                    postReelViewModel.uiState.collect { state ->
                        when (state) {
                            is UiState.Success -> {
                                syncNsfwStatuses(mainViewModel.nsfwStatuses.value)
                                submitPostReelsAndCheckCurrent()
                            }

                            is UiState.Error -> {}

                            else -> {}
                        }
                    }
                }
                launch {
                    mainViewModel.nsfwStatuses.collect { statuses ->
                        syncNsfwStatuses(statuses)
                        submitPostReelsAndCheckCurrent()
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
                                val postId = state.data

                                DataPostReelItem.data.removeAll {
                                    it.id == postId
                                }

                                DataPostReelItem.total =
                                    DataPostReelItem.total
                                        ?.minus(1)
                                        ?.coerceAtLeast(0)

                                postReelAdapter.submitList(
                                    DataPostReelItem.data.toList()
                                )

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

            DataPostReelItem.reset()
            postReelViewModel.getPosts()
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
        binding.postReelViewPager.unregisterOnPageChangeCallback(pageChangeCallback)

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
        val recyclerView = binding.postReelViewPager.getChildAt(0) as RecyclerView

        recyclerView.post {
            if (recyclerView.isAttachedToWindow) {
                postReelAdapter.notifyDataSetChanged()
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
        super.onPause()
    }

    private fun syncNsfwStatuses(statuses: Map<UUID, NsfwStatus>) {
        DataPostReelItem.data.indices.forEach { index ->
            val currentItem = DataPostReelItem.data[index]
            val latestStatus = statuses[currentItem.id]
                ?: return@forEach

            if (currentItem.nsfw != latestStatus) {
                DataPostReelItem.data[index] =
                    currentItem.copy(nsfw = latestStatus)
            }
        }
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
        postReelAdapter.submitList(DataPostReelItem.data.toList()) {
            checkCurrentNsfwReel()
        }
    }

    private fun checkCurrentNsfwReel() {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return

        val position = binding.postReelViewPager.currentItem
        val currentReel = postReelAdapter.currentList.getOrNull(position) ?: return

        handleNsfwReel(currentReel, position)
    }
}
