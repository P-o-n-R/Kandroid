package com.kandroid.app

import android.app.Application
import androidx.room.Room
import com.kandroid.app.data.KandroidDatabase
import com.kandroid.app.data.KanboardRepository
import com.kandroid.app.security.CredentialStore

class KandroidApplication : Application() {
    val database by lazy { Room.databaseBuilder(this, KandroidDatabase::class.java, "kandroid.db").build() }
    val repository by lazy { KanboardRepository(database.dao()) }
    val credentialStore by lazy { CredentialStore(this) }
}

