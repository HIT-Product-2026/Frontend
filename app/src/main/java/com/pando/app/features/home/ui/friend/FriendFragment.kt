package com.pando.app.features.home.ui.friend

import android.view.View
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
import com.pando.app.databinding.ItemSearchResultRvBinding
import com.pando.app.features.home.data.model.entity.DataFriendItem
import com.pando.app.features.home.data.model.entity.DataInviteItem
import com.pando.app.features.home.data.model.entity.DataSearchItem
import com.pando.app.features.home.data.model.entity.FriendItemModel
import com.pando.app.features.home.data.model.entity.InviteItemModel
import com.pando.app.features.home.data.model.entity.SearchItemModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.UUID

@AndroidEntryPoint
class FriendFragment : BaseFragment<FragmentFriendBinding>(FragmentFriendBinding::inflate) {
    private val friendViewModel: FriendViewModel by viewModels()
    private val avatarViewModel: AvatarViewModel by viewModels()
    private lateinit var inviteItemAdapter: BaseAdapter<InviteItemModel, ItemInviteRvBinding>
    private lateinit var friendsItemAdapter: BaseAdapter<FriendItemModel, ItemFriendsRvBinding>
    private lateinit var searchItemAdapter: BaseAdapter<SearchItemModel, ItemSearchResultRvBinding>
    private var avatarMap: Map<UUID, ByteArray> = emptyMap()
    private val inviteDiffCallBack = object : DiffUtil.ItemCallback<InviteItemModel>() {
        override fun areItemsTheSame(oldItem: InviteItemModel, newItem: InviteItemModel) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: InviteItemModel, newItem: InviteItemModel) =
            oldItem == newItem
    }

    override fun initData() {
    }

    override fun initView() {
        setupAdapters()
        setupRecyclerViews()

        friendViewModel.getFriendList()

        inviteItemAdapter.submitList(DataInviteItem.data)
        binding.resultLayout.visibility = View.GONE
        binding.sentFriendRequestLayout.visibility = View.GONE
        binding.receivedFriendRequestLayout.visibility = View.GONE
        binding.btnToggleList.visibility = View.GONE
    }

    override fun initActionView() {
        binding.backButton.setOnClickListener {
            findNavController().navigateUp()
        }

        val searchPlate = binding.searchView

        searchPlate.background = null
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
                                    binding.friendNumberText.text = "${DataFriendItem.total} người bạn"

                                    friendsItemAdapter.submitList(
                                        DataFriendItem.data.toList()
                                    )
                                }

                                is FriendResult.SearchState -> {
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
                            }
                        }

                        is UiState.Error -> {
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

            val avatar = avatarMap[item.id]

            Glide.with(itemBinding.root)
                .load(avatar)
                .placeholder(R.drawable.ic_default_avatar)
                .error(R.drawable.ic_default_avatar)
                .circleCrop()
                .into(itemBinding.profileIcon)

            if (avatar == null) {
                avatarViewModel.loadAvatar(item.id)
            }
        }

        searchItemAdapter = BaseAdapter(
            ItemSearchResultRvBinding::inflate,
            BaseDiffCallBack()
        ) { itemBinding, item ->
            itemBinding.tvName.text = item.name

            val avatar = avatarMap[item.id]

            Glide.with(itemBinding.root)
                .load(avatar)
                .placeholder(R.drawable.ic_default_avatar)
                .error(R.drawable.ic_default_avatar)
                .circleCrop()
                .into(itemBinding.profileIcon)

            if (avatar == null) {
                avatarViewModel.loadAvatar(item.id)
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
    }

}