package com.tughi.aggregator.viewmodels

import android.os.AsyncTask
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.tughi.aggregator.data.Feed
import com.tughi.aggregator.feeds.FeedsFinder
import com.tughi.aggregator.utilities.Http
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response
import org.jetbrains.anko.AnkoLogger
import java.io.IOException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException

class SubscribeViewModel : ViewModel(), AnkoLogger {

    val state = MutableLiveData<State>().apply {
        value = SubscribeViewModel.State(false, emptyList(), null)
    }

    private var currentFindTask: FindTask? = null

    fun findFeeds(url: String) {
        currentFindTask?.cancel()

        state.value = SubscribeViewModel.State(true, emptyList(), null)

        FindTask(this).also { currentFindTask = it }.execute(url)
    }

    override fun onCleared() {
        currentFindTask?.cancel()
    }

    data class State(val loading: Boolean, val feeds: List<Feed>, val message: String?) {
        fun cloneWith(loading: Boolean? = null, feeds: List<Feed>? = null, message: String? = null): State {
            return SubscribeViewModel.State(
                    loading = loading ?: this.loading,
                    feeds = feeds ?: this.feeds,
                    message = message ?: this.message
            )
        }
    }

    class FindTask(private val viewModel: SubscribeViewModel) : AsyncTask<Any, State, State>(), FeedsFinder.Listener {

        private val feeds = arrayListOf<Feed>()
        private var state = viewModel.state.value!!.cloneWith(feeds = feeds)
        private var requestCall: Call? = null

        fun cancel() {
            cancel(false)
            requestCall?.cancel()
        }

        override fun doInBackground(vararg params: Any?): State {
            val url = params[0] as String


            val request = Request.Builder().apply {
                try {
                    url(url)
                } catch (exception: IllegalArgumentException) {
                    return state.cloneWith(loading = false, message = "Invalid URL")
                }
            }.build()

            if (!isCancelled) {
                val response: Response?
                try {
                    response = Http.client.newCall(request).also { requestCall = it }.execute()
                } catch (exception: IOException) {
                    return when (exception) {
                        is NoRouteToHostException -> {
                            state.cloneWith(loading = false, message = "Could not open a connection")
                        }
                        is SocketTimeoutException -> {
                            state.cloneWith(loading = false, message = "Timeout error... Please try again")
                        }
                        else -> {
                            state.cloneWith(loading = false, message = "Unexpected error: ${exception::class.java.simpleName}")
                        }
                    }
                }

                if (!response.isSuccessful) {
                    return state.cloneWith(loading = false, message = "Server response: ${response.code()} ${response.message()}")
                }

                if (!isCancelled) {
                    val body = response.body()
                    val content = body?.charStream()
                    if (content != null) {
                        try {
                            FeedsFinder(this).find(response.request().url().toString(), content)
                        } catch (exception: Exception) {
                            publishProgress(state.cloneWith(message = exception.localizedMessage))
                        }
                    }
                }
            }

            val state = state
            return state.cloneWith(loading = false, message = if (state.feeds.isEmpty()) "No feeds found" else null)
        }

        override fun onProgressUpdate(vararg values: State?) {
            viewModel.state.value = values[0].also { state = it!! }
        }

        override fun onPostExecute(result: State?) {
            onProgressUpdate(result)
        }

        override fun onFeedFound(feed: Feed) {
            feeds.add(feed)
            publishProgress(state.cloneWith())
        }
    }

}