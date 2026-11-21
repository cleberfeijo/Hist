package br.uff.hist.core.model

import java.time.Instant

enum class ModerationStatus { PENDING, APPROVED, REJECTED }

enum class Role { USER, MODERATOR, ADMIN }

data class GeoPoint(val latitude: Double, val longitude: Double)

data class Source(val name: String, val url: String?, val license: String?)

data class User(
    val id: String,
    val name: String,
    val lastName: String,
    val email: String,
    val roles: Set<Role>,
) {
    val isModerator: Boolean get() = Role.MODERATOR in roles || Role.ADMIN in roles
}

data class Landmark(
    val id: String,
    val name: String,
    val description: String?,
    val location: GeoPoint,
    val category: Category,
    val city: String?,
    val state: String?,
    val imageUrl: String?,
    val source: Source?,
    val status: ModerationStatus,
    val rejectionReason: String?,
    val curiosityCount: Int,
    val createdBy: String?,
    val createdAt: Instant? = null,
)

data class Curiosity(
    val id: String,
    val landmarkId: String,
    val landmarkName: String?,
    val text: String,
    val authorName: String?,
    val source: Source?,
    val status: ModerationStatus,
    val rejectionReason: String?,
    val createdAt: Instant,
)

data class Page<T>(val items: List<T>, val total: Int, val page: Int, val pageSize: Int) {
    val hasMore: Boolean = page * pageSize < total
}
