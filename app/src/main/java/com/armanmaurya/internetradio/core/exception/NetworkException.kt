package com.armanmaurya.internetradio.core.exception

import java.io.IOException

/**
 * Represents system and framework-level network, HTTP, and API communication failures.
 */
sealed class NetworkException(
    message: String,
    cause: Throwable? = null
) : IOException(message, cause) {

    class NoConnectivity(
        message: String = "No internet connection",
        cause: Throwable? = null
    ) : NetworkException(message, cause)

    class ServerTimeout(
        message: String = "Connection timed out",
        cause: Throwable? = null
    ) : NetworkException(message, cause)

    class ServerError(
        val code: Int? = null,
        message: String = "Server encountered an error",
        cause: Throwable? = null
    ) : NetworkException(message, cause)

    class ApiException(
        val code: Int? = null,
        message: String = "Unknown API error",
        cause: Throwable? = null
    ) : NetworkException(message, cause)
}
