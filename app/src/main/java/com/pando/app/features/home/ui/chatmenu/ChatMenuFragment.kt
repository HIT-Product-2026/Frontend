package com.pando.app.features.home.ui.chatmenu

import android.widget.ImageView
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.pando.app.core.base.BaseAdapter
import com.pando.app.core.base.BaseDiffCallBack
import com.pando.app.core.base.BaseFragment
import com.pando.app.core.extensions.loadAvatar
import com.pando.app.core.ui.UiState
import com.pando.app.core.state.UiState
import com.pando.app.databinding.FragmentChatMenuBinding
import com.pando.app.databinding.ItemChatMenuRvBinding
import com.pando.app.features.home.data.model.entity.ChatMenuItemModel
import com.pando.app.features.home.data.model.entity.DataChatMenuItem
import com.pando.app.features.shared.AvatarViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.UUID

@AndroidEntryPoint
class ChatMenuFragment : BaseFragment<FragmentChatMenuBinding>(FragmentChatMenuBinding::inflate) {
    private var avatarMap: Map<UUID, ByteArray> = emptyMap()
    private val avatarViewModel: AvatarViewModel by activityViewModels()
    private val chatMenuViewModel: ChatMenuViewModel by viewModels()
    private val chatMenuAdapter: BaseAdapter<ChatMenuItemModel, ItemChatMenuRvBinding> by lazy {
        BaseAdapter(
            ItemChatMenuRvBinding::inflate,
            BaseDiffCallBack()
        ) { itemBinding, item ->
            itemBinding.tvName.text = item.name.orEmpty()

            bindAvatar(itemBinding.profileIcon, item.recipientId)

            itemBinding.chatPreviewTV.text =
                item.previewChat?.takeIf { it.isNotBlank() } ?: "Hãy bắt đầu cuộc trò chuyện!"

            itemBinding.hourTV.text = item.time?.takeIf { it.isNotBlank() } ?: ""

            itemBinding.chatCard.setOnClickListener {
                val action = ChatMenuFragmentDirections.actionChatMenuFragmentToChatFragment(
                    conversationId = item.conversationId,
                    senderId = item.id,
                    recipientId = item.recipientId,
                    name = item.name.orEmpty()
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
                avatarViewModel.avatars.collect { avatars ->
                    avatarMap = avatars

                    chatMenuAdapter.notifyDataSetChanged()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                chatMenuViewModel.uiState.collect { state ->
                    when (state) {
                        is UiState.Idle -> {}
                        is UiState.Loading -> {}
                        is UiState.Success -> {
                            val data = DataChatMenuItem.data.toList()

                            if (data.isNotEmpty()) {
                                chatMenuAdapter.submitList(data)

                                avatarViewModel.loadAvatars(
                                    data.map { it.id }
                                )
                            } else {
                                chatMenuAdapter.submitList(emptyList())
                            }
                        }

                        is UiState.Error -> {}
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

    private fun bindAvatar(imageView: ImageView, userId: UUID) {
        val avatar = avatarMap[userId]

        imageView.loadAvatar(avatar)

        if (avatar == null) {
            avatarViewModel.loadAvatar(userId)
        }
    }
}