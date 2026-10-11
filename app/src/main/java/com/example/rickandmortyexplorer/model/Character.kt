package com.example.rickandmortyexplorer.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(
    val info: Info,
    val results: List<T>
)

@Serializable
data class Info(
    val count: Int,
    val pages: Int,
    val next: String?,
    val prev: String?
)

@Serializable
data class Character(
    val id: Int,
    val name: String,
    val status: String,
    val species: String,
    val type: String,
    val gender: String,
    val origin: LocationRef,
    val location: LocationRef,
    val image: String,
    val episode: List<String>,
)

@Serializable
data class LocationRef(
    val name: String,
    val url: String
)

@Serializable
data class Episode(
    val id: Int,
    val name: String,
    @SerialName("air_date") val airDate: String,
    val episode: String
)

@Serializable
data class Location(
    val id: Int,
    val name: String,
    val type: String,
    val dimension: String
)

