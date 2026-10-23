package io.github.z0orb.akagi.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.z0orb.akagi.R
import io.github.z0orb.akagi.core.common.AudioCodec

@Composable
fun codecLabel(codec: AudioCodec): String = stringResource(
    when (codec) {
        AudioCodec.OPUS -> R.string.codec_opus
        AudioCodec.M4A -> R.string.codec_m4a
        AudioCodec.MP3 -> R.string.codec_mp3
    },
)
