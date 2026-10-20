package com.example.helper.studentfeedback.seed

import com.example.helper.coredata.entity.FeedbackTag

/**
 * Placeholder preset tags (student-feedback card: "editable placeholders
 * until Phase 0 supplies the client's real tags"). IDs are fixed so seeding
 * is idempotent and teacher edits survive — the labels here are examples,
 * not the client's vocabulary.
 */
object SeedTags {
    val ALL: List<FeedbackTag> = listOf(
        seed("seed-tag-great-day", "Great day", 1),
        seed("seed-tag-helpful", "Helpful in class", 2),
        seed("seed-tag-kind", "Kind to others", 3),
        seed("seed-tag-homework", "Homework not done", 4),
        seed("seed-tag-attention", "Needs attention", 5),
    )

    private fun seed(id: String, label: String, sortOrder: Int) = FeedbackTag(
        id = id,
        label = label,
        sortOrder = sortOrder,
        createdAt = 0L,
        updatedAt = 0L,
    )
}
