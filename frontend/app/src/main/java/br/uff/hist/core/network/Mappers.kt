package br.uff.hist.core.network

import br.uff.hist.core.model.Category
import br.uff.hist.core.model.Curiosity
import br.uff.hist.core.model.GeoPoint
import br.uff.hist.core.model.Landmark
import br.uff.hist.core.model.ModerationStatus
import br.uff.hist.core.model.Page
import br.uff.hist.core.model.Role
import br.uff.hist.core.model.Source
import br.uff.hist.core.model.User
import java.time.OffsetDateTime

private fun String.toStatus(): ModerationStatus =
    ModerationStatus.entries.firstOrNull { it.name.equals(this, ignoreCase = true) }
        ?: ModerationStatus.PENDING

private fun SourceDto.toDomain() = Source(name = name, url = url, license = license)

fun UserDto.toDomain() = User(
    id = id,
    name = name,
    lastName = lastName,
    email = email,
    roles = roles.mapNotNull { r -> Role.entries.firstOrNull { it.name.equals(r, ignoreCase = true) } }.toSet(),
)

fun LandmarkDto.toDomain() = Landmark(
    id = id,
    name = name,
    description = description,
    location = GeoPoint(latitude, longitude),
    category = Category.fromId(category),
    city = city,
    state = state,
    imageUrl = imageUrl,
    source = source?.toDomain(),
    status = status.toStatus(),
    rejectionReason = rejectionReason,
    curiosityCount = curiosityCount,
    createdBy = createdBy,
)

fun CuriosityDto.toDomain() = Curiosity(
    id = id,
    landmarkId = landmarkId,
    landmarkName = landmarkName,
    text = text,
    authorName = author?.name,
    source = source?.toDomain(),
    status = status.toStatus(),
    rejectionReason = rejectionReason,
    createdAt = OffsetDateTime.parse(createdAt).toInstant(),
)

fun <D, T> PageDto<D>.toDomain(map: (D) -> T): Page<T> =
    Page(items = items.map(map), total = total, page = page, pageSize = pageSize)
