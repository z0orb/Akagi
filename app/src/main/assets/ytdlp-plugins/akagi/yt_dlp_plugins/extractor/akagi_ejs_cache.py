# Akagi: let yt-dlp reuse the preprocessed YouTube player between runs (PLAN D-033).
# QuickJS needs ~30 s to preprocess the player on a phone and ~3 s with the cache. yt-dlp ships this cache
# disabled because it never rotates the ~4 MB entries; Akagi deletes all but the newest itself.
# Guarded so a rename inside yt-dlp only loses the speed-up, never breaks extraction.
try:
    from yt_dlp.extractor.youtube.jsc._builtin.ejs import EJSBaseJCP

    EJSBaseJCP._ENABLE_PREPROCESSED_PLAYER_CACHE = True
except Exception:
    pass
