package com.bytedance.ads_bytedance.ai.api

import com.bytedance.ads_bytedance.ai.model.AiMessage
import com.bytedance.ads_bytedance.data.model.AdItem

/**
 * AI Prompt 构造器
 *
 * 将广告信息转换为大模型可理解的 System Prompt + User Message。
 *
 * ## System Prompt 设计要点
 * - 角色设定：广告内容分析助手
 * - 输出约束：严格 JSON 格式，包含 summary（1-2 句 <80 字）+ tags（3-5 个含类别）
 * - 禁止输出 markdown 包装、额外解释文字
 *
 * ## 降级说明
 * 若大模型返回非 JSON 或格式不符，由 [AiResponseParser] 兜底为静态内容。
 */
object AiPromptBuilder {

    // ── System Prompt（角色 + 输出格式约束）──

    private val SYSTEM_PROMPT = """
你是一个广告内容分析助手。给定广告信息，请生成：
1. 一个简洁的摘要（1-2句话，不超过80字），概括广告的核心卖点和吸引用户的亮点
2. 3-5个智能标签，每个标签标注类别（category=品类、style=风格、audience=受众、scene=场景）

你必须严格按照以下 JSON 格式返回，不要包含任何其他文本、markdown 标记或解释：
{
  "summary": "简洁的广告摘要",
  "tags": [
    {"name": "标签名", "category": "category"},
    {"name": "标签名", "category": "style"},
    {"name": "标签名", "category": "audience"}
  ]
}

标签类别说明：
- category: 商品/服务所属品类（如 美妆、数码、服饰、食品、家居）
- style: 视觉/品牌风格（如 简约、潮流、复古、轻奢）
- audience: 目标用户群体（如 学生党、职场人、宝妈、运动爱好者）
- scene: 使用/消费场景（如 通勤、居家、户外、聚会）
""".trimIndent()

    // ── User Message 模板 ──

    private val USER_MESSAGE_TEMPLATE = """
广告标题：%s
广告描述：%s
广告主：%s
广告类型：%s

请为以上广告生成摘要和智能标签。
""".trimIndent()

    // ═══════════════════════════════════════════════════════
    // 公共 API
    // ═══════════════════════════════════════════════════════

    /**
     * 从广告信息构造 System Prompt + User Message
     *
     * @param ad 广告数据
     * @return System Prompt 和 User Message 各一条的消息列表
     */
    fun buildMessages(ad: AdItem): List<AiMessage> {
        return listOf(
            AiMessage(role = "system", content = SYSTEM_PROMPT),
            AiMessage(role = "user", content = buildUserMessage(ad))
        )
    }

    /**
     * 构造仅含 User Message 的搜索意图解析 Prompt（Day 7 使用）
     *
     * @param query 用户自然语言查询
     * @return 含 System Prompt + User Message 的消息列表
     */
    fun buildSearchMessages(query: String): List<AiMessage> {
        val searchSystemPrompt = """
你是一个广告搜索意图解析助手。将用户的自然语言查询转换为结构化搜索条件。
只返回 JSON，不要包含任何其他文本：
{
  "categories": ["品类1"],
  "audiences": ["受众1"],
  "styles": ["风格1"],
  "scenes": ["场景1"],
  "priceRange": {"min": 0, "max": -1},
  "keywords": ["关键词1", "关键词2"]
}
max=-1 表示不限价。
""".trimIndent()

        return listOf(
            AiMessage(role = "system", content = searchSystemPrompt),
            AiMessage(role = "user", content = "请解析以下搜索意图：$query")
        )
    }

    // ═══════════════════════════════════════════════════════
    // 内部方法
    // ═══════════════════════════════════════════════════════

    /**
     * 根据广告信息构造 User Message
     */
    private fun buildUserMessage(ad: AdItem): String {
        val typeName = when (ad) {
            is AdItem.LargeImageAd -> "大图广告"
            is AdItem.SmallImageAd -> "小图广告"
            is AdItem.VideoAd -> "视频广告"
        }
        return USER_MESSAGE_TEMPLATE.format(
            ad.title,
            ad.description.ifBlank { "(无描述)" },
            ad.advertiserName,
            typeName
        )
    }
}
