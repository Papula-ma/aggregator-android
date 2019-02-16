package com.tughi.aggregator.activities.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.annotation.StringRes
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.tughi.aggregator.AppDatabase
import com.tughi.aggregator.R
import com.tughi.aggregator.activities.reader.ReaderActivity
import com.tughi.aggregator.data.EntriesQuery
import com.tughi.aggregator.data.EntriesSortOrderByDateAsc
import com.tughi.aggregator.data.EntriesSortOrderByDateDesc
import com.tughi.aggregator.data.EntriesSortOrderByTitle
import com.tughi.aggregator.preferences.EntryListSettings
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

abstract class EntriesFragment : Fragment(), EntriesFragmentAdapterListener {

    private lateinit var toolbar: Toolbar

    protected val sessionTime = System.currentTimeMillis()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val fragmentView = inflater.inflate(R.layout.entry_list_fragment, container, false)

        val viewModelFactory = EntriesFragmentViewModel.Factory(getEntriesQuery())
        val viewModel = ViewModelProviders.of(this, viewModelFactory).get(EntriesFragmentViewModel::class.java)

        val entriesRecyclerView = fragmentView.findViewById<RecyclerView>(R.id.entries)
        val progressBar = fragmentView.findViewById<ProgressBar>(R.id.progress)

        entriesRecyclerView.adapter = EntriesFragmentEntryAdapter(this).also { adapter ->
            viewModel.entries.observe(this, Observer { entries ->
                adapter.submitList(entries)

                progressBar.visibility = View.GONE
            })
        }
        ItemTouchHelper(SwipeItemTouchHelper()).attachToRecyclerView(entriesRecyclerView)

        toolbar = fragmentView.findViewById(R.id.toolbar)
        toolbar.setNavigationIcon(when (this) {
            is MyFeedFragment -> R.drawable.action_menu
            else -> R.drawable.action_back
        })
        toolbar.setNavigationOnClickListener { onNavigationClick() }

        toolbar.inflateMenu(R.menu.entry_list_fragment)
        toolbar.setOnMenuItemClickListener {
            val entriesSortOrder = when (it?.itemId) {
                R.id.sort_by_date_asc -> EntriesSortOrderByDateAsc
                R.id.sort_by_date_desc -> EntriesSortOrderByDateDesc
                R.id.sort_by_title -> EntriesSortOrderByTitle
                else -> return@setOnMenuItemClickListener false
            }

            EntryListSettings.entriesSortOrder.value = entriesSortOrder

            return@setOnMenuItemClickListener true
        }

        EntryListSettings.entriesSortOrder.observe(this, Observer { entriesSortOrder ->
            toolbar.menu?.let {
                val sortMenuItemId = when (entriesSortOrder) {
                    EntriesSortOrderByDateAsc -> R.id.sort_by_date_asc
                    EntriesSortOrderByDateDesc -> R.id.sort_by_date_desc
                    EntriesSortOrderByTitle -> R.id.sort_by_title
                }
                it.findItem(sortMenuItemId).isChecked = true
            }
        })

        return fragmentView
    }

    abstract fun getEntriesQuery(): EntriesQuery

    abstract fun onNavigationClick()

    protected fun setTitle(@StringRes title: Int) {
        toolbar.setTitle(title)
    }

    protected fun setTitle(title: String) {
        toolbar.title = title
    }

    override fun onEntryClicked(entry: EntriesFragmentEntry, position: Int) {
        context?.run {
            startActivity(
                    Intent(this, ReaderActivity::class.java)
                            .putExtra(ReaderActivity.EXTRA_ENTRIES_QUERY, getEntriesQuery())
                            .putExtra(ReaderActivity.EXTRA_ENTRIES_SORT_ORDER, EntryListSettings.entriesSortOrder.value)
                            .putExtra(ReaderActivity.EXTRA_ENTRIES_POSITION, position)
            )
        }
    }

    private class SwipeItemTouchHelper : ItemTouchHelper.Callback() {

        override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int =
                when (viewHolder) {
                    is EntriesFragmentEntryViewHolder -> makeMovementFlags(0, ItemTouchHelper.START or ItemTouchHelper.END)
                    else -> 0
                }


        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            return false
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            if (viewHolder is EntriesFragmentEntryViewHolder) {
                val entry = viewHolder.entry
                GlobalScope.launch {
                    AppDatabase.instance.entryDao().run {
                        if (entry.readTime == 0L || entry.pinnedTime != 0L) {
                            markEntryRead(entry.id, System.currentTimeMillis())
                        } else {
                            markEntryPinned(entry.id, System.currentTimeMillis())
                        }
                    }
                }
            }
        }

    }

}

