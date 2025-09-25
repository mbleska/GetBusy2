package com.example.getbusy.domain

import com.example.getbusy.data.Tag
import com.example.getbusy.data.TagCategory

data class FilterSpec(
    val place: Set<Long> = emptySet(),
    val company: Set<Long> = emptySet(),
    val duration: Set<Long> = emptySet(),
    val userTags: Set<Long> = emptySet()
) {
    fun isEmpty(): Boolean =
        place.isEmpty() && company.isEmpty() && duration.isEmpty() && userTags.isEmpty()

    fun toRepositoryArgs(): RepositoryArgs = RepositoryArgs(
        placeIds = place.toList(),
        companyIds = company.toList(),
        durationIds = duration.toList(),
        mustHaveIds = userTags.toList()
    )

    data class RepositoryArgs(
        val placeIds: List<Long>,
        val companyIds: List<Long>,
        val durationIds: List<Long>,
        val mustHaveIds: List<Long>
    )

    companion object {
        fun fromTags(selected: List<Tag>): FilterSpec {
            val place = selected.filter { it.category == TagCategory.PLACE }.map { it.id }.toSet()
            val company = selected.filter { it.category == TagCategory.COMPANY }.map { it.id }.toSet()
            val duration = selected.filter { it.category == TagCategory.DURATION }.map { it.id }.toSet()
            val user = selected.filter { it.category == null }.map { it.id }.toSet()
            return FilterSpec(place, company, duration, user)
        }
    }
}
