package com.tughi.aggregator

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.google.android.material.textfield.TextInputLayout
import com.tughi.aggregator.viewmodels.SubscribeViewModel

class SubscribeActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VIA_ACTION = "via_action"
    }

    private val urlTextInputLayout by lazy { findViewById<TextInputLayout>(R.id.url_wrapper) }
    private val urlEditText by lazy { urlTextInputLayout.findViewById<EditText>(R.id.url) }
    private val messageTextView by lazy { findViewById<TextView>(R.id.message) }
    private val progressBar by lazy { findViewById<ProgressBar>(R.id.progress) }

    private lateinit var viewModel: SubscribeViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.subscribe_activity)

        viewModel = ViewModelProviders.of(this).get(SubscribeViewModel::class.java)
        viewModel.busy.observe(this, Observer {
            updateUI()
        })

        urlEditText.setOnEditorActionListener { view, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                findFeeds()
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false
        }

        updateUI()
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            if (!intent.getBooleanExtra(EXTRA_VIA_ACTION, false)) {
                setHomeAsUpIndicator(R.drawable.action_cancel)
            }
        }

        urlEditText.requestFocus()
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            android.R.id.home ->
                finish()
            else ->
                return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun findFeeds() {
        viewModel.findFeeds(urlEditText.text.toString())
    }

    private fun updateUI() {
        if (viewModel.busy.value == true) {
            urlTextInputLayout.isEnabled = false
            messageTextView.visibility = View.GONE
            progressBar.visibility = View.VISIBLE
        } else {
            urlTextInputLayout.isEnabled = true
            progressBar.visibility = View.GONE

            val feeds = viewModel.feeds.value
            if (feeds == null) {
                messageTextView.visibility = View.VISIBLE
                messageTextView.text = viewModel.message.value
            }
        }
    }

}
