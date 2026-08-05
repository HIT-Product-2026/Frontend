package com.pando.app.core.base

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.pando.app.core.extensions.applyButtonPressFeedbackRecursively

open class BaseAdapter<T : Any, B : ViewBinding>(
    private val inflateMethod: (LayoutInflater, ViewGroup, Boolean) -> B,
    diffCallback: DiffUtil.ItemCallback<T>,
    private val onBind: (B, T) -> Unit
) : ListAdapter<T, BaseAdapter<T, B>.ViewHolder>(diffCallback) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): BaseAdapter<T, B>.ViewHolder {
        val binding = inflateMethod.invoke(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(val binding: B) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: T) {
            onBind(binding, item)
            binding.root.applyButtonPressFeedbackRecursively()
        }
    }

}
