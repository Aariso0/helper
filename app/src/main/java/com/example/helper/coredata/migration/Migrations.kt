package com.example.helper.coredata.migration

import androidx.room.migration.Migration

/**
 * Explicit, reviewable Room migrations, versioned in order (core-data card).
 * Every schema change adds its Migration here and bumps AppDatabase's version;
 * destructive fallbacks are banned in this project ("never lose work").
 * Empty at schema v1 — the pre-built baseline needed none.
 */
object Migrations {
    val ALL: Array<Migration> = arrayOf()
}
