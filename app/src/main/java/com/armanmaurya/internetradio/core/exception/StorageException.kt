package com.armanmaurya.internetradio.core.exception

import java.io.IOException

/**
 * Represents system and framework-level storage, SAF (Storage Access Framework),
 * and file I/O failures across the application.
 */
sealed class StorageException(
    message: String,
    cause: Throwable? = null
) : IOException(message, cause) {

    class FileNotFound(
        message: String = "Requested file or document was not found",
        cause: Throwable? = null
    ) : StorageException(message, cause)

    class PermissionDenied(
        message: String = "Storage or URI permission revoked or not granted",
        cause: Throwable? = null
    ) : StorageException(message, cause)

    class StorageFull(
        message: String = "Device storage is full",
        cause: Throwable? = null
    ) : StorageException(message, cause)

    class StreamWriteFailed(
        message: String = "Failed to open or write to output stream",
        cause: Throwable? = null
    ) : StorageException(message, cause)

    class StreamReadFailed(
        message: String = "Failed to open or read from input stream",
        cause: Throwable? = null
    ) : StorageException(message, cause)
}
