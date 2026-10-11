package com.example.rickandmortyexplorer.network

import com.example.rickandmortyexplorer.model.ApiResponse
import com.example.rickandmortyexplorer.model.Character
import com.example.rickandmortyexplorer.model.Episode
import com.example.rickandmortyexplorer.model.Location
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

private const val BASE_URL = "https://rickandmortyapi.com/api/"

private val json = Json { ignoreUnknownKeys = true }

private val retrofit = Retrofit.Builder()
    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
    .baseUrl(BASE_URL)
    .build()

interface RickAndMortyApiService {
    @GET("character")
    suspend fun getCharacters(@Query("name") name: String?): ApiResponse<Character>

    @GET("location")
    suspend fun getLocations(@Query("name") name: String?): ApiResponse<Location>

    @GET("episode")
    suspend fun getEpisodes(@Query("name") name: String?): ApiResponse<Episode>
}

object RickAndMortyApi {
    val retrofitService: RickAndMortyApiService by lazy {
        retrofit.create(RickAndMortyApiService::class.java)
    }
}
