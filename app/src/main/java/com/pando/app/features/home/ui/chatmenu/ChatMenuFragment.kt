package com.pando.app.features.home.ui.chatmenu

import android.widget.ImageView
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.pando.app.R
import com.pando.app.core.base.BaseAdapter
import com.pando.app.core.base.BaseDiffCallBack
import com.pando.app.core.base.BaseFragment
import com.pando.app.core.extensions.loadAvatar
import com.pando.app.databinding.FragmentChatMenuBinding
import com.pando.app.databinding.ItemChatMenuRvBinding
import com.pando.app.features.home.data.model.entity.ChatMenuItemModel
import com.pando.app.features.shared.AvatarViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.UUID

@AndroidEntryPoint
class ChatMenuFragment : BaseFragment<FragmentChatMenuBinding>(FragmentChatMenuBinding::inflate) {
    private var avatarMap: Map<UUID, ByteArray> = emptyMap()
    private val avatarViewModel: AvatarViewModel by activityViewModels()
    private val chatMenuViewModel: ChatMenuViewModel by viewModels()
    private val chatMenuAdapter : BaseAdapter<ChatMenuItemModel, ItemChatMenuRvBinding> by lazy {
        BaseAdapter(
            ItemChatMenuRvBinding::inflate,
            BaseDiffCallBack()
        ) { itemBinding, item ->
            itemBinding.tvName.text = item.name

            bindAvatar(itemBinding.profileIcon, item.id)

            itemBinding.chatPreviewTV.text = item.previewChat

            itemBinding.hourTV.text = item.time
        }
    }

    override fun initData() {
    }

    override fun initView() {
        setupRecyclerView()

        chatMenuViewModel.getConversations()
    }

    override fun initActionView() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                avatarViewModel.avatars.collect { avatars ->
                    avatarMap = avatars

                    chatMenuAdapter.notifyDataSetChanged()
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