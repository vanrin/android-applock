package io.github.vanrin.applock

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch

class AppListAdapter(
    private val onToggle: (pkg: String, locked: Boolean) -> Unit,
) : RecyclerView.Adapter<AppListAdapter.Holder>() {

    data class AppEntry(val label: String, val pkg: String)

    private var all: List<AppEntry> = emptyList()
    private var shown: List<AppEntry> = emptyList()
    private val locked = mutableSetOf<String>()
    private var query = ""

    fun submit(entries: List<AppEntry>, lockedSet: Set<String>) {
        all = entries
        locked.clear()
        locked.addAll(lockedSet)
        applyFilter()
    }

    fun filter(q: String) {
        query = q.trim()
        applyFilter()
    }

    private fun applyFilter() {
        shown = if (query.isEmpty()) all else all.filter {
            it.label.contains(query, true) || it.pkg.contains(query, true)
        }
        notifyDataSetChanged()
    }

    class Holder(v: android.view.View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.icon)
        val label: TextView = v.findViewById(R.id.label)
        val pkg: TextView = v.findViewById(R.id.pkg)
        val toggle: MaterialSwitch = v.findViewById(R.id.toggle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false))

    override fun getItemCount() = shown.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = shown[position]
        holder.label.text = entry.label
        holder.pkg.text = entry.pkg
        val pm = holder.itemView.context.packageManager
        holder.icon.setImageDrawable(
            runCatching { pm.getApplicationIcon(entry.pkg) }.getOrNull()
        )
        holder.toggle.setOnCheckedChangeListener(null)
        holder.toggle.isChecked = entry.pkg in locked
        holder.toggle.setOnCheckedChangeListener { _, checked ->
            if (checked) locked.add(entry.pkg) else locked.remove(entry.pkg)
            onToggle(entry.pkg, checked)
        }
        holder.itemView.setOnClickListener { holder.toggle.toggle() }
    }
}
