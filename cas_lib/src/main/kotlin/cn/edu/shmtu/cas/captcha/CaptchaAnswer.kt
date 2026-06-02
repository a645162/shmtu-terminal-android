package cn.edu.shmtu.cas.captcha

enum class CaptchaAnswerKind {
    EXPRESSION,  // 算式如 "12+34="
    ANSWER       // 已是最终答案
}

data class CaptchaAnswer(
    val value: String,
    val kind: CaptchaAnswerKind = CaptchaAnswerKind.EXPRESSION
) {
    /**
     * 将 EXPRESSION 类型转换为 ANSWER 类型。
     * 例如 "12+34=" → "46" (ANSWER)
     * 如果已经是 ANSWER 或无法解析表达式，则返回自身。
     */
    fun intoFinalAnswer(): CaptchaAnswer {
        if (kind == CaptchaAnswerKind.ANSWER) return this
        val result = Captcha.getExprResultByExprString(value)
        return if (result.isNotEmpty()) CaptchaAnswer(result, CaptchaAnswerKind.ANSWER) else this
    }
}
