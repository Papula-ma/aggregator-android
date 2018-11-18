package com.tughi.aggregator

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class UnsubscribeDialogFragment : DialogFragment() {

    companion object {
        const val ARG_FEED_ID = "feed_id"
        const val ARG_FEED_TITLE = "feed_title"
        const val ARG_FINISH_ACTIVITY = "finish_activity"

        fun show(fragmentManager: FragmentManager, feedId: Long, feedTitle: String, finishActivity: Boolean) {
            UnsubscribeDialogFragment()
                    .apply {
                        arguments = Bundle().apply {
                            putLong(ARG_FEED_ID, feedId)
                            putString(ARG_FEED_TITLE, feedTitle)
                            putBoolean(ARG_FINISH_ACTIVITY, finishActivity)
                        }
                    }
                    .show(fragmentManager, "unsubscribe-dialog")
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val arguments = arguments!!
        val feedId = arguments.getLong(ARG_FEED_ID)
        val feedTitle = arguments.getString(ARG_FEED_TITLE)
        val finishActivity = arguments.getBoolean(ARG_FINISH_ACTIVITY)
        return AlertDialog.Builder(context!!)
                .setTitle(feedTitle)
                .setMessage(R.string.unsubscribe_feed__message)
                .setNegativeButton(R.string.action__no, null)
                .setPositiveButton(R.string.action__yes) { dialog, _ ->
                    GlobalScope.launch {
                        AppDatabase.instance.feedDao().deleteFeed(feedId)
                    }
                    if (finishActivity) {
                        activity?.finish()
                    }
                }
                .create()
    }
}