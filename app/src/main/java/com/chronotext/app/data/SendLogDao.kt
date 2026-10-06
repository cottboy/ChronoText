package com.chronotext.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface SendLogDao {

    /** 最近记录（含快照字段，任务删除后历史仍可读） */
    @Query("SELECT * FROM send_logs ORDER BY scheduledAt DESC LIMIT 500")
    suspend fun recent(): List<SendLogEntity>

    @Query("SELECT * FROM send_logs WHERE id = :id")
    suspend fun byId(id: Long): SendLogEntity?

    @Insert
    suspend fun insert(log: SendLogEntity): Long

    @Update
    suspend fun update(log: SendLogEntity)

    @Query("DELETE FROM send_logs")
    suspend fun clearAll()
}
