package com.mateof.tfm.data.api

import com.mateof.tfm.core.ApiEnvelope
import com.mateof.tfm.data.model.CreateStrmRequest
import com.mateof.tfm.data.model.SharedCollectionDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface SharesApi {

    @GET("api/v1/shares")
    suspend fun list(
        @Query("filter") filter: String? = null,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 100
    ): ApiEnvelope<List<SharedCollectionDto>>

    @DELETE("api/v1/shares/{id}")
    suspend fun delete(@Path("id") id: String): ApiEnvelope<Boolean>

    /**
     * Builds .strm files for a channel folder.
     *
     * With [CreateStrmRequest.destinationFolder] the files are written under
     * the server local root and the response echoes that folder. Without it,
     * the response carries a relative URL to a downloadable ZIP archive.
     */
    @POST("api/v1/shares/strm")
    suspend fun createStrm(
        @Query("channelId") channelId: String,
        @Body body: CreateStrmRequest
    ): ApiEnvelope<String>
}
