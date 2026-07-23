package com.mateof.tfm.data.api

import com.mateof.tfm.core.ApiEnvelope
import com.mateof.tfm.data.model.ApiFileDto
import com.mateof.tfm.data.model.AddTrackRequest
import com.mateof.tfm.data.model.CreateFolderRequest
import com.mateof.tfm.data.model.CreatePlaylistRequest
import com.mateof.tfm.data.model.DirectorySizeDto
import com.mateof.tfm.data.model.DownloadMessagesRequest
import com.mateof.tfm.data.model.FolderContentsDto
import com.mateof.tfm.data.model.LocalDeleteRequest
import com.mateof.tfm.data.model.LocalRenameRequest
import com.mateof.tfm.data.model.OperationResultDto
import com.mateof.tfm.data.model.PersistedTransferDto
import com.mateof.tfm.data.model.PlaylistDto
import com.mateof.tfm.data.model.SpeedHistoryDto
import com.mateof.tfm.data.model.StartDownloadsRequest
import com.mateof.tfm.data.model.StartUploadsRequest
import com.mateof.tfm.data.model.TransferDto
import com.mateof.tfm.data.model.TransferSummaryDto
import com.mateof.tfm.data.model.TransfersSnapshotDto
import com.mateof.tfm.data.model.UpdatePlaylistRequest
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

interface TransfersApi {

    @GET("api/v1/transfers")
    suspend fun snapshot(): ApiEnvelope<TransfersSnapshotDto>

    @GET("api/v1/transfers/summary")
    suspend fun summary(): ApiEnvelope<TransferSummaryDto>

    @GET("api/v1/transfers/speed-history")
    suspend fun speedHistory(): ApiEnvelope<SpeedHistoryDto>

    @GET("api/v1/transfers/{id}")
    suspend fun get(@Path("id") id: String): ApiEnvelope<TransferDto>

    @POST("api/v1/transfers/downloads")
    suspend fun startDownloads(@Body body: StartDownloadsRequest): ApiEnvelope<OperationResultDto>

    @POST("api/v1/transfers/uploads")
    suspend fun startUploads(@Body body: StartUploadsRequest): ApiEnvelope<OperationResultDto>

    @POST("api/v1/transfers/messages")
    suspend fun downloadMessages(@Body body: DownloadMessagesRequest): ApiEnvelope<OperationResultDto>

    @POST("api/v1/transfers/downloads/pause")
    suspend fun pauseDownloads(): ApiEnvelope<TransferSummaryDto>

    @POST("api/v1/transfers/downloads/resume")
    suspend fun resumeDownloads(): ApiEnvelope<TransferSummaryDto>

    @POST("api/v1/transfers/downloads/stop")
    suspend fun stopDownloads(): ApiEnvelope<TransferSummaryDto>

    @POST("api/v1/transfers/{id}/pause")
    suspend fun pause(@Path("id") id: String): ApiEnvelope<Boolean>

    @POST("api/v1/transfers/{id}/cancel")
    suspend fun cancel(@Path("id") id: String): ApiEnvelope<Boolean>

    @POST("api/v1/transfers/{id}/retry")
    suspend fun retry(@Path("id") id: String): ApiEnvelope<Boolean>

    @POST("api/v1/transfers/clear")
    suspend fun clear(@Query("scope") scope: String = "all"): ApiEnvelope<Boolean>

    @POST("api/v1/transfers/queue/clear")
    suspend fun clearQueue(@Query("scope") scope: String = "all"): ApiEnvelope<Boolean>

    @GET("api/v1/transfers/persisted")
    suspend fun persisted(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 100
    ): ApiEnvelope<List<PersistedTransferDto>>

    @DELETE("api/v1/transfers/persisted/{internalId}")
    suspend fun deletePersisted(@Path("internalId") internalId: String): ApiEnvelope<Boolean>

    @DELETE("api/v1/transfers/persisted")
    suspend fun deleteAllPersisted(): ApiEnvelope<Boolean>
}

interface LocalApi {

    @GET("api/v1/local")
    suspend fun browse(
        @Query("path") path: String? = null,
        @Query("filter") filter: String? = null,
        @Query("search") search: String? = null,
        @Query("filesOnly") filesOnly: Boolean = false,
        @Query("sortBy") sortBy: String? = null,
        @Query("sortDescending") sortDescending: Boolean = false,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 100
    ): ApiEnvelope<FolderContentsDto>

    @GET("api/v1/local/info")
    suspend fun info(@Query("path") path: String): ApiEnvelope<ApiFileDto>

    @GET("api/v1/local/size")
    suspend fun size(@Query("path") path: String? = null): ApiEnvelope<DirectorySizeDto>

    @POST("api/v1/local/folders")
    suspend fun createFolder(@Body body: CreateFolderRequest): ApiEnvelope<ApiFileDto>

    @POST("api/v1/local/rename")
    suspend fun rename(@Body body: LocalRenameRequest): ApiEnvelope<ApiFileDto>

    @POST("api/v1/local/delete")
    suspend fun delete(@Body body: LocalDeleteRequest): ApiEnvelope<OperationResultDto>

    @Multipart
    @POST("api/v1/local/upload")
    suspend fun upload(
        @Part file: MultipartBody.Part,
        @Part("path") path: RequestBody? = null
    ): ApiEnvelope<ApiFileDto>

    @POST("api/v1/local/cache/clear")
    suspend fun clearCache(): ApiEnvelope<Boolean>
}

interface PlaylistsApi {

    @GET("api/v1/playlists")
    suspend fun list(
        @Query("sortBy") sortBy: String? = null,
        @Query("sortDescending") sortDescending: Boolean = false,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 100
    ): ApiEnvelope<List<PlaylistDto>>

    @GET("api/v1/playlists/{id}")
    suspend fun get(@Path("id") id: String): ApiEnvelope<PlaylistDto>

    @POST("api/v1/playlists")
    suspend fun create(@Body body: CreatePlaylistRequest): ApiEnvelope<PlaylistDto>

    @PUT("api/v1/playlists/{id}")
    suspend fun update(@Path("id") id: String, @Body body: UpdatePlaylistRequest): ApiEnvelope<PlaylistDto>

    @DELETE("api/v1/playlists/{id}")
    suspend fun delete(@Path("id") id: String): ApiEnvelope<Boolean>

    @POST("api/v1/playlists/{id}/tracks")
    suspend fun addTrack(@Path("id") id: String, @Body body: AddTrackRequest): ApiEnvelope<PlaylistDto>

    @DELETE("api/v1/playlists/{id}/tracks/{fileId}")
    suspend fun removeTrack(@Path("id") id: String, @Path("fileId") fileId: String): ApiEnvelope<PlaylistDto>

    @PUT("api/v1/playlists/{id}/tracks/order")
    suspend fun reorder(@Path("id") id: String, @Body order: List<String>): ApiEnvelope<PlaylistDto>

    @POST("api/v1/playlists/{id}/download")
    suspend fun download(
        @Path("id") id: String,
        @Query("destinationFolder") destinationFolder: String? = null
    ): ApiEnvelope<OperationResultDto>
}
