package com.tughi.aggregator.activities.reader

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.tughi.aggregator.App
import com.tughi.aggregator.R
import com.tughi.aggregator.data.Entries
import com.tughi.aggregator.data.Tags
import com.tughi.aggregator.utilities.Language
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.nio.charset.Charset


class ReaderFragment : Fragment() {

    companion object {
        internal const val ARG_ENTRY_ID = "entry_id"
        internal const val ARG_ENTRY_READ_TIME = "entry_read_time"

        private val entryTemplate by lazy {
            App.instance.resources
                    .openRawResource(R.raw.entry)
                    .readBytes()
                    .toString(Charset.defaultCharset())
        }
    }

    private lateinit var markDoneMenuItem: MenuItem
    private lateinit var markPinnedMenuItem: MenuItem
    private lateinit var addStarMenuItem: MenuItem
    private lateinit var removeStarMenuItem: MenuItem

    private var loadedEntry: ReaderFragmentViewModel.Entry? = null
    private var loadedEntryHtml: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val fragmentView = inflater.inflate(R.layout.reader_entry_fragment, container, false)

        arguments?.also { arguments ->
            val entryId = arguments.getLong(ARG_ENTRY_ID)
            val entryReadTime = arguments.getLong(ARG_ENTRY_READ_TIME)

            val viewModelFactory = ReaderFragmentViewModel.Factory(entryId, entryReadTime)
            val viewModel = ViewModelProviders.of(this, viewModelFactory).get(ReaderFragmentViewModel::class.java)

            val webView: WebView = fragmentView.findViewById(R.id.content)

            webView.settings.apply {
                javaScriptEnabled = false // TODO: enable one content is sanitized
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
            }
            webView.scrollBarStyle = WebView.SCROLLBARS_INSIDE_OVERLAY

            val attributes = context!!.obtainStyledAttributes(intArrayOf(android.R.attr.colorBackground))
            webView.setBackgroundColor(attributes.getColor(0, 0))
            attributes.recycle()

            viewModel.entry.observe(this, Observer { entry ->
                loadedEntry = entry ?: return@Observer

                val entryLink = entry.link
                val entryTitle = entry.title
                val entryFeedTitle = entry.feedTitle
                val entryFeedLanguage = entry.feedLanguage
                val entryPublished = entry.publishTime
                val entryContent = entry.content
                val entryAuthor = entry.author

                // TODO: run this in a coroutine
                val entryHtml = entryTemplate
                        .replace("{{ reader.theme }}", App.theme.value?.toLowerCase() ?: "")
                        .replace("{{ layout_direction }}", if (Language.isRightToLeft(entryFeedLanguage)) "rtl" else "ltr")
                        .replace("{{ entry.feed_name }}", entryFeedTitle)
                        .replace("{{ entry.link }}", entryLink ?: "#")
                        .replace("{{ entry.date }}", DateUtils.formatDateTime(activity, entryPublished, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR))
                        .replace("{{ entry.title }}", entryTitle ?: "")
                        .replace("{{ entry.author }}", entryAuthor ?: "")
                        .replace("{{ entry.content }}", entryContent ?: "")

                if (entryHtml != loadedEntryHtml) {
                    loadedEntryHtml = entryHtml
                    webView.loadDataWithBaseURL(entryLink, entryHtml, "text/html", null, null)
                }

                activity?.invalidateOptionsMenu()
            })
        }

        return fragmentView
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)

        menu?.let {
            inflater?.inflate(R.menu.reader_activity, it)

            markDoneMenuItem = it.findItem(R.id.mark_done)
            markPinnedMenuItem = it.findItem(R.id.mark_pinned)
            addStarMenuItem = it.findItem(R.id.add_star)
            removeStarMenuItem = it.findItem(R.id.remove_star)
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu?) {
        super.onPrepareOptionsMenu(menu)

        val read = loadedEntry?.run { readTime != 0L && pinnedTime == 0L } ?: false

        markDoneMenuItem.isVisible = !read
        markPinnedMenuItem.isVisible = read

        val star = loadedEntry?.run { starTime != 0L } ?: false

        addStarMenuItem.isVisible = !star
        removeStarMenuItem.isVisible = star
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.mark_done -> {
                loadedEntry?.let {
                    GlobalScope.launch {
                        Entries.markRead(it.id)
                    }
                }
            }
            R.id.mark_pinned -> {
                loadedEntry?.let {
                    GlobalScope.launch {
                        Entries.markPinned(it.id)
                    }
                }
            }
            R.id.add_star -> {
                loadedEntry?.let {
                    GlobalScope.launch {
                        Tags.addTag(it.id, Tags.STAR)
                    }
                }
            }
            R.id.remove_star -> {
                loadedEntry?.let {
                    GlobalScope.launch {
                        Tags.removeTag(it.id, Tags.STAR)
                    }
                }
            }
            R.id.share -> {
                // TODO: share entry
                Toast.makeText(context!!, "Not implemented yet", Toast.LENGTH_SHORT).show()
            }
            R.id.tag -> {
                // TODO: tag entry
                Toast.makeText(context!!, "Not implemented yet", Toast.LENGTH_SHORT).show()
            }
            else -> {
                return super.onOptionsItemSelected(item)
            }
        }

        return true
    }

}
