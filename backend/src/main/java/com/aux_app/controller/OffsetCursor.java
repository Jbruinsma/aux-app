package com.aux_app.controller;

import com.aux_app.dto.music_piece.MusicPieceOverview;
import com.aux_app.dto.music_piece.MusicPiecePage;
import com.aux_app.error.AuxException;
import org.springframework.http.HttpStatus;

import java.util.List;

// Paging for lists ordered by something other than a stable key (play counts). The client treats the cursor as
// opaque; today it's the offset of the next page.
// ponytail: an offset over a live play-count order can skip or repeat a piece mid-read; fine at our size
final class OffsetCursor {

    private OffsetCursor() {
    }

    static int parse(String cursor) {
        if (cursor == null) return 0;
        try {
            int offset = Integer.parseInt(cursor);
            if (offset >= 0) return offset;
        } catch (NumberFormatException ignored) {
            // falls through to the 400
        }
        throw new AuxException(HttpStatus.BAD_REQUEST, "INVALID_FIELD", "Invalid cursor", "cursor");
    }

    // `rows` was fetched with a limit of pageSize + 1, so an extra row means there's another page
    static MusicPiecePage page(List<MusicPieceOverview> rows, int offset, int pageSize) {
        boolean more = rows.size() > pageSize;
        return new MusicPiecePage(
                more ? rows.subList(0, pageSize) : rows,
                more ? Integer.toString(offset + pageSize) : null
        );
    }
}
