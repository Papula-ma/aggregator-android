package com.tughi.aggregator.utilities

import com.tughi.aggregator.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.internal.readBomAsCharset
import java.io.IOException
import java.io.InputStreamReader
import java.io.Reader
import java.net.MalformedURLException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

object Http {

    private val client: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(UserAgentInterceptor())
            .build()

    suspend fun request(url: String, config: (Request.Builder) -> Unit = {}) = suspendCancellableCoroutine<Result<Response>> {
        val request = Request.Builder()
                .apply {
                    try {
                        url(url)
                    } catch (exception: IllegalArgumentException) {
                        it.resume(Failure(MalformedURLException(url)))
                        return@suspendCancellableCoroutine
                    }
                    config(this)
                }
                .build()

        val call = client.newCall(request)
        it.invokeOnCancellation {
            call.cancel()
        }

        call.enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                it.resume(Success(response))
            }

            override fun onFailure(call: Call, e: IOException) {
                it.resume(Failure(e))
            }
        })
    }

    class UserAgentInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request().newBuilder()
                    .header("User-Agent", "Aggregator/${BuildConfig.VERSION_NAME}")
                    .build()
            return chain.proceed(request)
        }
    }

}

fun ResponseBody.content(): Reader {
    val source = source()
    val charset = contentType()?.charset()?.let { if (it == Charsets.UTF_16) Charsets.UTF_16LE else it } ?: Charsets.UTF_8
    return InputStreamReader(source.inputStream(), source.readBomAsCharset(charset))
}
