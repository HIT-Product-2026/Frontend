package com.pando.app.features.home.ui.friend

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupWindow
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.pando.app.R
import com.pando.app.core.base.BaseAdapter
import com.pando.app.core.base.BaseDiffCallBack
import com.pando.app.core.base.BaseFragment
import com.pando.app.core.ui.UiState
import com.pando.app.databinding.FragmentFriendBinding
import com.pando.app.databinding.ItemFriendsRvBinding
import com.pando.app.databinding.ItemInviteRvBinding
import com.pando.app.databinding.ItemReceivedFriendRequestRvBinding
import com.pando.app.databinding.ItemSearchResultRvBinding
import com.pando.app.databinding.ItemSentFriendRequestRvBinding
import com.pando.app.databinding.PopupFriendActionBinding
import com.pando.app.features.home.data.model.entity.DataFriendItem
import com.pando.app.features.home.data.model.entity.DataInviteItem
import com.pando.app.features.home.data.model.entity.DataReceivedRequestItem
import com.pando.app.features.home.data.model.entity.DataSearchItem
import com.pando.app.features.home.data.model.entity.DataSentRequestItem
import com.pando.app.features.home.data.model.entity.FriendItemModel
import com.pando.app.features.home.data.model.entity.InviteItemModel
import com.pando.app.features.home.data.model.entity.ReceivedRequestItemModel
import com.pando.app.features.home.data.model.entity.SearchItemModel
import com.pando.app.features.home.data.model.entity.SentRequestItemModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.UUID
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.activityViewModels
import androidx.viewbinding.ViewBinding
import com.pando.app.core.extensions.loadAvatar
import com.pando.app.features.shared.AvatarViewModel

@AndroidEntryPoint
class FriendFragment : BaseFragment<FragmentFriendBinding>(FragmentFriendBinding::inflate) {
    private var avatarMap: Map<UUID, ByteArray> = emptyMap()

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

        inviteItemAdapter.submitList(DataInviteItem.data)
    }

    override fun initActionView() {
        binding.backButton.setOnClickListener {
            findNavController().navigateUp()
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

                    friendsItemAdapter.notifyDataSetChanged()
                    searchItemAdapter.notifyDataSetChanged()
                    inviteItemAdapter.notifyDataSetChanged()
                    receivedRequestedAdapter.notifyDataSetChanged()
                    sentRequestedAdapter.notifyDataSetChanged()
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
                                is FriendResult.FriendListSuccess -> {
                                    updateFriendRV()
                                }

                                is FriendResult.SentRequestedUsersSuccess -> {
                                    updateSentRV()
                                }

                                is FriendResult.ReceivedRequestedUsersSuccess -> {
                                    updateReceivedRV()
                                }

                                is FriendResult.SearchState -> {
                                    updateSearchRV()
                                }

                                is FriendResult.UnfriendSuccess -> {
                                    val result = state.data.response.data
                                    val friend = DataFriendItem.data.firstOrNull { item ->
                                        result.receiver.id == item.id
                                    }

                                    if (friend != null) {
                                        DataFriendItem.data.remove(friend)
                                        DataFriendItem.total = DataFriendItem.total?.minus(1)
                                        updateFriendRV()
                                    }
                                }
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
                            val requested = event.response.data
                            val searchUser = DataSearchItem.data.firstOrNull { item ->
                                requested.receiver.id == item.id
                            }
                            binding.searchView.clearFocus()

                            if (searchUser != null) {
                                DataSearchItem.data.remove(searchUser)
                                DataSearchItem.total = DataSearchItem.total?.minus(1)

                                DataSentRequestItem.data.add(
                                    SentRequestItemModel(
                                        searchUser.id,
                                        searchUser.name,
                                        requested.id
                                    )
                                )

                                updateSearchRV()
                                updateSentRV()
                            }
                        }

                        is FriendEvent.RejectFriendSuccess -> {
                            val result = event.response.data
                            val received = DataReceivedRequestItem.data.firstOrNull { item ->
                                result.requester.id == item.id
                            }
                            val requested = DataSentRequestItem.data.firstOrNull { item ->
                                result.receiver.id == item.id
                            }

                            if (received != null) {
                                DataReceivedRequestItem.data.remove(received)
                                DataReceivedRequestItem.total = DataReceivedRequestItem.total?.minus(1)

                                updateReceivedRV()
                            } else if (requested != null) {
                                DataSentRequestItem.data.remove(requested)
                                DataSentRequestItem.total = DataSentRequestItem.total?.minus(1)

                                updateSentRV()
                            }
                        }

                        is FriendEvent.AcceptFriendSuccess -> {
                            val result = event.response.data
                            val requested = DataReceivedRequestItem.data.firstOrNull { item ->
                                result.requester.id == item.id
                            }

                            if (requested != null) {
                                DataReceivedRequestItem.data.remove(requested)
                                DataReceivedRequestItem.total = DataReceivedRequestItem.total?.minus(1)

                                DataFriendItem.data.add(
                                    FriendItemModel(
                                        requested.id,
                                        requested.name
                                    )
                                )

                                updateReceivedRV()
                                updateFriendRV()
                            }
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

    fun updateSentRV() {
        val dataItems = DataSentRequestItem.data.toList()

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

    fun updateReceivedRV() {
        val dataItems = DataReceivedRequestItem.data.toList()

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

    fun updateSearchRV() {
        val searchItems = DataSearchItem.data.toList()

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

    fun updateFriendRV() {
        val dataItems = DataFriendItem.data.toList()

        if (dataItems.isNotEmpty()) {
            binding.friendNumberText.visibility = View.VISIBLE
            binding.friendsLayout.visibility = View.VISIBLE
            binding.friendNumberText.text =
                "${DataFriendItem.total} người bạn"

            friendsItemAdapter.submitList(dataItems)

            avatarViewModel.loadAvatars(
                dataItems.map { it.id }
            )
        } else {
            binding.friendNumberText.visibility = View.GONE
            binding.friendsLayout.visibility = View.GONE
            friendsItemAdapter.submitList(emptyList())
        }
    }

    private fun updateSearchActionStates(states: Map<UUID, FriendActionState>) {
        val updatedItems = DataSearchItem.data.map { item ->
            val state = states[item.id]

            item.copy(
                isLoading = state?.isLoading == true && state.action == FriendAction.REQUEST,
                errorMessage = state?.errorMessage
            )
        }

        searchItemAdapter.submitList(updatedItems)
    }

    private fun updateSentActionStates(states: Map<UUID, FriendActionState>) {
        val updatedItems = DataSentRequestItem.data.map { item ->
            val state = states[item.friendshipId]

            item.copy(
                isLoading = state?.isLoading == true,
                errorMessage = state?.errorMessage
            )
        }

        sentRequestedAdapter.submitList(updatedItems)
    }

    private fun updateReceivedActionStates(states: Map<UUID, FriendActionState>) {
        val updatedItems = DataReceivedRequestItem.data.map { item ->
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