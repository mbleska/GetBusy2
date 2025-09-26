package com.example.getbusy.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ------------------------------
// Entities
// ------------------------------

@Entity(tableName = "activities")
data class ActivityItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val text: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false
)

enum class TagCategory { PLACE, COMPANY, DURATION }

@Entity(
    tableName = "tags",
    indices = [Index(value = ["name", "category"], unique = true)]
)
data class Tag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val category: TagCategory? = null,            // null => uživatelský (volný) tag
    val isDefault: Boolean = false,               // zda je z naší výchozí sady
    val isActive: Boolean = true                  // lze „skrýt“ systémové tagy
)

@Entity(
    tableName = "activity_tag_join",
    primaryKeys = ["activityId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = ActivityItem::class,
            parentColumns = ["id"],
            childColumns = ["activityId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tagId"), Index("activityId")]
)
data class ActivityTagJoin(
    val activityId: Long,
    val tagId: Long
)

// ------------------------------
// DAOs
// ------------------------------

@Dao
interface ActivityDao {
    @Insert
    suspend fun insert(item: ActivityItem): Long

    @Update
    suspend fun update(item: ActivityItem)

    @Query("DELETE FROM activities WHERE id = :id")
    suspend fun deleteById(id: Long)


    @Query("SELECT * FROM activities WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ActivityItem?

    @Query("SELECT * FROM activities WHERE isArchived = 0 ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<ActivityItem>>
    @Query("""
        SELECT a.* 
        FROM activities a
        WHERE a.isArchived = 0
          AND NOT EXISTS (
            SELECT 1
            FROM activity_tag_join j
            INNER JOIN tags t ON t.id = j.tagId
            WHERE j.activityId = a.id
              AND t.category IS NOT NULL   -- jen systémové tagy
              AND t.isActive = 0           -- neaktivní
          )
        ORDER BY a.updatedAt DESC
    """)
    fun getAllHidingInactiveSystemTags(): kotlinx.coroutines.flow.Flow<List<ActivityItem>>


    @Query("""
    SELECT a.*
    FROM activities a
    WHERE a.isArchived = 0
      AND NOT EXISTS (
        SELECT 1
        FROM activity_tag_join j
        INNER JOIN tags t ON t.id = j.tagId
        WHERE j.activityId = a.id
          AND t.category IS NOT NULL
          AND t.isActive = 0
      )
    ORDER BY RANDOM()
    LIMIT 1
""")
    suspend fun getRandomAny(): ActivityItem?


    // Random výběr s OR uvnitř kategorií a AND mezi kategoriemi + povinné user tagy (všechny)
    @Query(
        """
    SELECT a.* FROM activities a
    WHERE a.isArchived = 0
      AND NOT EXISTS (
        SELECT 1
        FROM activity_tag_join j
        INNER JOIN tags t ON t.id = j.tagId
        WHERE j.activityId = a.id
          AND t.category IS NOT NULL
          AND t.isActive = 0
      )
      AND (:placeSize = 0 OR EXISTS (
            SELECT 1 FROM activity_tag_join j 
            WHERE j.activityId = a.id AND j.tagId IN (:placeIds)
      ))
      AND (:companySize = 0 OR EXISTS (
            SELECT 1 FROM activity_tag_join j 
            WHERE j.activityId = a.id AND j.tagId IN (:companyIds)
      ))
      AND (:durationSize = 0 OR EXISTS (
            SELECT 1 FROM activity_tag_join j 
            WHERE j.activityId = a.id AND j.tagId IN (:durationIds)
      ))
      AND (:mustSize = 0 OR (
            SELECT COUNT(DISTINCT j.tagId) 
            FROM activity_tag_join j 
            WHERE j.activityId = a.id AND j.tagId IN (:mustHaveIds)
      ) = :mustSize)
    ORDER BY RANDOM()
    LIMIT 1
    """
    )
    suspend fun getRandomFiltered(
        placeIds: List<Long>,
        placeSize: Int,
        companyIds: List<Long>,
        companySize: Int,
        durationIds: List<Long>,
        durationSize: Int,
        mustHaveIds: List<Long>,
        mustSize: Int
    ): ActivityItem?

}

@Dao
interface TagDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: Tag): Long

    @Update
    suspend fun update(tag: Tag)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM tags WHERE isActive = 1 ORDER BY category IS NULL, category, name COLLATE NOCASE")
    fun getAllActive(): Flow<List<Tag>>

    @Query("SELECT * FROM tags ORDER BY category IS NULL, category, name COLLATE NOCASE")
    fun getAllAnyStatus(): Flow<List<Tag>>

    @Query("SELECT * FROM tags WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<Tag>

    @Query("SELECT * FROM tags WHERE category = :category AND isActive = 1 ORDER BY name COLLATE NOCASE")
    suspend fun getActiveByCategory(category: TagCategory): List<Tag>

    @Query("SELECT * FROM tags WHERE category IS NULL AND isActive = 1 ORDER BY name COLLATE NOCASE")
    fun getAllActiveUserTags(): Flow<List<Tag>>
    @Query("""
    SELECT * FROM tags
    WHERE category IS NOT NULL     -- systémové tagy
      AND isActive = 1
    ORDER BY category, name COLLATE NOCASE
""")
    fun observeActiveSystemTags(): kotlinx.coroutines.flow.Flow<List<Tag>>

}

@Dao
interface JoinDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTagToActivity(join: ActivityTagJoin)

    @Query("DELETE FROM activity_tag_join WHERE activityId = :activityId AND tagId = :tagId")
    suspend fun removeTagFromActivity(activityId: Long, tagId: Long)

    @Query("DELETE FROM activity_tag_join WHERE activityId = :activityId")
    suspend fun clearTagsForActivity(activityId: Long)

    @Query(
        """
        SELECT t.* FROM tags t 
        INNER JOIN activity_tag_join j ON j.tagId = t.id
        WHERE j.activityId = :activityId
        """
    )
    suspend fun getTagsForActivity(activityId: Long): List<Tag>
}

// ------------------------------
// Database
// ------------------------------

@Database(
    entities = [ActivityItem::class, Tag::class, ActivityTagJoin::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun activityDao(): ActivityDao
    abstract fun tagDao(): TagDao
    abstract fun joinDao(): JoinDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "getbusy.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}

class Converters {
    @TypeConverter
    fun toCategory(value: String?): TagCategory? = value?.let { TagCategory.valueOf(it) }

    @TypeConverter
    fun fromCategory(category: TagCategory?): String? = category?.name
}
