package com.fdzaki.adshield.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// v4.5.0: version 2->3 for DomainLogEntity.backgroundApp (Silent Leak
// Detector — see kdoc there). fallbackToDestructiveMigration() below already
// covers this, same as the 1->2 bump in v4.3.0.
@Database(entities = [DomainLogEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun domainLogDao(): DomainLogDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "adshield.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
