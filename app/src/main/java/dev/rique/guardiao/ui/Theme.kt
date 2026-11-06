package dev.rique.guardiao.ui

data class Theme(val id: String, val name: String, val core: Int, val glow: Int)

object Themes {
    val all = listOf(
        Theme("branco", "Branco", 0xFFF2FBFF.toInt(), 0xFFBFEFFF.toInt()),
        Theme("ciano", "Ciano", 0xFFCCFBFF.toInt(), 0xFF3FD8FF.toInt()),
        Theme("ambar", "Âmbar", 0xFFFFE6B8.toInt(), 0xFFFFA62E.toInt()),
        Theme("verde", "Verde", 0xFFD6FFDC.toInt(), 0xFF3DFF74.toInt()),
        Theme("lilas", "Lilás", 0xFFEAE0FF.toInt(), 0xFFA98BFF.toInt()),
    )

    fun byId(id: String?): Theme = all.firstOrNull { it.id == id } ?: all[0]
}
