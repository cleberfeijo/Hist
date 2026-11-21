package br.uff.hist.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val HistJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

@Serializable
data class SourceDto(
    val name: String,
    val url: String? = null,
    val license: String? = null,
)

@Serializable
data class UserDto(
    val id: String,
    val name: String,
    @SerialName("last_name") val lastName: String,
    val email: String,
    val roles: List<String> = emptyList(),
)

@Serializable
data class TokenResponseDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "bearer",
    @SerialName("expires_in") val expiresIn: Long = 0,
    val user: UserDto,
)

/** Também recebe os itens de `/landmarks/nearby` (`distance_m` e `chat_available` são ignorados). */
@Serializable
data class LandmarkDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val latitude: Double,
    val longitude: Double,
    val category: String? = null,
    val city: String? = null,
    val state: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    val source: SourceDto? = null,
    val status: String,
    @SerialName("rejection_reason") val rejectionReason: String? = null,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("curiosity_count") val curiosityCount: Int = 0,
    /** Ausente nas listagens públicas mais antigas; a fila de moderação usa para mostrar a data. */
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class AuthorDto(val id: String, val name: String)

@Serializable
data class CuriosityDto(
    val id: String,
    @SerialName("landmark_id") val landmarkId: String,
    @SerialName("landmark_name") val landmarkName: String? = null,
    val text: String,
    val author: AuthorDto? = null,
    val source: SourceDto? = null,
    val status: String,
    @SerialName("rejection_reason") val rejectionReason: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class PageDto<T>(
    val items: List<T>,
    val total: Int,
    val page: Int,
    @SerialName("page_size") val pageSize: Int,
)

@Serializable
data class ErrorDetailDto(val field: String? = null, val message: String? = null)

@Serializable
data class ErrorBodyDto(
    val code: String,
    val message: String,
    val details: List<ErrorDetailDto> = emptyList(),
)

@Serializable
data class ErrorEnvelopeDto(val error: ErrorBodyDto)

@Serializable
data class RegisterRequest(
    val name: String,
    @SerialName("last_name") val lastName: String,
    val email: String,
    val password: String,
)

@Serializable
data class NewLandmarkRequest(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val description: String? = null,
    val category: String? = null,
    val address: String? = null,
    val city: String? = null,
    val state: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    val curiosity: String? = null,
)

@Serializable
data class NewCuriosityRequest(val text: String)

@Serializable
data class UpdateMeRequest(
    val name: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    val email: String? = null,
    @SerialName("current_password") val currentPassword: String? = null,
)

@Serializable
data class ChangePasswordRequest(
    @SerialName("current_password") val currentPassword: String,
    @SerialName("new_password") val newPassword: String,
)

@Serializable
data class PasswordBody(val password: String)

@Serializable
data class RejectRequest(val reason: String)
