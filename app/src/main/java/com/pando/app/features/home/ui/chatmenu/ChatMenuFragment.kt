package com.pando.app.features.home.ui.chatmenu

import android.util.Log
import android.widget.ImageView
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pando.app.core.base.BaseAdapter
import com.pando.app.core.base.BaseDiffCallBack
import com.pando.app.core.base.BaseFragment
import com.pando.app.core.extensions.formatDateTime
import com.pando.app.core.extensions.loadAvatar
import com.pando.app.core.state.SocketConnectionState
import com.pando.app.core.state.UiState
import com.pando.app.databinding.FragmentChatMenuBinding
import com.pando.app.databinding.ItemChatMenuRvBinding
import com.pando.app.features.home.data.model.entity.ChatMenuItemModel
import com.pando.app.features.shared.AvatarViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.UUID

@AndroidEntryPoint
class ChatMenuFragment : BaseFragment<FragmentChatMenuBinding>(FragmentChatMenuBinding::inflate) {
    companion object {
        private const val TAG = "SOCKET_CONNECTION"
    }

    private var avatarMap: Map<UUID, String> = emptyMap()
    private val avatarViewModel: AvatarViewModel by activityViewModels()
    private val chatMenuViewModel: ChatMenuViewModel by viewModels()
    private val chatMenuAdapter: BaseAdapter<ChatMenuItemModel, ItemChatMenuRvBinding> by lazy {
        BaseAdapter(
            ItemChatMenuRvBinding::inflate,
            BaseDiffCallBack()
        ) { itemBinding, item ->
            itemBinding.tvName.text = item.name.orEmpty()

            bindAvatar(itemBinding.profileIcon, item.recipientId, item.avatarUrl)

            itemBinding.chatPreviewTV.text =
                item.previewChat?.takeIf { it.isNotBlank() } ?: "Hãy bắt đầu cuộc trò chuyện!"

            itemBinding.hourTV.text = item.time?.formatDateTime()

            itemBinding.chatCard.setOnClickListener {
                val action = ChatMenuFragmentDirections.actionChatMenuFragmentToChatFragment(
                    conversationId = item.id,
                    senderId = item.senderId,
                    recipientId = item.recipientId,
                    name = item.name.orEmpty(),
                    avatarUrl = item.avatarUrl.orEmpty()
                )
                findNavController().navigate(action)
            }
        }
    }

    override fun initData() {
    }

    override fun initView() {
        setupRecyclerView()

        chatMenuViewModel.getConversations()
    }

    override fun initActionView() {
        binding.backButton.setOnClickListener {
            findNavController().navigateUp()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    avatarViewModel.avatars.collect { avatars ->
                        avatarMap = avatars

                        refreshVisibleItems()
                    }
                }
                launch {
                    chatMenuViewModel.uiState.collect { state ->
                        when (state) {
                            is UiState.Idle -> {}
                            is UiState.Loading -> {}
                            is UiState.Success -> {}
                            is UiState.Error -> {}
                        }
                    }
                }
                launch {
                    chatMenuViewModel.socketConnectionState.collect { state ->
                        when (state) {
                            SocketConnectionState.Connecting -> {}

                            SocketConnectionState.Connected -> {
                                chatMenuViewModel.subscribeConversations()
                            }

                            SocketConnectionState.Disconnected -> {
                                Log.d(TAG, "Đã ngắt kết nối")
                                chatMenuViewModel.unsubscribeConversations()
                            }

                            is SocketConnectionState.Error -> {
                                Log.e(TAG, state.message)
                                chatMenuViewModel.unsubscribeConversations()
                            }
                        }
                    }
                }
                launch {
                    chatMenuViewModel.conversations.collect { conversations ->
                        chatMenuAdapter.submitList(conversations)
                    }
                }
            }
        }
    }

    private fun setupRecyclerView() {
        binding.chatRV.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = chatMenuAdapter
        }
    }

    private fun bindAvatar(imageView: ImageView, userId: UUID, avatarUrl: String?) {
        val avatar = avatarUrl?.takeIf(String::isNotBlank) ?: avatarMap[userId]

        imageView.loadAvatar(avatar)

        if (avatar.isNullOrBlank()) {
            avatarViewModel.loadAvatar(userId)
        }
    }

    private fun refreshVisibleItems() {
        val layoutManager = binding.chatRV.layoutManager as? LinearLayoutManager
            ?: return
        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val lastVisible = layoutManager.findLastVisibleItemPosition()

        if (firstVisible == RecyclerView.NO_POSITION ||
            lastVisible == RecyclerView.NO_POSITION ||
            chatMenuAdapter.itemCount == 0
        ) {
            return
        }

        val lastPosition = lastVisible.coerceAtMost(chatMenuAdapter.itemCount - 1)
        if (firstVisible <= lastPosition) {
            chatMenuAdapter.notifyItemRangeChanged(
                firstVisible,
                lastPosition - firstVisible + 1
            )
        }
    }

    override fun onDestroyView() {
        chatMenuViewModel.unsubscribeConversations()
        super.onDestroyView()
    }
}
