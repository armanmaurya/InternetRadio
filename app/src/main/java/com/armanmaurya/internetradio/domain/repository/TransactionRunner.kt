package com.armanmaurya.internetradio.domain.repository

/** Runs several repository writes as one database transaction: all of them are saved, or none. */
interface TransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}
