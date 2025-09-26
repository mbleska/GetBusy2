package com.example.getbusy.data

import kotlinx.coroutines.flow.Flow

class ActivityRepository(
    private val activityDao: ActivityDao,
    private val tagDao: TagDao,
    private val joinDao: JoinDao
) {
    // -------------------
    // Aktivity
    // -------------------

    fun getAllActivities(): Flow<List<ActivityItem>> = activityDao.getAll()
    fun getAllActivitiesHidingInactiveSystemTags(): Flow<List<ActivityItem>> =
        activityDao.getAllHidingInactiveSystemTags()

    suspend fun getActivityById(id: Long): ActivityItem? = activityDao.getById(id)

    suspend fun insertActivity(item: ActivityItem, tagIds: List<Long>): Long {
        val newId = activityDao.insert(item)
        joinDao.clearTagsForActivity(newId)
        tagIds.forEach { joinDao.addTagToActivity(ActivityTagJoin(newId, it)) }
        return newId
    }

    suspend fun updateActivity(item: ActivityItem, tagIds: List<Long>) {
        activityDao.update(item.copy(updatedAt = System.currentTimeMillis()))
        joinDao.clearTagsForActivity(item.id)
        tagIds.forEach { joinDao.addTagToActivity(ActivityTagJoin(item.id, it)) }
    }

    suspend fun deleteActivity(id: Long) = activityDao.deleteById(id)

    suspend fun getTagsForActivity(id: Long): List<Tag> = joinDao.getTagsForActivity(id)

    // -------------------
    // Tagy
    // -------------------

    fun getAllActiveTags(): Flow<List<Tag>> = tagDao.getAllActive()

    fun getAllTagsAnyStatus(): Flow<List<Tag>> = tagDao.getAllAnyStatus()

    suspend fun insertTag(tag: Tag): Long = tagDao.insert(tag)

    suspend fun updateTag(tag: Tag) = tagDao.update(tag)

    suspend fun deleteTag(id: Long) = tagDao.deleteById(id)

    suspend fun getTagsByIds(ids: List<Long>): List<Tag> = tagDao.getByIds(ids)

    // -------------------
    // Random výběr
    // -------------------

    suspend fun getRandomAny(): ActivityItem? = activityDao.getRandomAny()

    suspend fun getRandomFiltered(
        placeIds: List<Long>,
        companyIds: List<Long>,
        durationIds: List<Long>,
        mustHaveIds: List<Long>
    ): ActivityItem? {
        return activityDao.getRandomFiltered(
            placeIds = placeIds,
            placeSize = placeIds.size,
            companyIds = companyIds,
            companySize = companyIds.size,
            durationIds = durationIds,
            durationSize = durationIds.size,
            mustHaveIds = mustHaveIds,
            mustSize = mustHaveIds.size
        )
    }
}
