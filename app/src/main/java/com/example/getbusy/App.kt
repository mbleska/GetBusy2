package com.example.getbusy

import android.app.Application
import android.content.Context
import com.example.getbusy.data.*
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GetBusyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MobileAds.initialize(this)
        AppGraph.init(this)

        // seed výchozích tagů (idempotentně – INSERT IGNORE v DAO)
        CoroutineScope(Dispatchers.IO).launch {
            Seed.seedDefaults()
        }
    }
}

object AppGraph {
    lateinit var db: AppDatabase
        private set
    lateinit var repo: ActivityRepository
        private set

    fun init(context: Context) {
        db = AppDatabase.getInstance(context)
        repo = ActivityRepository(db.activityDao(), db.tagDao(), db.joinDao())
    }
}

private object Seed {
    private val defaults = listOf(
        Tag(name = "doma", category = TagCategory.PLACE, isDefault = true, isActive = true),
        Tag(name = "venku", category = TagCategory.PLACE, isDefault = true, isActive = true),

        Tag(name = "sám", category = TagCategory.COMPANY, isDefault = true, isActive = true),
        Tag(name = "s kamarády", category = TagCategory.COMPANY, isDefault = true, isActive = true),
        Tag(name = "s partnerem", category = TagCategory.COMPANY, isDefault = true, isActive = true),

        Tag(name = "do 30 min", category = TagCategory.DURATION, isDefault = true, isActive = true),
        Tag(name = "30–60 min", category = TagCategory.DURATION, isDefault = true, isActive = true),
        Tag(name = "60+ min", category = TagCategory.DURATION, isDefault = true, isActive = true)
    )

    suspend fun seedDefaults() {
        val tagDao = AppGraph.db.tagDao()
        defaults.forEach { tagDao.insert(it) }
    }
}
