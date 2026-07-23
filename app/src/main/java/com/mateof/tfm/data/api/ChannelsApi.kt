package com.mateof.tfm.data.api

import com.mateof.tfm.core.ApiEnvelope
import com.mateof.tfm.data.model.ApiFileDto
import com.mateof.tfm.data.model.ChannelDto
import com.mateof.tfm.data.model.ChannelFoldersDto
import com.mateof.tfm.data.model.ChannelMessageDto
import com.mateof.tfm.data.model.CopyMoveRequest
import com.mateof.tfm.data.model.CreateChannelRequest
import com.mateof.tfm.data.model.CreateFolderRequest
import com.mateof.tfm.data.model.FolderContentsDto
import com.mateof.tfm.data.model.FolderStatsDto
import com.mateof.tfm.data.model.IdsRequest
import com.mateof.tfm.data.model.InvitationDto
import com.mateof.tfm.data.model.LeaveChannelRequest
import com.mateof.tfm.data.model.OperationResultDto
import com.mateof.tfm.data.model.RefreshChannelRequest
import com.mateof.tfm.data.model.RenameFileRequest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ChannelsApi {

    @GET("api/v1/channels")
    suspend fun list(
        @Query("onlySaved") onlySaved: Boolean = false,
        @Query("favoritesOnly") favoritesOnly: Boolean = false,
        @Query("search") search: String? = null,
        @Query("sortBy") sortBy: String? = null,
        @Query("sortDescending") sortDescending: Boolean = false,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 50
    ): ApiEnvelope<List<ChannelDto>>

    @GET("api/v1/channels/folders")
    suspend fun folders(): ApiEnvelope<ChannelFoldersDto>

    @GET("api/v1/channels/{id}")
    suspend fun details(@Path("id") id: String): ApiEnvelope<ChannelDto>

    @POST("api/v1/channels")
    suspend fun create(@Body body: CreateChannelRequest): ApiEnvelope<ChannelDto>

    @POST("api/v1/channels/{id}/favorite")
    suspend fun addFavorite(@Path("id") id: String): ApiEnvelope<Boolean>

    @DELETE("api/v1/channels/{id}/favorite")
    suspend fun removeFavorite(@Path("id") id: String): ApiEnvelope<Boolean>

    @POST("api/v1/channels/{id}/database")
    suspend fun createDatabase(@Path("id") id: String): ApiEnvelope<Boolean>

    @DELETE("api/v1/channels/{id}/database")
    suspend fun dropDatabase(@Path("id") id: String): ApiEnvelope<Boolean>

    @POST("api/v1/channels/{id}/leave")
    suspend fun leave(@Path("id") id: String, @Body body: LeaveChannelRequest): ApiEnvelope<Boolean>

    @POST("api/v1/channels/{id}/refresh")
    suspend fun refresh(@Path("id") id: String, @Body body: RefreshChannelRequest): ApiEnvelope<Boolean>

    @GET("api/v1/channels/{id}/refresh")
    suspend fun isRefreshing(@Path("id") id: String): ApiEnvelope<Boolean>

    @GET("api/v1/channels/{id}/messages")
    suspend fun messages(
        @Path("id") id: String,
        @Query("limit") limit: Int = 30,
        @Query("offset") offset: Int = 0,
        @Query("onlyMedia") onlyMedia: Boolean = false
    ): ApiEnvelope<List<ChannelMessageDto>>

    @GET("api/v1/channels/{id}/invitation")
    suspend fun invitation(@Path("id") id: String): ApiEnvelope<InvitationDto>

    @POST("api/v1/channels/join")
    suspend fun join(@Query("hash") hash: String): ApiEnvelope<ChannelDto>
}

interface FilesApi {

    @GET("api/v1/channels/{channelId}/files")
    suspend fun browse(
        @Path("channelId") channelId: String,
        @Query("path") path: String? = null,
        @Query("folderId") folderId: String? = null,
        @Query("filter") filter: String? = null,
        @Query("search") search: String? = null,
        @Query("filesOnly") filesOnly: Boolean = false,
        @Query("sortBy") sortBy: String? = null,
        @Query("sortDescending") sortDescending: Boolean = false,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 100
    ): ApiEnvelope<FolderContentsDto>

    @GET("api/v1/channels/{channelId}/files/search")
    suspend fun search(
        @Path("channelId") channelId: String,
        @Query("q") q: String,
        @Query("path") path: String? = null,
        @Query("filter") filter: String? = null,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 100
    ): ApiEnvelope<List<ApiFileDto>>

    @GET("api/v1/channels/{channelId}/files/{fileId}")
    suspend fun get(
        @Path("channelId") channelId: String,
        @Path("fileId") fileId: String
    ): ApiEnvelope<ApiFileDto>

    @GET("api/v1/channels/{channelId}/files/stats")
    suspend fun stats(
        @Path("channelId") channelId: String,
        @Query("path") path: String? = null
    ): ApiEnvelope<FolderStatsDto>

    @POST("api/v1/channels/{channelId}/files/folders")
    suspend fun createFolder(
        @Path("channelId") channelId: String,
        @Body body: CreateFolderRequest
    ): ApiEnvelope<ApiFileDto>

    @PUT("api/v1/channels/{channelId}/files/{fileId}/name")
    suspend fun rename(
        @Path("channelId") channelId: String,
        @Path("fileId") fileId: String,
        @Body body: RenameFileRequest
    ): ApiEnvelope<ApiFileDto>

    @POST("api/v1/channels/{channelId}/files/delete")
    suspend fun delete(
        @Path("channelId") channelId: String,
        @Body body: IdsRequest
    ): ApiEnvelope<OperationResultDto>

    @POST("api/v1/channels/{channelId}/files/copy")
    suspend fun copy(
        @Path("channelId") channelId: String,
        @Body body: CopyMoveRequest
    ): ApiEnvelope<OperationResultDto>

    @POST("api/v1/channels/{channelId}/files/move")
    suspend fun move(
        @Path("channelId") channelId: String,
        @Body body: CopyMoveRequest
    ): ApiEnvelope<OperationResultDto>

    @Multipart
    @POST("api/v1/channels/{channelId}/files/upload")
    suspend fun upload(
        @Path("channelId") channelId: String,
        @Part file: MultipartBody.Part,
        @Part("path") path: RequestBody? = null
    ): ApiEnvelope<OperationResultDto>
}
