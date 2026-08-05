package com.pando.app.features.home.ui.friend

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupWindow
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pando.app.core.base.BaseAdapter
import com.pando.app.core.base.BaseDiffCallBack
import com.pando.app.core.base.BaseFragment
import com.pando.app.core.state.UiState
import com.pando.app.databinding.FragmentFriendBinding
import com.pando.app.databinding.ItemFriendsRvBinding
import com.pando.app.databinding.ItemInviteRvBinding
import com.pando.app.databinding.ItemReceivedFriendRequestRvBinding
import com.pando.app.databinding.ItemSearchResultRvBinding
import com.pando.app.databinding.ItemSentFriendRequestRvBinding
import com.pando.app.databinding.PopupFriendActionBinding
import com.pando.app.features.home.data.model.entity.FriendItemModel
import com.pando.app.features.home.data.model.entity.InviteItemModel
import com.pando.app.features.home.data.model.entity.ReceivedRequestItemModel
import com.pando.app.features.home.data.model.entity.SearchItemModel
import com.pando.app.features.home.data.model.entity.SentRequestItemModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.UUID
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.activityViewModels
import androidx.viewbinding.ViewBinding
import com.pando.app.R
import com.pando.app.core.extensions.loadAvatar
import com.pando.app.core.extensions.showComingSoon
import com.pando.app.core.extensions.showShortToast
import com.pando.app.features.shared.AvatarViewModel

@AndroidEntryPoint
class FriendFragment : BaseFragment<FragmentFriendBinding>(FragmentFriendBinding::inflate) {
    private companion object {
        const val COLLAPSED_FRIEND_COUNT = 3
    }

    private val inviteItems = listOf(
        InviteItemModel("facebook_ic", "Facebook", R.drawable.facebook),
        InviteItemModel("messenger_ic", "Messenger", R.drawable.messenger),
        InviteItemModel("instagram_ic", "Instagram", R.drawable.instagram),
        InviteItemModel("message_ic", "Message", R.drawable.message_ic),
        InviteItemModel("more_ic", "More", R.drawable.more)
    )

    private var avatarMap: Map<UUID, String> = emptyMap()
    private var isFriendListExpanded = false

    //View Model
    private val friendViewModel: FriendViewModel by viewModels()
    private val avatarViewModel: AvatarViewModel by activityViewModels()

    //Adapter
    private lateinit var inviteItemAdapter: BaseAdapter<InviteItemModel, ItemInviteRvBinding>
    private lateinit var friendsItemAdapter: BaseAdapter<FriendItemModel, ItemFriendsRvBinding>
    private lateinit var searchItemAdapter: BaseAdapter<SearchItemModel, ItemSearchResultRvBinding>
    private lateinit var receivedRequestedAdapter: BaseAdapter<ReceivedRequestItemModel, ItemReceivedFriendRequestRvBinding>
    private lateinit var sentRequestedAdapter: BaseAdapter<SentRequestItemModel, ItemSentFriendRequestRvBinding>

    private val inviteDiffCallBack = object : DiffUtil.ItemCallback<InviteItemModel>() {
        override fun areItemsTheSame(oldItem: InviteItemModel, newItem: InviteItemModel) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: InviteItemModel, newItem: InviteItemModel) =
            oldItem == newItem
    }

    //Popup Window FriendItem
    private var friendPopupWindow: PopupWindow? = null

    override fun initData() {
    }

    override fun initView() {
        setupAdapters()
        setupRecyclerViews()

        friendViewModel.getFriendList()
        friendViewModel.getSentRequestedUsers()
        friendViewModel.getReceivedRequestedUsers()

        binding.friendNumberText.visibility = View.GONE
        binding.friendsLayout.visibility = View.GONE
        binding.resultLayout.visibility = View.GONE
        binding.sentFriendRequestLayout.visibility = View.GONE
        binding.receivedFriendRequestLayout.visibility = View.GONE
        binding.btnToggleList.visibility = View.GONE

        inviteItemAdapter.submitList(inviteItems)
    }

    override fun initActionView() {
        binding.backButton.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnToggleList.setOnClickListener {
            isFriendListExpanded = !isFriendListExpanded
            updateFriendRV(
                friendViewModel.friends.value,
                friendViewModel.friendTotal.value
            )
        }

        binding.searchView.queryHint = "Nhập email hoặc username của bạn bè"
        binding.searchView.setIconifiedByDefault(false)
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(newText: String?): Boolean {
                val keyword = newText.orEmpty()

                if (keyword.isBlank()) {
                    binding.resultLayout.visibility = View.GONE
                    searchItemAdapter.submitList(emptyList())
                }

                friendViewModel.onSearchQueryChanged(keyword)
                return false
            }

            override fun onQueryTextSubmit(query: String?): Boolean {
                friendViewModel.searchAgain(query.orEmpty())
                return false
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                avatarViewModel.avatars.collect { avatars ->
                    avatarMap = avatars

                    refreshVisibleItems(binding.friendsRV)
                    refreshVisibleItems(binding.resultRV)
                    refreshVisibleItems(binding.receivedFriendRequestRV)
                    refreshVisibleItems(binding.sentFriendRequestRV)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                friendViewModel.uiState.collect { state ->
                    when (state) {
                        is UiState.Idle -> {}
                        is UiState.Loading -> {}

                        is UiState.Success -> {
                            when (state.data) {
                                is FriendResult.UnfriendSuccess -> {
                                    requireContext().showShortToast(R.string.friend_removed)
                                }

                                is FriendResult.FriendListSuccess,
                                is FriendResult.SentRequestedUsersSuccess,
                                is FriendResult.ReceivedRequestedUsersSuccess,
                                is FriendResult.SearchState -> Unit
                            }
                        }

                        is UiState.Error -> {

                        }
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(
                        friendViewModel.friends,
                        friendViewModel.friendTotal
                    ) { friends, total -> friends to total }
                        .collect { (friends, total) ->
                            updateFriendRV(friends, total)
                        }
                }

                launch {
                    friendViewModel.searchResults.collect { results ->
                        updateSearchRV(results)
                    }
                }

                launch {
                    friendViewModel.sentRequests.collect { requests ->
                        updateSentRV(requests)
                    }
                }

                launch {
                    friendViewModel.receivedRequests.collect { requests ->
                        updateReceivedRV(requests)
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                friendViewModel.actionStates.collect { states ->
                    updateSearchActionStates(states)
                    updateSentActionStates(states)
                    updateReceivedActionStates(states)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                friendViewModel.friendEvent.collect { event ->
                    when (event) {
                        is FriendEvent.RequestFriendSuccess -> {
                            requireContext().showShortToast(R.string.friend_request_sent)
                            binding.searchView.clearFocus()
                        }

                        is FriendEvent.RejectFriendSuccess -> {
                            requireContext().showShortToast(
                                if (event.wasOutgoing) {
                                    R.string.friend_request_cancelled
                                } else {
                                    R.string.friend_request_rejected
                                }
                            )
                        }

                        is FriendEvent.AcceptFriendSuccess -> {
                            requireContext().showShortToast(R.string.friend_request_accepted)
                        }
                    }
                }
            }
        }
    }

    private fun setupAdapters() {
        inviteItemAdapter = BaseAdapter(
            ItemInviteRvBinding::inflate,
            inviteDiffCallBack
        ) { itemBinding, item ->
            itemBinding.tvName.text = item.name
            itemBinding.appIcon.setImageResource(item.icon)
            itemBinding.inviteCard.setOnClickListener {
                requireContext().showComingSoon()
            }
        }

        friendsItemAdapter = BaseAdapter(
            ItemFriendsRvBinding::inflate,
            BaseDiffCallBack()
        ) { itemBinding, item ->
            itemBinding.tvName.text = item.name

            bindAvatar(itemBinding.profileIcon, item.id)

            itemBinding.functionBtn.setOnClickListener {
                showFriendActions(itemBinding, item)
            }
        }

        searchItemAdapter = BaseAdapter(
            ItemSearchResultRvBinding::inflate,
            BaseDiffCallBack()
        ) { itemBinding, item ->
            itemBinding.tvName.text = item.name

            bindAvatar(itemBinding.profileIcon, item.id)

            itemBinding.addFriendBtn.setOnClickListener {
                friendViewModel.requestFriend(item.id)
            }
        }

        sentRequestedAdapter = BaseAdapter(
            ItemSentFriendRequestRvBinding::inflate,
            BaseDiffCallBack()
        ) { itemBinding, item ->
            itemBinding.tvName.text = item.name

            bindAvatar(itemBinding.profileIcon, item.id)

            itemBinding.cancelBtn.setOnClickListener {
                friendViewModel.rejectFriend(item.friendshipId)
            }
        }

        receivedRequestedAdapter = BaseAdapter(
            ItemReceivedFriendRequestRvBinding::inflate,
            BaseDiffCallBack()
        ) { itemBinding, item ->
            itemBinding.tvName.text = item.name

            bindAvatar(itemBinding.profileIcon, item.id)

            itemBinding.cancelBtn.setOnClickListener {
                friendViewModel.rejectFriend(item.friendshipId)
            }

            itemBinding.acceptBtn.setOnClickListener {
                friendViewModel.acceptFriend(item.friendshipId)
            }
        }
    }

    private fun setupRecyclerViews() {
        binding.inviteRV.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = inviteItemAdapter
        }

        binding.friendsRV.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = friendsItemAdapter
        }

        binding.resultRV.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = searchItemAdapter
        }

        binding.sentFriendRequestRV.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = sentRequestedAdapter
        }

        binding.receivedFriendRequestRV.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = receivedRequestedAdapter
        }
    }

    private fun refreshVisibleItems(recyclerView: RecyclerView) {
        val adapter = recyclerView.adapter ?: return
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
            ?: return
        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val lastVisible = layoutManager.findLastVisibleItemPosition()

        if (firstVisible == RecyclerView.NO_POSITION ||
            lastVisible == RecyclerView.NO_POSITION ||
            adapter.itemCount == 0
        ) {
            return
        }

        val lastPosition = lastVisible.coerceAtMost(adapter.itemCount - 1)
        if (firstVisible <= lastPosition) {
            adapter.notifyItemRangeChanged(
                firstVisible,
                lastPosition - firstVisible + 1
            )
        }
    }

    private fun updateSentRV(dataItems: List<SentRequestItemModel>) {
        if (dataItems.isNotEmpty()) {
            binding.sentFriendRequestLayout.visibility = View.VISIBLE

            sentRequestedAdapter.submitList(dataItems)

            avatarViewModel.loadAvatars(
                dataItems.map { it.id }
            )
        } else {
            binding.sentFriendRequestLayout.visibility = View.GONE
            sentRequestedAdapter.submitList(emptyList())
        }
    }

    private fun updateReceivedRV(dataItems: List<ReceivedRequestItemModel>) {
        if (dataItems.isNotEmpty()) {
            binding.receivedFriendRequestLayout.visibility =
                View.VISIBLE

            receivedRequestedAdapter.submitList(dataItems)

            avatarViewModel.loadAvatars(
                dataItems.map { it.id }
            )
        } else {
            binding.receivedFriendRequestLayout.visibility = View.GONE
            receivedRequestedAdapter.submitList(emptyList())
        }
    }

    private fun updateSearchRV(searchItems: List<SearchItemModel>) {
        if (searchItems.isNotEmpty()) {
            binding.resultLayout.visibility = View.VISIBLE

            searchItemAdapter.submitList(searchItems)

            avatarViewModel.loadAvatars(
                searchItems.map { it.id }
            )
        } else {
            binding.resultLayout.visibility = View.GONE
            searchItemAdapter.submitList(emptyList())
        }
    }

    private fun updateFriendRV(
        dataItems: List<FriendItemModel>,
        total: Int
    ) {
        if (dataItems.isNotEmpty()) {
            binding.friendNumberText.visibility = View.VISIBLE
            binding.friendsLayout.visibility = View.VISIBLE
            binding.btnToggleList.visibility =
                if (dataItems.size > COLLAPSED_FRIEND_COUNT) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            binding.btnToggleList.text =
                if (isFriendListExpanded) "Thu gọn" else "Xem thêm"
            binding.btnToggleList.setCompoundDrawablesRelativeWithIntrinsicBounds(
                null,
                null,
                ContextCompat.getDrawable(
                    requireContext(),
                    if (isFriendListExpanded) {
                        R.drawable.outline_arrow_drop_up_24
                    } else {
                        R.drawable.outline_arrow_drop_down_24
                    }
                ),
                null
            )
            binding.friendNumberText.text =
                "$total người bạn"

            val displayedItems = if (isFriendListExpanded) {
                dataItems
            } else {
                dataItems.take(COLLAPSED_FRIEND_COUNT)
            }

            friendsItemAdapter.submitList(displayedItems)

            avatarViewModel.loadAvatars(
                displayedItems.map { it.id }
            )
        } else {
            binding.friendNumberText.visibility = View.GONE
            binding.friendsLayout.visibility = View.GONE
            binding.btnToggleList.visibility = View.GONE
            friendsItemAdapter.submitList(emptyList())
        }
    }

    private fun updateSearchActionStates(states: Map<UUID, FriendActionState>) {
        val updatedItems = friendViewModel.searchResults.value.map { item ->
            val state = states[item.id]

            item.copy(
                isLoading = state?.isLoading == true && state.action == FriendAction.REQUEST,
                errorMessage = state?.errorMessage
            )
        }

        searchItemAdapter.submitList(updatedItems)
    }

    private fun updateSentActionStates(states: Map<UUID, FriendActionState>) {
        val updatedItems = friendViewModel.sentRequests.value.map { item ->
            val state = states[item.friendshipId]

            item.copy(
                isLoading = state?.isLoading == true,
                errorMessage = state?.errorMessage
            )
        }

        sentRequestedAdapter.submitList(updatedItems)
    }

    private fun updateReceivedActionStates(states: Map<UUID, FriendActionState>) {
        val updatedItems = friendViewModel.receivedRequests.value.map { item ->
            val state = states[item.friendshipId]

            item.copy(
                loadingAction = if (state?.isLoading == true) state.action else null,
                errorMessage = state?.errorMessage
            )
        }

        receivedRequestedAdapter.submitList(updatedItems)
    }

    private fun showFriendActions(view: ViewBinding, friend: FriendItemModel) {
        if (friendPopupWindow?.isShowing == true) {
            friendPopupWindow?.dismiss()
            return
        }

        val popupBinding = PopupFriendActionBinding.inflate(layoutInflater)

        val popupWindow = PopupWindow(
            popupBinding.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true

            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

            setOnDismissListener { friendPopupWindow = null }
        }

        friendPopupWindow = popupWindow

        popupBinding.btnRemoveFriend.setOnClickListener {
            popupWindow.dismiss()

            friendViewModel.unfriend(friend.id)
        }

        popupBinding.btnBlockFriend.setOnClickListener {
            popupWindow.dismiss()
            requireContext().showComingSoon()
        }

        popupBinding.root.measure(
            View.MeasureSpec.UNSPECIFIED,
            View.MeasureSpec.UNSPECIFIED
        )

        val popupWidth = popupBinding.root.measuredWidth

        val xOffset = view.root.width - popupWidth

        popupWindow.showAsDropDown(view.root, xOffset, -20)
    }

    private fun bindAvatar(imageView: ImageView, userId: UUID) {
        val avatar = avatarMap[userId]

        imageView.loadAvatar(avatar)

        if (avatar == null) {
            avatarViewModel.loadAvatar(userId)
        }
    }
}
