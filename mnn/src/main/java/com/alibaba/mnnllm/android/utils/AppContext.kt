// Ported to RikkaLLM.
// Minimal replacement for MnnLlmChat's com.alibaba.mls.api.ApplicationProvider,
// which is not available outside of the original app.

package com.alibaba.mnnllm.android.utils

import android.annotation.SuppressLint
import android.content.Context

@SuppressLint("StaticFieldLeak")
object AppContext {

    @Volatile
    private var application: Context? = null

    /**
     * Must be called once (e.g. from Application.onCreate) before using the MNN module.
     */
    fun init(context: Context) {
        application = context.applicationContext
    }

    fun get(): Context {
        return application
            ?: error("AppContext.init(context) must be called before using the MNN module")
    }
}
