package com.rjy.xyz.apps.xyzinfo.ui.compare

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.rjy.xyz.apps.xyzinfo.R
import com.rjy.xyz.apps.xyzinfo.data.CompareRow
import com.rjy.xyz.apps.xyzinfo.data.DeviceSnapshot

/**
 * 对比表适配器：把 A 机的每一行与 B 机按「分组 + 标签」对齐，并做差异高亮。
 *
 * - 数值型（[CompareRow.numeric] 非 null）按大小判优劣：更大的一方标绿（更优），更小的一方标红；
 * - 文本型不同则双方都标黄；
 * - 相同 / B 机未提供则保持默认色。
 */
sealed class CompareItem {
    data class Group(val name: String) : CompareItem()
    data class Row(val a: CompareRow?, val b: CompareRow?) : CompareItem()
}

class CompareAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<CompareItem>()

    fun submit(a: DeviceSnapshot?, b: DeviceSnapshot?) {
        items.clear()
        items.addAll(buildCompareItems(a, b))
        notifyDataSetChanged()
    }

    internal fun buildCompareItems(a: DeviceSnapshot?, b: DeviceSnapshot?): List<CompareItem> {
        if (a == null) return emptyList()
        val result = mutableListOf<CompareItem>()
        val aRows = a.rows
        val bRows = b?.rows.orEmpty()
        val aMap = aRows.associateBy { it.group to it.label }
        val bMap = bRows.associateBy { it.group to it.label }
        val groups = LinkedHashSet<String>().apply {
            addAll(aRows.map { it.group })
            addAll(bRows.map { it.group })
        }
        groups.forEach { group ->
            result.add(CompareItem.Group(group))
            val labels = LinkedHashSet<String>().apply {
                addAll(aRows.filter { it.group == group }.map { it.label })
                addAll(bRows.filter { it.group == group }.map { it.label })
            }
            labels.forEach { label ->
                result.add(CompareItem.Row(aMap[group to label], bMap[group to label]))
            }
        }
        return result
    }

    override fun getItemViewType(position: Int): Int =
        when (items[position]) {
            is CompareItem.Group -> VIEW_GROUP
            is CompareItem.Row -> VIEW_ROW
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_GROUP) {
            GroupVH(inflater.inflate(R.layout.compare_group, parent, false))
        } else {
            RowVH(inflater.inflate(R.layout.compare_row, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is CompareItem.Group -> (holder as GroupVH).tvGroup.text = item.name
            is CompareItem.Row -> (holder as RowVH).bind(item.a, item.b)
        }
    }

    override fun getItemCount(): Int = items.size

    private class GroupVH(view: View) : RecyclerView.ViewHolder(view) {
        val tvGroup: TextView = view.findViewById(R.id.tvGroup)
    }

    private class RowVH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvLabel: TextView = view.findViewById(R.id.tvLabel)
        private val tvA: TextView = view.findViewById(R.id.tvA)
        private val tvB: TextView = view.findViewById(R.id.tvB)
        private val ctx = view.context

        fun bind(a: CompareRow?, b: CompareRow?) {
            tvLabel.text = a?.label ?: b?.label.orEmpty()
            tvA.text = a?.value ?: "—"
            tvB.text = b?.value ?: "—"

            val defaultColor = ContextCompat.getColor(ctx, R.color.text_primary)
            tvA.setTextColor(defaultColor)
            tvB.setTextColor(defaultColor)

            if (a == null || b == null) return

            if (a.numeric != null && b.numeric != null && a.numeric != b.numeric) {
                val aBetter = if (a.betterWhenHigher) a.numeric > b.numeric else a.numeric < b.numeric
                tvA.setTextColor(if (aBetter) colorBetter else colorWorse)
                tvB.setTextColor(if (aBetter) colorWorse else colorBetter)
            } else if (a.value != b.value) {
                tvA.setTextColor(colorDiff)
                tvB.setTextColor(colorDiff)
            }
        }
    }

    private companion object {
        const val VIEW_GROUP = 0
        const val VIEW_ROW = 1
        /** 更优：绿；更弱：红；文本不同：黄。 */
        val colorBetter = 0xFF0E9F76.toInt()
        val colorWorse = 0xFFE5484D.toInt()
        val colorDiff = 0xFFF2B705.toInt()
    }
}
