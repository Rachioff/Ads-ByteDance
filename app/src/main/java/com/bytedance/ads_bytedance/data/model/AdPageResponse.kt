package com.bytedance.ads_bytedance.data.model

import kotlinx.serialization.Serializable

/**
 * 分页广告列表响应
 *
 * JSON 结构的 Kotlin 映射，对应 assets/mock/*.json 的顶层格式，
 * 也是 RemoteDataSource API 返回的标准分页响应体。
 *
 * @param page 当前页码（从 1 开始）
 * @param totalPages 总页数
 * @param pageSize 每页条数
 * @param items 当前页广告列表（AdItem 密封类多态反序列化）
 */
@Serializable
data class AdPageResponse(
    val page: Int,
    val totalPages: Int,
    val pageSize: Int,
    val items: List<AdItem>
)
