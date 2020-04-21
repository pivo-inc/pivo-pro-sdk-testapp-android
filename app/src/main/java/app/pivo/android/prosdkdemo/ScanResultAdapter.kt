package app.pivo.android.prosdkdemo

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import app.pivo.android.prosdk.util.PivoDevice
import java.util.*

/**
 * Created by murodjon on 2020/03/12
 */

class ScanResultsAdapter : RecyclerView.Adapter<ScanResultsAdapter.ViewHolder?>() {
    private val TAG = "ScanResultsAdapter"

    class ViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {
        var deviceNAme: TextView
        init {
            deviceNAme = itemView.findViewById(R.id.device_item_view)
        }
    }

    interface OnAdapterItemClickListener {
        fun onAdapterViewClick(view: View?)
    }

    private var data: MutableList<PivoDevice> = ArrayList()

    private var onAdapterItemClickListener: OnAdapterItemClickListener? = null

    private val onClickListener =
        View.OnClickListener { v ->
            if (onAdapterItemClickListener != null) {
                onAdapterItemClickListener!!.onAdapterViewClick(v)
            }
        }

    fun addAllScanResults(bleScanResult: ArrayList<PivoDevice>?) {
        data = ArrayList()
        data.addAll(bleScanResult!!)
        Collections.sort(data,
            SORTING_COMPARATOR
        )
        notifyDataSetChanged()
    }

    fun addScanResult(scanDevice: PivoDevice) {
        Log.e( TAG,"Found: ${scanDevice.name}")
        for (i in data.indices) {
            if (data[i].macAddress == scanDevice.macAddress) {
                data[i] = scanDevice
                notifyItemChanged(i)
                return
            }
        }
        data.add(scanDevice)
        Collections.sort(data,
            SORTING_COMPARATOR
        )
        notifyDataSetChanged()
    }

    fun clearScanResults() {
        data.clear()
        notifyDataSetChanged()
    }

    fun getItemAtPosition(childAdapterPosition: Int): PivoDevice? {
        return if (data.size >= childAdapterPosition && childAdapterPosition >= 0) data[childAdapterPosition] else null
    }

    override fun getItemCount(): Int {
        return data.size
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        val result = data[position]
        holder.deviceNAme.text = String.format(Locale.getDefault(), "%s", result.name)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int): ViewHolder {
        val itemView: View = LayoutInflater.from(parent.context)
            .inflate(R.layout.device_item, parent, false)
        itemView.setOnClickListener(onClickListener)
        return ViewHolder(
            itemView
        )
    }

    fun setOnAdapterItemClickListener(onAdapterItemClickListener: OnAdapterItemClickListener) {
        this.onAdapterItemClickListener = onAdapterItemClickListener
    }

    companion object {
        private val SORTING_COMPARATOR =
            Comparator { lhs: PivoDevice, rhs: PivoDevice ->
                lhs.macAddress.compareTo(rhs.macAddress)
            }
    }
}
