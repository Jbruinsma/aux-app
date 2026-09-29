package com.aux_app.dto.users;

import io.swagger.v3.oas.annotations.media.Schema;

public record BannerUpdate(
        @Schema(example = "https://aux.justinabruinsma.com/banner/3f2b8c1e-8d4a-4c6e-9a51-2f0d7b9e6c11.webp") String bannerUrl
) {}
