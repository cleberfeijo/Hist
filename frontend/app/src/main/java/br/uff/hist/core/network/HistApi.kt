package br.uff.hist.core.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface HistApi {
    @FormUrlEncoded
    @POST("auth/login")
    suspend fun login(@Field("username") username: String, @Field("password") password: String): TokenResponseDto

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): TokenResponseDto

    /** O token vai explícito: o logout roda depois de a sessão local ser apagada (o interceptor já não o conhece). */
    @POST("auth/logout")
    suspend fun logout(@Header("Authorization") authorization: String): Response<Unit>

    @GET("users/me")
    suspend fun me(): UserDto

    @PATCH("users/me")
    suspend fun updateMe(@Body body: UpdateMeRequest): UserDto

    @POST("users/me/password")
    suspend fun changePassword(@Body body: ChangePasswordRequest): TokenResponseDto

    @HTTP(method = "DELETE", path = "users/me", hasBody = true)
    suspend fun deleteMe(@Body body: PasswordBody): Response<Unit>

    @GET("users/me/landmarks")
    suspend fun myLandmarks(
        @Query("status") status: String?,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
    ): PageDto<LandmarkDto>

    @GET("users/me/curiosities")
    suspend fun myCuriosities(
        @Query("status") status: String?,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
    ): PageDto<CuriosityDto>

    @GET("landmarks/nearby")
    suspend fun nearby(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("radius") radius: Int,
        @Query("limit") limit: Int,
    ): List<LandmarkDto>

    @GET("landmarks/{id}")
    suspend fun landmark(@Path("id") id: String): LandmarkDto

    @GET("landmarks/{id}/curiosities")
    suspend fun landmarkCuriosities(
        @Path("id") id: String,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
    ): PageDto<CuriosityDto>

    @POST("landmarks")
    suspend fun createLandmark(@Body body: NewLandmarkRequest): LandmarkDto

    @POST("landmarks/{id}/curiosities")
    suspend fun createCuriosity(@Path("id") id: String, @Body body: NewCuriosityRequest): CuriosityDto

    @DELETE("landmarks/{id}")
    suspend fun deleteLandmark(@Path("id") id: String): Response<Unit>

    @GET("curiosities/{id}")
    suspend fun curiosity(@Path("id") id: String): CuriosityDto

    @DELETE("curiosities/{id}")
    suspend fun deleteCuriosity(@Path("id") id: String): Response<Unit>

    @GET("moderation/landmarks")
    suspend fun moderationLandmarks(
        @Query("status") status: String,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
    ): PageDto<LandmarkDto>

    @POST("moderation/landmarks/{id}/approve")
    suspend fun approveLandmark(@Path("id") id: String): LandmarkDto

    @POST("moderation/landmarks/{id}/reject")
    suspend fun rejectLandmark(@Path("id") id: String, @Body body: RejectRequest): LandmarkDto

    @GET("moderation/curiosities")
    suspend fun moderationCuriosities(
        @Query("status") status: String,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
    ): PageDto<CuriosityDto>

    @POST("moderation/curiosities/{id}/approve")
    suspend fun approveCuriosity(@Path("id") id: String): CuriosityDto

    @POST("moderation/curiosities/{id}/reject")
    suspend fun rejectCuriosity(@Path("id") id: String, @Body body: RejectRequest): CuriosityDto
}
