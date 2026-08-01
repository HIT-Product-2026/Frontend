package com.pando.app.features.home.ui.chat

import android.util.Log
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.pando.app.core.base.BaseDiffCallBack
import com.pando.app.core.base.BaseFragment
import com.pando.app.core.extensions.loadAvatar
import com.pando.app.core.state.SocketConnectionState
import com.pando.app.core.state.UiState
import com.pando.app.databinding.FragmentChatBinding
import com.pando.app.databinding.ItemImageMessageReceivedBinding
import com.pando.app.databinding.ItemImageMessageSentBinding
import com.pando.app.features.home.data.model.entity.DataChatMessageItem
import com.pando.app.features.shared.AvatarViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.UUID

@AndroidEntryPoint
class ChatFragment : BaseFragment<FragmentChatBinding>(FragmentChatBinding::inflate) {
    companion object {
        private const val TAG = "SOCKET_CONNECTION"
    }

    private var imageMap: Map<UUID, String> = emptyMap()
    private val args: ChatFragmentArgs by navArgs()
    private val chatViewModel: ChatViewModel by viewModels()
    private val avatarViewModel: AvatarViewModel by activityViewModels()
    private var isLoadingOlderMessages = false

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

        chatViewModel.setCurrentConversationId(args.conversationId)
        chatViewModel.setCurrentRecipientId(args.recipientId)

        avatarViewModel.loadAvatar(args.recipientId)
    }

    override fun initView() {
        binding.toolbarName.text = args.name
        setupKeyboardInsets()

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
            chatViewModel.sendMessage(args.conversationId, message)

            binding.sendMessageET.text?.clear()
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
                    chatViewModel.messages.collect { messages ->
                        chatAdapter.submitList(messages) {
                            if (messages.isNotEmpty() && isLoadingOlderMessages == false) {
                                binding.messageList.smoothScrollToPosition(messages.lastIndex)
                            } else if (messages.isNotEmpty() && isLoadingOlderMessages == true) {
                                isLoadingOlderMessages = false
                            }
                        }
                        Log.d("MessageSocket", "Chap nhat thanh cong len man hinh")
                    }
                }
                launch {
                    chatViewModel.uiState.collect { state ->
                        when (state) {
                            is UiState.Idle -> {}
                            is UiState.Loading -> {}
                            is UiState.Success -> {
                                chatViewModel.clearResult()
//                                when (val event = state.data) {
//                                    is ChatEvent.GetChatHistoryEvent -> {
//                                        chatViewModel.clearResult()
//                                    }
////
//                                    is ChatEvent.SendTextEvent -> {
//                                        DataChatMessageItem.data.add(
//                                            ChatMessageItemModel(
//                                                id = event.response.data.id,
//                                                conversationId = args.conversationId,
//                                                senderId = event.response.data.sender.id,
//                                                recipientId = args.recipientId,
//                                                content = event.response.data.content,
//                                                type = MessageType.TEXT,
//                                                createdAt = event.response.data.createdAt
//                                            )
//                                        )
//
//                                        chatAdapter.submitList(DataChatMessageItem.data.toList())
//                                        submitMessagesAndScrollToBottom()
//                                        binding.sendMessageET.text?.clear()
//                                        chatViewModel.clearResult()
//                                    }
//
//                                    is ChatEvent.SocketErrorEvent -> {
//                                        chatViewModel.clearResult()
//                                    }
//                                }
                            }

                            is UiState.Error -> {}
                        }
                    }
                }
                launch {
                    chatViewModel.socketConnectionState.collect { state ->
                        when (state) {
                            SocketConnectionState.Connecting -> {
                                Log.d(TAG, "Đang kết nối")
                            }

                            SocketConnectionState.Connected -> {
                                chatViewModel.subscribeMessage()
                            }

                            SocketConnectionState.Disconnected -> {
                                Log.d(TAG, "Đã ngắt kết nối")
                                chatViewModel.unsubscribeMessage()
                            }

                            is SocketConnectionState.Error -> {
                                Log.e(TAG, state.message)
                                chatViewModel.unsubscribeMessage()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupRecyclerView() {
        val linearLayoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }

        binding.messageList.apply {
            layoutManager = linearLayoutManager
            adapter = chatAdapter

            addOnScrollListener(
                object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(
                        recyclerView: RecyclerView,
                        dx: Int,
                        dy: Int
                    ) {
                        super.onScrolled(recyclerView, dx, dy)

                        // Tọa độ vuốt Oxy
                        // dy >= 0 vuốt theo chiều Oy tăng thì apdapter cũng được kéo theo chiều đó
                        // dy < 0 ngược lại theo chiều Oy giảm
                        // Nên ở đây dùng Oy giảm để kéo hiển thị những message trước đó
                        if (dy >= 0) return

                        val firstVisiblePosition = linearLayoutManager.findFirstVisibleItemPosition()

                        Log.d("Test", "giá trị firstVisiblePosition $firstVisiblePosition")

                        if (firstVisiblePosition <= 2 && isLoadingOlderMessages == false) {
                            Log.d("Test", "Đã thỏa mãn điều kiện để load ")

                            loadOlderMessages()
                        }
                    }
                }
            )
        }
    }

    private fun loadOlderMessages() {
        if (isLoadingOlderMessages) return
        if (DataChatMessageItem.nextCursor?.isBlank() == true) return

        Log.d("Test", "loadOlderMessages đang chạy")

        isLoadingOlderMessages = true

        chatViewModel.getMessageList(
            conversationId = args.conversationId,
            recipientId = args.recipientId
        )
    }

    private fun setupKeyboardInsets() {
        val initialBottomPadding = binding.root.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val imeBottom = windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val navigationBarBottom =
                windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

            view.updatePadding(
                bottom = initialBottomPadding + maxOf(imeBottom, navigationBarBottom)
            )

            val isKeyboardVisible =
                windowInsets.isVisible(WindowInsetsCompat.Type.ime())

            if (!isKeyboardVisible) {
                binding.sendMessageET.clearFocus()
            }

            windowInsets
        }

        ViewCompat.requestApplyInsets(binding.root)
    }


//    private fun submitMessagesAndScrollToBottom(smooth: Boolean = true) {
//        val messages = DataChatMessageItem.data.toList()
//
//        chatAdapter.submitList(messages) {
//            if (messages.isEmpty()) return@submitList
//
//            val lastPosition = messages.lastIndex
//
//            if (smooth) {
//                binding.messageList.smoothScrollToPosition(lastPosition)
//            } else {
//                binding.messageList.scrollToPosition(lastPosition)
//            }
//        }
//    }

    override fun onDestroyView() {
        binding.messageList.adapter = null
        chatViewModel.unsubscribeMessage()
        super.onDestroyView()
    }
}
