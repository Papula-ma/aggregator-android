package com.tughi.aggregator.viewmodels

import android.os.AsyncTask
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.tughi.aggregator.data.Feed
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jetbrains.anko.AnkoLogger
import java.io.IOException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException

class SubscribeViewModel : ViewModel(), AnkoLogger {

    val busy = MutableLiveData<Boolean>()
    var message: String? = null
    var feeds = emptyList<Feed>()
    var errorMessage: String? = null

    private var currentFindTask: FindTask? = null

    fun findFeeds(url: String) {
        currentFindTask?.cancel()

        message = null
        feeds = emptyList()
        errorMessage = null
        busy.value = true

        FindTask(this).also { currentFindTask = it }.execute(url)
    }

    override fun onCleared() {
        currentFindTask?.cancel()
    }

    class FindTask(private val viewModel: SubscribeViewModel) : AsyncTask<Any, Void, Boolean>() {

        private var requestCall: Call? = null

        fun cancel() {
            cancel(false)
            requestCall?.cancel()
        }

        override fun doInBackground(vararg params: Any?): Boolean {
            val url = params[0] as String


            val request = Request.Builder().apply {
                try {
                    url(url)
                } catch (exception: IllegalArgumentException) {
                    viewModel.errorMessage = "Invalid URL"
                    return false
                }
            }.build()

            if (!isCancelled) {
                val response: Response?
                try {
                    response = OkHttpClient().newCall(request).also { requestCall = it }.execute()
                } catch (exception: IOException) {
                    when (exception) {
                        is NoRouteToHostException -> {
                            viewModel.errorMessage = "Could not open a connection"
                        }
                        is SocketTimeoutException -> {
                            viewModel.errorMessage = "Timeout error... Please try again"
                        }
                        else -> {
                            viewModel.errorMessage = "Unexpected error: ${exception::class.java.simpleName}"
                        }
                    }
                    return false
                }

                if (!response.isSuccessful) {
                    viewModel.errorMessage = "Server response: ${response.code()} ${response.message()}"
                    return false
                }

                if (!isCancelled) {
                    val body = response.body()
                    // val contentType = body?.contentType()
                    val content = body?.string()

                    viewModel.message = content

                    // TODO: parse response content
                }
            }

            return true
        }

        override fun onPostExecute(success: Boolean?) {
            viewModel.busy.value = false
        }

    }

}