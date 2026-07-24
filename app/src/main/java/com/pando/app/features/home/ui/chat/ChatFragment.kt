package com.pando.app.features.home.ui.chat

import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.pando.app.core.base.BaseDiffCallBack
import com.pando.app.core.base.BaseFragment
import com.pando.app.core.extensions.loadAvatar
import com.pando.app.core.session.UserSession
import com.pando.app.core.ui.UiState
import com.pando.app.core.state.UiState
import com.pando.app.databinding.FragmentChatBinding
import com.pando.app.databinding.ItemImageMessageReceivedBinding
import com.pando.app.databinding.ItemImageMessageSentBinding
import com.pando.app.features.home.data.model.entity.ChatMessageItemModel
import com.pando.app.features.home.data.model.entity.DataChatMessageItem
import com.pando.app.features.home.data.model.entity.enumEntity.MessageType
import com.pando.app.features.shared.AvatarViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class ChatFragment : BaseFragment<FragmentChatBinding>(FragmentChatBinding::inflate) {
    private var imageMap: Map<UUID, ByteArray> = emptyMap()
    private val args: ChatFragmentArgs by navArgs()
    private val chatViewModel: ChatViewModel by viewModels()
    private val avatarViewModel: AvatarViewModel by activityViewModels()

//    @Inject
//    lateinit var userSession: UserSession
    private val chatAdapter: ChatAdapter by lazy {
        ChatAdapter(
            args.senderId,
            BaseDiffCallBack()
        ) { itemBinding, item ->
            when (itemBinding) {
                is ItemImageMessageReceivedBinding -> {
                    val image = imageMap[item.id]

                    Glide.with(this)
                        .load(image)
                        .into(itemBinding.imageMessage)

                    if (image == null) {
                        chatViewModel.loadImageMessage(item.id)
                    }
                }

                is ItemImageMessageSentBinding -> {
                    val image = imageMap[item.id]

                    Glide.with(this)
                        .load(image)
                        .into(itemBinding.imageMessage)

                    if (image == null) {
                        chatViewModel.loadImageMessage(item.id)
                    }
                }
            }
        }
    }

    override fun initData() {
        DataChatMessageItem.reset()
        avatarViewModel.loadAvatar(args.recipientId)
    }

    override fun initView() {
        binding.toolbarName.text = args.name

        if (DataChatMessageItem.data.isEmpty()) {
            chatViewModel.getMessageList(args.conversationId, args.recipientId)
        }

        setupRecyclerView()
        chatAdapter.submitList(DataChatMessageItem.data.toList())
    }

    override fun initActionView() {
        binding.toolBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.sendBtn.setOnClickListener {
            val message = binding.sendMessageET.text?.toString()?.trim().orEmpty()
            if (message.isBlank()) return@setOnClickListener

            chatViewModel.sendTextMessage(args.conversationId, message)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    avatarViewModel.avatars.collect { avatars ->
                        avatars[args.recipientId]?.let { avatar ->
                            chatAdapter.updateRecipientAvatar(avatar)
                            binding.toolBarAvatar.loadAvatar(avatar)
                        }
                    }
                }
                launch {
                    chatViewModel.images.collect { images ->
                        imageMap = images
                        chatAdapter.notifyDataSetChanged()
                    }
                }
                launch {
                    chatViewModel.uiState.collect { state ->
                        when (state) {
                            is UiState.Idle -> {}
                            is UiState.Loading -> {}
                            is UiState.Success -> {
                                when (val event = state.data) {
                                    is ChatEvent.GetChatHistoryEvent -> {
                                        submitMessagesAndScrollToBottom()
                                    }

                                    is ChatEvent.SendTextEvent -> {
                                        DataChatMessageItem.data.add(ChatMessageItemModel(
                                                id = event.response.data.id,
                                                conversationId = args.conversationId,
                                                senderId = event.response.data.sender.id,
                                                recipientId = args.recipientId,
                                                content = event.response.data.content,
                                                type = MessageType.TEXT,
                                                createdAt = event.response.data.createdAt
                                            )
                                        )

                                        chatAdapter.submitList(DataChatMessageItem.data.toList())
                                        submitMessagesAndScrollToBottom()
                                        binding.sendMessageET.text?.clear()
                                        chatViewModel.clearResult()
                                    }
                                }
                            }

                            is UiState.Error -> {}
                        }
                    }
                }
            }
        }
    }

    private fun setupRecyclerView() {
        binding.messageList.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
        }
    }

    private fun submitMessagesAndScrollToBottom(smooth: Boolean = true) {
        val messages = DataChatMessageItem.data.toList()

        chatAdapter.submitList(messages) {
            if (messages.isEmpty()) return@submitList

            val lastPosition = messages.lastIndex

            if (smooth) {
                binding.messageList.smoothScrollToPosition(lastPosition)
            } else {
                binding.messageList.scrollToPosition(lastPosition)
            }
        }
    }
}