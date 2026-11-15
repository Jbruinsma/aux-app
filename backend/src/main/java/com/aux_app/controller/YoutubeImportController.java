package com.aux_app.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.aux_app.auth.CurrentUser;
import com.aux_app.dto.base.Message;
import com.aux_app.dto.imports.YoutubeImport;
import com.aux_app.dto.imports.YoutubeImportAvailability;
import com.aux_app.dto.imports.YoutubeImportCreation;
import com.aux_app.entity.UserEntity;
import com.aux_app.services.YoutubeImportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/imports/youtube")
public class YoutubeImportController {

    private final YoutubeImportService imports;

    public YoutubeImportController(YoutubeImportService imports) {
        this.imports = imports;
    }

    @GetMapping("")
    @Operation(
            summary = "Check whether the caller can import from YouTube",
            description = "Testers and admins only, and only while the server has the feature on. Use it to show or hide the YouTube option on the upload page.")
    @ApiResponse(responseCode = "200", description = "OK")
    public YoutubeImportAvailability availability(@CurrentUser UserEntity user) {
        return new YoutubeImportAvailability(imports.isAvailable(user));
    }

    @PostMapping("")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary = "Start importing a YouTube video's audio",
            description = """
                    Phase one of uploading from YouTube. JSON body: `url`, a link to one video. The server checks the
                    video (30 seconds to 10 minutes, not live) and converts its audio to MP3 in the background; poll
                    `GET /api/imports/youtube/{importId}` until `status` is READY or FAILED (usually 10-60 seconds).
                    Phase two is the normal `POST /api/music-pieces/create`, with `importId` in place of `mp3File`.
                    A READY import is kept for 30 minutes. At most 2 running and 5 unused imports per user, and 20 per hour.
                    Testers and admins only.
                    """)
    @ApiResponse(responseCode = "202", description = "Import queued; body is its current state")
    @ApiResponse(responseCode = "400", description = "Not a link to a single YouTube video (code INVALID_YOUTUBE_URL), or missing `url` (code INVALID_FIELD)")
    @ApiResponse(responseCode = "403", description = "Caller isn't a tester or admin (code YOUTUBE_IMPORT_FORBIDDEN)")
    @ApiResponse(responseCode = "429", description = "Per-user limit reached (code IMPORT_LIMIT)")
    @ApiResponse(responseCode = "503", description = "Feature is off (code YOUTUBE_IMPORT_DISABLED), or the server is busy (code IMPORT_BUSY)")
    public YoutubeImport start(@CurrentUser UserEntity user, @Valid @RequestBody YoutubeImportCreation body) {
        return imports.start(user, body.url());
    }

    @GetMapping("/{importId}")
    @Operation(summary = "Get an import's progress", description = "Only the caller's own imports. A FAILED import says why in `error`.")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "404", description = "No such import of the caller's, or it expired (code IMPORT_NOT_FOUND)")
    public YoutubeImport get(@CurrentUser UserEntity user, @PathVariable String importId) {
        return imports.get(user, importId);
    }

    @DeleteMapping("/{importId}")
    @Operation(summary = "Cancel or discard an import", description = "Stops a running import, or deletes a finished one the caller no longer wants.")
    @ApiResponse(responseCode = "200", description = "Import gone")
    @ApiResponse(responseCode = "404", description = "No such import of the caller's (code IMPORT_NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "A music piece is being created from it right now (code IMPORT_IN_USE)")
    public Message discard(@CurrentUser UserEntity user, @PathVariable String importId) {
        imports.discard(user, importId);
        return new Message("Import discarded");
    }
}
