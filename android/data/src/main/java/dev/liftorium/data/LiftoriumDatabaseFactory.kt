package dev.liftorium.data

import android.content.Context
import androidx.room.Room

/**
 * Single entry point for building the production [LiftoriumDatabase]
 * instance. Lives in `:data` so `:app` does not need to depend on the
 * `androidx.room` runtime artifact directly; the manual DI container
 * in `dev.liftorium.app.di.AppContainer` calls
 * [createLiftoriumDatabase] to construct the singleton database for
 * the process lifetime.
 *
 * The production database is built with [LIFTORIUM_DATABASE_MIGRATIONS]
 * registered. Per ADR 2026-05-16, destructive migrations are forbidden;
 * every version bump MUST add an entry to that list AND ship a paired
 * `MigrationTest` case.
 */
public object LiftoriumDatabaseFactory {

    public const val DATABASE_NAME: String = "liftorium.db"

    public fun create(context: Context): LiftoriumDatabase =
        Room.databaseBuilder(
            context.applicationContext,
            LiftoriumDatabase::class.java,
            DATABASE_NAME,
        )
            .addMigrations(*LIFTORIUM_DATABASE_MIGRATIONS)
            .build()
}

/** Convenience top-level for symmetry with the rest of `:data`. */
public fun createLiftoriumDatabase(context: Context): LiftoriumDatabase =
    LiftoriumDatabaseFactory.create(context)
