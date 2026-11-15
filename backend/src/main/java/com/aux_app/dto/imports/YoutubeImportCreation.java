package com.aux_app.dto.imports;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "A YouTube video to import as an MP3")
public record YoutubeImportCreation(
        @Schema(description = """
                Link to one YouTube video: `youtube.com/watch?v=`, `youtu.be/`, `/shorts/`, `/embed/` or `/live/`, on
                www., m. or music.youtube.com. Extra query parameters (`t`, `si`, `list`) are ignored; playlists,
                channels and other sites are rejected""",
                example = "https://www.youtube.com/watch?v=dQw4w9WgXcQ") @NotBlank @Size(max = 200) String url
) {}
