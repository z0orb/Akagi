package io.github.z0orb.akagi.ytdlp

/** Trimmed real `-J` outputs under `src/test/resources/ytdlp`. */
object FormatFixtures {
    fun json(name: String): String =
        requireNotNull(javaClass.getResource("/ytdlp/$name.json")) { "Missing fixture $name" }.readText()

    fun info(name: String): VideoInfo = AudioFormatMapper.parse(json(name))
}
