package com.bytedance.ads_bytedance.ai.api

import com.bytedance.ads_bytedance.ai.model.AiGeneratedContent
import com.bytedance.ads_bytedance.data.model.Tag
import com.bytedance.ads_bytedance.data.model.TagCategory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * AI 响应解析器
 *
 * 职责：
 * 1. 从大模型返回的 content 字符串中提取 JSON
 * 2. 解析 summary + tags 字段
 * 3. 校验标签类别合法性
 * 4. 解析失败时返回降级静态内容
 *
 * ## 容错策略
 * - 大模型可能返回 markdown 包裹的 JSON（```json ... ```）→ 自动提取
 * - 标签 category 非法 → 默认 CATEGORY
 * - summary 缺失 → 使用广告描述的前两句作为降级摘要
 * - 整体解析失败 → 返回 null，由调用方使用预置 fallbackSummary/fallbackTags
 */
object AiResponseParser {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    // ── 允许的类别值 ──
    private val VALID_CATEGORIES = TagCategory.entries.map { it.name.lowercase() }.toSet()

    /**
     * 解析大模型返回的 content 字符串
     *
     * @param rawContent 大模型返回的原始文本（可能含 markdown 包装）
     * @return 解析成功的 [AiGeneratedContent]；失败返回 null
     */
    fun parse(rawContent: String): AiGeneratedContent? {
        return try {
            // 步骤 1：提取 JSON（处理 markdown 代码块包装）
            val jsonStr = extractJson(rawContent)

            // 步骤 2：解析 JSON 对象
            val obj = json.parseToJsonElement(jsonStr).jsonObject

            // 步骤 3：提取 summary（必填字段，缺失则失败）
            val summary = obj["summary"]?.jsonPrimitive?.content?.trim()
            if (summary.isNullOrBlank()) {
                // 尝试 "摘要" 中文 key（兼容部分模型行为）
                val altSummary = obj["摘要"]?.jsonPrimitive?.content?.trim()
                if (altSummary.isNullOrBlank()) return null
                AiGeneratedContent(
                    summary = altSummary.take(80),
                    tags = parseTags(obj)
                )
            } else {
                AiGeneratedContent(
                    summary = summary.take(80),
                    tags = parseTags(obj)
                )
            }
        } catch (e: Exception) {
            null // 任何异常都降级
        }
    }

    /**
     * 从大模型响应中提取 JSON 字符串
     *
     * 处理以下情况：
     * - 直接返回 JSON：`{"summary": "...", "tags": [...]}`
     * - markdown 代码块包装：```json\n{...}\n```
     * - 前后有说明文字：`好的，以下是分析结果：\n{...}`
     */
    private fun extractJson(raw: String): String {
        var content = raw.trim()

        // 尝试匹配 markdown 代码块
        val codeBlockRegex = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.DOT_MATCHES_ALL)
        val match = codeBlockRegex.find(content)
        if (match != null) {
            content = match.groupValues[1].trim()
        }

        // 尝试找到第一个 { 和最后一个 }
        val firstBrace = content.indexOf('{')
        val lastBrace = content.lastIndexOf('}')
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            content = content.substring(firstBrace, lastBrace + 1)
        }

        return content
    }

    /**
     * 解析 tags 数组 + 校验类别合法性
     */
    private fun parseTags(obj: JsonObject): List<Tag> {
        return try {
            val tagsArray = obj["tags"]?.jsonArray
                ?: obj["标签"]?.jsonArray  // 兼容中文 key
                ?: return emptyList()

            tagsArray.mapNotNull { element ->
                val tagObj = element.jsonObject
                val name = tagObj["name"]?.jsonPrimitive?.content?.trim()
                    ?: tagObj["Name"]?.jsonPrimitive?.content?.trim()
                    ?: return@mapNotNull null

                val categoryStr = tagObj["category"]?.jsonPrimitive?.content?.trim()
                    ?: tagObj["Category"]?.jsonPrimitive?.content?.trim()
                    ?: "category"

                // 校验类别
                val category = parseCategory(categoryStr)
                Tag(name = name.take(20), category = category)
            }.take(5) // 最多 5 个标签
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 解析标签类别（大小写不敏感，非法值回退到 CATEGORY）
     */
    private fun parseCategory(raw: String): TagCategory {
        val normalized = raw.trim().lowercase()
        return if (normalized in VALID_CATEGORIES) {
            TagCategory.entries.first { it.name.lowercase() == normalized }
        } else {
            TagCategory.CATEGORY
        }
    }

    // ═══════════════════════════════════════════════════════
    // 降级静态内容（API 完全不可用时的兜底）
    // ═══════════════════════════════════════════════════════

    /**
     * 基于广告描述生成降级静态摘要
     *
     * 策略：取描述的前 1-2 句（最多 80 字）作为摘要。
     * 这确保即使 AI API 完全不可用，UI 也不会空白。
     */
    fun fallbackSummary(description: String): String {
        if (description.isBlank()) return "暂无摘要"

        val firstSentence = description
            .split(Regex("[。.!！?？\n]"))
            .firstOrNull { it.trim().isNotEmpty() }
            ?.trim()
            ?: description.take(40)

        return firstSentence.take(80)
    }

    /**
     * 基于已有标签生成降级标签列表（直接使用 Mock 数据中的静态标签）
     */
    fun fallbackTags(existingTags: List<Tag>): List<Tag> {
        return existingTags.take(5)
    }
}
