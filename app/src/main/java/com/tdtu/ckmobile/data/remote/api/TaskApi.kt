package com.tdtu.ckmobile.data.remote.api

import com.tdtu.ckmobile.data.remote.model.ChangesResponse
import com.tdtu.ckmobile.data.remote.model.CreateTaskRequest
import com.tdtu.ckmobile.data.remote.model.TaskDto
import com.tdtu.ckmobile.data.remote.model.UpdateTaskRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface TaskApi {
    @GET("api/tasks")
    suspend fun getAll(): List<TaskDto>

    @GET("api/tasks/changes")
    suspend fun getChanges(
        @Query("since") since: Long,
        @Query("limit") limit: Int = 100
    ): Response<ChangesResponse>

    @POST("api/tasks")
    suspend fun create(@Body body: CreateTaskRequest): Response<TaskDto>

    @PUT("api/tasks/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body body: UpdateTaskRequest
    ): Response<TaskDto>

    @DELETE("api/tasks/{id}")
    suspend fun delete(
        @Path("id") id: String,
        @Query("baseVersion") baseVersion: Int
    ): Response<TaskDto>
}
