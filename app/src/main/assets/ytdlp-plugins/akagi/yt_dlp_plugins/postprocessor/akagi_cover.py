# Akagi: square cover art for --embed-thumbnail (PLAN D-060).
# yt-dlp's thumbnail converter skips a file already in the target format, so a JPEG thumbnail kept YouTube's 16:9
# frame. This turns whatever thumbnail was written (WebP or JPEG) into a centred 600 px square JPEG before the embed
# step. A failure only leaves the original thumbnail, so a download never fails over its cover.
import os

from yt_dlp.postprocessor.ffmpeg import FFmpegPostProcessor


class AkagiCoverPP(FFmpegPostProcessor):
    def run(self, info):
        for thumbnail in info.get('thumbnails') or []:
            source = thumbnail.get('filepath')
            if not source or not os.path.exists(source):
                continue
            target = os.path.splitext(source)[0] + '.square.jpg'
            try:
                self.run_ffmpeg(source, target, [
                    '-vf', "crop='min(iw,ih)':'min(iw,ih)',scale=600:600", '-q:v', '3', '-frames:v', '1'])
            except Exception as error:
                self.report_warning(f'Akagi: square cover failed, embedding the original: {error}')
                continue
            os.remove(source)
            thumbnail['filepath'] = target
        return [], info
