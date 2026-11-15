package com.rjy.xyz.apps.xyzinfo.ui.compare

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.rjy.xyz.apps.xyzinfo.R
import com.rjy.xyz.apps.xyzinfo.data.DeviceCatalog

/** 内置机型库卡片列表：品牌、芯片和关键规格分三层展示。 */
class DeviceCatalogAdapter(
    private val onSelected: (DeviceCatalog.Device) -> Unit
) : RecyclerView.Adapter<DeviceCatalogAdapter.DeviceViewHolder>() {

    private var devices: List<DeviceCatalog.Device> = emptyList()

    fun submit(items: List<DeviceCatalog.Device>) {
        devices = items
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder =
        DeviceViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_device_catalog, parent, false)
        )

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(devices[position], onSelected)
    }

    override fun getItemCount(): Int = devices.size

    class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val brand: TextView = view.findViewById(R.id.tvCatalogBrand)
        private val name: TextView = view.findViewById(R.id.tvCatalogName)
        private val chip: TextView = view.findViewById(R.id.tvCatalogChip)
        private val specs: TextView = view.findViewById(R.id.tvCatalogSpecs)

        fun bind(device: DeviceCatalog.Device, onSelected: (DeviceCatalog.Device) -> Unit) {
            brand.text = device.brand
            name.text = device.name
            chip.text = device.processor
            specs.text = buildList {
                if (device.ramGb > 0) add("${device.ramGb} GB")
                if (device.storage.isNotBlank()) add(device.storage)
                if (device.batteryMah > 0) add("${device.batteryMah} mAh")
                if (device.screenSize.isNotBlank()) add("${device.screenSize}\"")
                if (device.refreshHz > 0) add("${device.refreshHz} Hz")
            }.joinToString(" · ")
            itemView.setOnClickListener { onSelected(device) }
        }
    }
}
