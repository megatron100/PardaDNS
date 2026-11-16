package com.example.pardadns.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.pardadns.data.db.entity.AppQueryStatsEntity
import com.example.pardadns.data.db.entity.DailyStatsEntity
import com.example.pardadns.data.db.entity.DnsLatencyEntity
import com.example.pardadns.data.db.entity.DomainStatsEntity
import com.example.pardadns.data.db.entity.HourlyStatsEntity
import com.example.pardadns.data.db.entity.QueryLogEntity

@Database(
    entities = [
        DailyStatsEntity::class,
        DomainStatsEntity::class,
        AppQueryStatsEntity::class,
        HourlyStatsEntity::class,
        DnsLatencyEntity::class,
        QueryLogEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class PardaDatabase : RoomDatabase() {
    abstract fun statsDao(): StatsDao
}
