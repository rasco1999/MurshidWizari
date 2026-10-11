package com.murshidwizari.student

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.ConnectException
import javax.net.ssl.SSLException

/** A failed POST may already have reached the server; never silently resend it. */
object NetworkRules {
    /** 5xx, proxy/intermediate responses and malformed receipts cannot prove a POST was rejected. */
    fun uncertainHttpMutation(status: Int, validJson: Boolean): Boolean =
        status !in setOf(401, 403) &&
            (!validJson || status in 300..399 || status in setOf(408, 409, 425, 429) || status >= 500)

    fun requiresReconciliation(networkError: Boolean, transportUncertain: Boolean): Boolean =
        networkError || transportUncertain

    fun message(error: Exception, mayHaveSubmitted: Boolean = false): String {
        val detail = when (error) {
            is SocketTimeoutException -> "الاتصال بطيء أو انتهت مهلة انتظار الخادم."
            is UnknownHostException, is ConnectException -> "الإنترنت غير متاح أو تعذر الوصول إلى الخادم."
            is SSLException -> "تعذر إنشاء اتصال آمن بالخادم."
            else -> "تعذر الاتصال بالخادم. تحقق من الإنترنت."
        }
        return detail + if (mayHaveSubmitted) " ربما وصل الطلب؛ تحقق من حالته قبل إعادة الإرسال." else " حاول مجدداً."
    }
}
