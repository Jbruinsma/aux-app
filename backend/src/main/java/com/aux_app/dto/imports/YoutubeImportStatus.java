package com.aux_app.dto.imports;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
        QUEUED: waiting for a free slot. FETCHING_INFO: checking the video. DOWNLOADING: getting and converting the
        audio. READY: send `importId` to `POST /api/music-pieces/create`. FAILED: see `error`; start a new import to retry""")
public enum YoutubeImportStatus {
    QUEUED,
    FETCHING_INFO,
    DOWNLOADING,
    READY,
    FAILED
}
