package com.tughi.aggregator.activities.feedsettings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.tughi.aggregator.AppDatabase
import com.tughi.aggregator.R
import com.tughi.aggregator.activities.updatemode.UpdateModeActivity
import com.tughi.aggregator.activities.updatemode.startUpdateModeActivity
import com.tughi.aggregator.activities.updatemode.toString
import com.tughi.aggregator.data.UpdateMode
import com.tughi.aggregator.services.AutoUpdateScheduler
import com.tughi.aggregator.services.FaviconUpdaterService
import com.tughi.aggregator.utilities.backupFeeds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class FeedSettingsFragment : Fragment() {

    companion object {
        const val ARG_FEED_ID = "feed_id"

        const val REQUEST_UPDATE_MODE = 1
    }

    private lateinit var urlEditText: EditText
    private lateinit var titleEditText: EditText
    private lateinit var updateModeTextView: TextView

    private lateinit var viewModel: FeedSettingsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val fragmentView = inflater.inflate(R.layout.feed_settings_fragment, container, false)

        urlEditText = fragmentView.findViewById(R.id.url)
        titleEditText = fragmentView.findViewById(R.id.title)
        updateModeTextView = fragmentView.findViewById(R.id.update_mode)

        updateModeTextView.keyListener = null
        updateModeTextView.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                val inputMethodManager = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)

                view.callOnClick()
            }
        }
        updateModeTextView.setOnClickListener {
            val feed = viewModel.feed.value ?: return@setOnClickListener
            startUpdateModeActivity(REQUEST_UPDATE_MODE, viewModel.newUpdateMode ?: feed.updateMode)
        }


        val feedId = arguments!!.getLong(ARG_FEED_ID)
        viewModel = ViewModelProviders.of(this, FeedSettingsViewModel.Factory(feedId))
                .get(FeedSettingsViewModel::class.java)

        viewModel.feed.observe(this, Observer { feed ->
            if (feed != null) {
                urlEditText.setText(feed.url)
                titleEditText.setText(feed.customTitle ?: feed.title)

                updateModeTextView.apply { text = feed.updateMode.toString(context) }
            }
        })

        fragmentView.findViewById<View>(R.id.unsubscribe).setOnClickListener {
            val feed = viewModel.feed.value
            if (feed != null) {
                val feedTitle = feed.customTitle ?: feed.title
                UnsubscribeDialogFragment.show(fragmentManager!!, feedId, feedTitle, true)
            }
        }

        return fragmentView
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_UPDATE_MODE && resultCode == Activity.RESULT_OK) {
            val serializedUpdateMode = data?.getStringExtra(UpdateModeActivity.EXTRA_UPDATE_MODE) ?: return
            viewModel.newUpdateMode = UpdateMode.deserialize(serializedUpdateMode).also { updateMode ->
                updateModeTextView.apply { text = updateMode.toString(context) }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater?.inflate(R.menu.feed_settings_fragment, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean = when (item?.itemId) {
        R.id.save -> onSave()
        else -> super.onOptionsItemSelected(item)
    }

    private fun onSave(): Boolean {
        val url = urlEditText.text.toString().trim()
        val title = titleEditText.text.toString().trim()
        val updateMode = viewModel.newUpdateMode

        val feed = viewModel.feed.value
        if (feed?.id != null) {
            GlobalScope.launch {
                val feedDao = AppDatabase.instance.feedDao()

                feedDao.updateFeed(
                        id = feed.id,
                        url = url,
                        customTitle = if (title.isEmpty()) null else title,
                        updateMode = updateMode ?: feed.updateMode
                )

                if (updateMode != null && updateMode != feed.updateMode) {
                    AutoUpdateScheduler.scheduleFeed(feed.id)
                }

                GlobalScope.launch(Dispatchers.IO) {
                    backupFeeds()
                }

                launch(Dispatchers.Main) {
                    FaviconUpdaterService.start(feed.id)

                    activity?.finish()
                }
            }
        }

        return true
    }

}
