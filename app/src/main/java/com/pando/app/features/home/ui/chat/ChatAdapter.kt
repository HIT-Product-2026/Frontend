package com.pando.app.features.home.ui.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.pando.app.core.base.BaseDiffCallBack
import com.pando.app.core.extensions.formatDateTime
import com.pando.app.core.extensions.loadAvatar
import com.pando.app.databinding.ItemImageMessageReceivedBinding
import com.pando.app.databinding.ItemImageMessageSentBinding
import com.pando.app.databinding.ItemMessageReceivedBinding
import com.pando.app.databinding.ItemMessageSentBinding
import com.pando.app.features.home.data.model.entity.ChatMessageItemModel
import com.pando.app.features.home.data.model.entity.enumEntity.MessageType
import java.util.UUID

class ChatAdapter(
    private val currentUserId: UUID?,
    diffCallBack: BaseDiffCallBack<ChatMessageItemModel>,
    private val onBind: (ViewBinding, ChatMessageItemModel) -> Unit
) : ListAdapter<ChatMessageItemModel, RecyclerView.ViewHolder>(diffCallBack) {
    companion object {
        private const val VIEW_TYPE_TEXT_SENT = 1
        private const val VIEW_TYPE_TEXT_RECEIVED = 2
        private const val VIEW_TYPE_IMAGE_SENT = 3
        private const val VIEW_TYPE_IMAGE_RECEIVED = 4
    }

    private var recipientAvatar: ByteArray? = null

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        val isMine = item.senderId == currentUserId

        return when (item.type) {
            MessageType.TEXT if isMine -> {
                VIEW_TYPE_TEXT_SENT
            }

            MessageType.TEXT -> {
                VIEW_TYPE_TEXT_RECEIVED
            }

            MessageType.IMAGE if isMine -> {
                VIEW_TYPE_IMAGE_SENT
            }

            else -> {
                VIEW_TYPE_IMAGE_RECEIVED
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {
        val view = LayoutInflater
            .from(parent.context)

        return when (viewType) {
            VIEW_TYPE_TEXT_SENT -> {
                val binding = ItemMessageSentBinding.inflate(
                    view,
                    parent,
                    false
                )

                TextSentViewHolder(binding)
            }

            VIEW_TYPE_TEXT_RECEIVED -> {
                val binding = ItemMessageReceivedBinding.inflate(
                    view,
                    parent,
                    false
                )

                TextReceivedViewHolder(binding)
            }

            VIEW_TYPE_IMAGE_SENT -> {
                val binding = ItemImageMessageSentBinding.inflate(
                    view,
                    parent,
                    false
                )

                ImageSentViewHolder(binding)
            }

            else -> {
                val binding = ItemImageMessageReceivedBinding.inflate(
                    view,
                    parent,
                    false
                )

                ImageReceivedViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int
    ) {
        when (holder) {
            is TextSentViewHolder -> {
                holder.bind(getItem(position))
            }

            is TextReceivedViewHolder -> {
                holder.bind(getItem(position))
            }

            is ImageSentViewHolder -> {
                holder.bind(getItem(position))
            }

            is ImageReceivedViewHolder -> {
                holder.bind(getItem(position))
            }
        }
    }

    inner class TextSentViewHolder(private val binding: ItemMessageSentBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ChatMessageItemModel) {
            binding.tvMessage.text = item.content

            binding.tvTime.text = item.createdAt.formatDateTime()
        }
    }

    inner class TextReceivedViewHolder(private val binding: ItemMessageReceivedBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ChatMessageItemModel) {
            binding.tvMessage.text = item.content

            binding.imgAvatar.loadAvatar(recipientAvatar)

            binding.tvTime.text = item.createdAt.formatDateTime()
        }
    }

    inner class ImageSentViewHolder(private val binding: ItemImageMessageSentBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ChatMessageItemModel) {
            onBind(binding, item)
        }
    }

    inner class ImageReceivedViewHolder(private val binding: ItemImageMessageReceivedBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ChatMessageItemModel) {
            binding.imgAvatar.loadAvatar(recipientAvatar)

            onBind(binding, item)
        }
    }

    fun updateRecipientAvatar(avatar: ByteArray) {
        if (recipientAvatar === avatar) return

        recipientAvatar = avatar
        notifyDataSetChanged()
    }
}