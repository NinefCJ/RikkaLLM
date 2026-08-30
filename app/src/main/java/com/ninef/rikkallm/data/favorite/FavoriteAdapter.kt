package com.ninef.rikkallm.data.favorite

import com.ninef.rikkallm.data.db.entity.FavoriteEntity
import com.ninef.rikkallm.data.model.FavoriteType

interface FavoriteAdapter<T> {
    val type: FavoriteType

    fun buildRefKey(target: T): String

    fun buildFavoriteEntity(
        target: T,
        existing: FavoriteEntity? = null,
        now: Long = System.currentTimeMillis()
    ): FavoriteEntity
}
