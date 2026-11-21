package com.odiousapps.gardencam

import android.content.Intent
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.odiousapps.gardencam.Alert
import com.odiousapps.gardencam.Effects.edgeToEdge
import com.odiousapps.gardencam.GroupData
import com.odiousapps.gardencam.LocalNetwork
import com.odiousapps.gardencam.Settings
import com.odiousapps.gardencam.SourceAdapter
import com.odiousapps.gardencam.SourceData
import com.odiousapps.gardencam.SourceItemTouch
import com.odiousapps.gardencam.StreamData
import com.odiousapps.gardencam.Utils
import com.odiousapps.gardencam.MainApp.Companion.context
import com.odiousapps.gardencam.databinding.ActivityMainBinding

class MainActivity: AppCompatActivity() {
    companion object {
        private var isCreated: Boolean = false
    }

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val sources by lazy { SourceData.getAll() }

    val exportSettings = Settings(this).export()
    val importSettings = Settings(this).import()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (redirect())
            return // StreamsActivity requests local network access itself
        setContentView(binding.root)
        edgeToEdge(binding.root)
        initActivity()
        if (LocalNetwork.needsPermission(this, StreamData.getAll().flatMap { LocalNetwork.urlsOf(it) }))
            requestPermissions(arrayOf(LocalNetwork.PERMISSION), 0)
    }

    private fun initActivity() {
        GridLayoutManager(
            this,
            Utils.getColumnCount(resources.displayMetrics),
            RecyclerView.VERTICAL,
            false)
            .apply { binding.recyclerView.layoutManager = this }

        binding.toolbar.btnBack.setImageResource(R.drawable.ic_baseline_menu_24)
        binding.toolbar.btnBack.setOnClickListener {
            MainMenu(this).showPopupMenu(it)
        }
        binding.toolbar.tvLabel.text = getString(R.string.main_title)

        binding.recyclerView.adapter = SourceAdapter(sources) { it: Intent -> startActivity(it) }
        SourceItemTouch().helper().attachToRecyclerView(binding.recyclerView)

        if (sources.isEmpty())
            initEmpty()
        else
            Alert.init(this, binding.toolbar.btnAlert)

        this.onBackPressedDispatcher.addCallback(callback)
    }

    private fun initEmpty() {
        binding.emptyBox.emptyContent.visibility = View.VISIBLE
        binding.emptyBox.btnEdit.setOnClickListener {
            val intent = Intent(this, EditActivity::class.java)
            startActivity(intent)
        }
        binding.emptyBox.btnImport.setOnClickListener {
            MainMenu(this).import()
        }
        binding.emptyBox.tvManualLink.text = HtmlCompat.fromHtml(
            getString(R.string.manual_link),
            HtmlCompat.FROM_HTML_MODE_COMPACT)
        binding.emptyBox.tvManualLink.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun redirect(): Boolean {
        if (isCreated)
            return false
        isCreated = true
        val startup = SourceData.getStartup() ?: return false
        if (startup.id < 0)
            return false

        val newIntent = Intent(context, StreamsActivity::class.java).putExtra("id", startup.id)
        when (startup.type) {
            "stream" -> {
                val streams = StreamData.getAll()
                if (streams.isEmpty() || startup.id > streams.count() - 1)
                    return false
                newIntent.putExtra("type", "stream")
            }
            "group" -> {
                val groups = GroupData.getAll()
                if (groups.isEmpty() || startup.id > groups.count() - 1)
                    return false
                newIntent.putExtra("type", "group")
            }
            else -> return false
        }
        startActivity(newIntent)
        return true
    }

    private val callback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            finishAffinity()
        }
    }
}
