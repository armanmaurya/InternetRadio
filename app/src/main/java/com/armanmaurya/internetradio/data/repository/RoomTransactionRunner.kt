package com.armanmaurya.internetradio.data.repository

import androidx.room.withTransaction
import com.armanmaurya.internetradio.data.local.RadioDatabase
import com.armanmaurya.internetradio.domain.repository.TransactionRunner
import javax.inject.Inject

class RoomTransactionRunner @Inject constructor(
    private val database: RadioDatabase
) : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = database.withTransaction { block() }
}
