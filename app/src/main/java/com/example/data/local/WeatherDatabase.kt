package com.example.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "favorite_cities")
data class CityEntity(
    @PrimaryKey val name: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "weather_cache")
data class WeatherCacheEntity(
    @PrimaryKey val city: String,
    val weatherJson: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface WeatherDao {
    @Query("SELECT * FROM favorite_cities ORDER BY addedAt DESC")
    fun getFavoriteCities(): Flow<List<CityEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavoriteCity(city: CityEntity)

    @Query("DELETE FROM favorite_cities WHERE name = :name")
    suspend fun deleteFavoriteCity(name: String)

    @Query("SELECT * FROM favorite_cities WHERE name = :name LIMIT 1")
    suspend fun getFavoriteCityByName(name: String): CityEntity?

    @Query("SELECT * FROM weather_cache WHERE city = :city LIMIT 1")
    suspend fun getWeatherCache(city: String): WeatherCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeatherCache(cache: WeatherCacheEntity)
}

@Database(entities = [CityEntity::class, WeatherCacheEntity::class], version = 1, exportSchema = false)
abstract class WeatherDatabase : RoomDatabase() {
    abstract fun weatherDao(): WeatherDao
}
