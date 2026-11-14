/*
 * ═══════════════════════════════════════════════════════════════════════════
 * LiquidTheme.kt — 液态玻璃主题（通透高光版）
 * ═══════════════════════════════════════════════════════════════════════════
 * 1. 取消背景遮罩，完美显示自定义壁纸。
 * 2. 玻璃外层极度透明，内层卡片实色托底，保证文字绝对清晰。
 * 3. 关闭浅色模式下的文字阴影，解决发糊重影。
 * ═══════════════════════════════════════════════════════════════════════════
 */
package net.ccbluex.liquidbounce.features.module.modules.render.clickgui

enum class LiquidTheme(val displayName: String) {
    DARK("Dark"),
    LIGHT("Light");

    fun toggle(): LiquidTheme = if (this == DARK) LIGHT else DARK
}

class LiquidTokens(
    val glassBg: Int,
    val glassBorder: Int,
    val glassHeaderBg: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val accent: Int,
    val rowHover: Int,
    val divider: Int,
    val cardHoverBg: Int,
    val cardEnabledBg: Int,
    val cardIdleBg: Int,
    val cardBorder: Int,
    val cardInnerBg: Int,
    val veil: Int,
    val toggleTrackOff: Int,
    val toggleTrackOn: Int,
    val toggleKnob: Int,
    val textShadow: Boolean
)

object LiquidPalette {
    // 深色模式
    val DARK = LiquidTokens(
        glassBg           = 0x35000000,         // 外层玻璃：约 20% 黑，透明
        glassBorder       = 0x60FFFFFF,         // 玻璃边缘白色高光
        glassHeaderBg     = 0x50000000,         // 顶栏：约 30% 黑
        textPrimary       = 0xFFFFFFFF.toInt(),
        textSecondary     = 0xFFB0B0B0.toInt(),
        accent            = 0xFF00D2FF.toInt(),
        rowHover          = 0x20FFFFFF,
        divider           = 0x30FFFFFF,
        cardHoverBg       = 0xFF1A1A22.toInt(), // 悬停实色卡片
        cardEnabledBg     = 0xFF002244.toInt(),
        cardIdleBg        = 0xE6000000.toInt(), // 90% 黑
        cardBorder        = 0x40FFFFFF,
        cardInnerBg       = 0xFF111111.toInt(),
        veil              = 0x00000000,         // 0 遮罩：完美显示背景图
        toggleTrackOff    = 0xFF444444.toInt(),
        toggleTrackOn     = 0xFF00D2FF.toInt(),
        toggleKnob        = 0xFFFFFFFF.toInt(),
        textShadow        = false
    )

    // 浅色模式
    val LIGHT = LiquidTokens(
        glassBg           = 0x30FFFFFF,         // 外层玻璃：约 19% 白，极度通透
        glassBorder       = 0xB0FFFFFF.toInt(), // 玻璃边缘高光
        glassHeaderBg     = 0x50FFFFFF,         // 顶栏：约 31% 白
        textPrimary       = 0xFF000000.toInt(), // 纯黑文字
        textSecondary     = 0xFF333333.toInt(),
        accent            = 0xFF0055CC.toInt(),
        rowHover          = 0x15000000,
        divider           = 0x30000000,
        cardHoverBg       = 0xFFFFFFFF.toInt(), // 悬停纯白
        cardEnabledBg     = 0xFFE0F0FF.toInt(), // 启用浅蓝
        cardIdleBg        = 0xF2FFFFFF.toInt(), // 95% 白，边缘微透，但托得住文字
        cardBorder        = 0xFFD0D0D0.toInt(),
        cardInnerBg       = 0xFFFFFFFF.toInt(),
        veil              = 0x00000000,         // 0 遮罩：完美显示背景图
        toggleTrackOff    = 0xFFCCCCCC.toInt(),
        toggleTrackOn     = 0xFF000000.toInt(),
        toggleKnob        = 0xFFFFFFFF.toInt(),
        textShadow        = false               // 关键：浅色模式必须关闭文字阴影防发糊
    )

    fun of(theme: LiquidTheme): LiquidTokens = when (theme) {
        LiquidTheme.DARK  -> DARK
        LiquidTheme.LIGHT -> LIGHT
    }
}
