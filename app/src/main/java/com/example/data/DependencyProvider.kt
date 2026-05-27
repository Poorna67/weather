package com.example.data

import android.content.Context
import androidx.room.Room
import com.example.data.local.WeatherDatabase
import com.example.data.repository.WeatherRepository

object DependencyProvider {
    private var database: WeatherDatabase? = null
    private var repository: WeatherRepository? = null

    fun initialize(context: Context) {
        synchronized(this) {
            if (database == null) {
                database = Room.databaseBuilder(
                    context.applicationContext,
                    WeatherDatabase::class.java,
                    "aura_weather_db"
                )
                .fallbackToDestructiveMigration()
                .build()
            }
            if (repository == null) {
                repository = WeatherRepository(database!!.weatherDao())
            }
        }
    }

    fun getRepository(): WeatherRepository {
        return repository ?: throw IllegalStateException("DependencyProvider has not been initialized. Call initialize(context) first.")
    }
}
