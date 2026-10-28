package com.armanmaurya.internetradio.core.exception

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class CoreExceptionsTest {

    @Test
    fun storageExceptions_areSubclassesOfStorageExceptionAndIOException() {
        val fileNotFound = StorageException.FileNotFound("Not found")
        val permissionDenied = StorageException.PermissionDenied("No perm")
        val storageFull = StorageException.StorageFull("Full")
        val writeFailed = StorageException.StreamWriteFailed("Write failed")
        val readFailed = StorageException.StreamReadFailed("Read failed")

        val exceptions = listOf(fileNotFound, permissionDenied, storageFull, writeFailed, readFailed)

        exceptions.forEach { ex ->
            assertTrue(ex is StorageException)
            assertTrue(ex is IOException)
        }
    }

    @Test
    fun storageException_retainsMessageAndCause() {
        val cause = IOException("Root cause")
        val ex = StorageException.StreamWriteFailed("Failed to write", cause)

        assertEquals("Failed to write", ex.message)
        assertEquals(cause, ex.cause)
    }

    @Test
    fun networkExceptions_areSubclassesOfNetworkExceptionAndIOException() {
        val noConn = NetworkException.NoConnectivity()
        val timeout = NetworkException.ServerTimeout()
        val serverErr = NetworkException.ServerError(500, "Internal Server Error")
        val apiEx = NetworkException.ApiException(404, "Not Found")

        val exceptions = listOf(noConn, timeout, serverErr, apiEx)

        exceptions.forEach { ex ->
            assertTrue(ex is NetworkException)
            assertTrue(ex is IOException)
        }
    }

    @Test
    fun networkException_retainsDetails() {
        val ex = NetworkException.ApiException(400, "Bad Request")
        assertEquals(400, ex.code)
        assertEquals("Bad Request", ex.message)
    }
}
