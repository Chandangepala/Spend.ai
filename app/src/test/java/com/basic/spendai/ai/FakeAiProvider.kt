package com.basic.spendai.ai

/** Returns canned replies in order (null = model failed), and records every prompt it was sent. */
class FakeAiProvider(private vararg val replies: String?) : AiProvider {
    override val tier = AiTier.NANO
    val prompts = mutableListOf<String>()

    override suspend fun status(): AiStatus = AiStatus.Available

    override suspend fun generate(prompt: String, maxTokens: Int): String? {
        prompts += prompt
        return replies.getOrNull(prompts.size - 1)
    }
}
