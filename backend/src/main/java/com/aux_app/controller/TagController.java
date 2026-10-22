package com.aux_app.controller;

import com.aux_app.auth.CurrentUser;
import com.aux_app.entity.UserEntity;
import com.aux_app.services.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tags")
public class TagController {

    private final TagService tags;

    public TagController(TagService tags) {
        this.tags = tags;
    }

    @GetMapping
    @Operation(
            summary = "Suggest tags",
            description = """
                    Tags starting with `q` (case-insensitive), at most 20. Curated genres and moods come first, then
                    tags used most on public pieces and playlists. Tags only used on private items are left out.
                    Omit `q` for the top tags. Use it for the tag picker on the upload and playlist forms.
                    """)
    @ApiResponse(responseCode = "200", description = "OK; tag names, empty if none match")
    public List<String> suggestTags(
            @CurrentUser UserEntity user,
            @Parameter(description = "Start of the tag name", example = "ch") @RequestParam(required = false) String q
    ) {
        return this.tags.suggest(q);
    }
}
