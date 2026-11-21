package com.armanmaurya.internetradio.data.remote.lyrics.kugou

import com.google.gson.annotations.SerializedName

data class KuGouSearchResponse(
    @SerializedName("status") val status: Int? = null,
    @SerializedName("errcode") val errCode: Int? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("candidates") val candidates: List<KuGouCandidate>? = null
)

data class KuGouCandidate(
    @SerializedName("id") val id: String? = null,
    @SerializedName("accesskey") val accessKey: String? = null,
    @SerializedName("song") val song: String? = null,
    @SerializedName("singer") val singer: String? = null,
    @SerializedName("duration") val duration: Long? = null
)

data class KuGouDownloadResponse(
    @SerializedName("status") val status: Int? = null,
    @SerializedName("errcode") val errCode: Int? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("content") val content: String? = null,
    @SerializedName("fmt") val fmt: String? = null
)
