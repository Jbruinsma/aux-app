package com.aux_app.controller;

import com.aux_app.dto.artist.ArtistSummary;
import com.aux_app.dto.music_piece.TopMusicPiece;
import com.aux_app.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/core")
public class CoreController {

    private final UserRepository users;

    public CoreController(UserRepository users) {
        this.users = users;
    }

    @GetMapping("/users")
    @Operation(
            summary = "Get the list of users",
            description = "Public. Returns up to 6 random user IDs of users with a custom profile picture."
    )
    @ApiResponse(responseCode = "200", description = "OK")
    @ResponseStatus(HttpStatus.OK)
    public List<String> getUsers() {
        return users.findUserIds();
    }

    @GetMapping("/top")
    @Operation(
            summary = "Get the top music pieces",
            description = "Public. NOT IMPLEMENTED YET: returns one placeholder entry whose fields all read `NOT IMPLEMENTED`."
    )
    @ApiResponse(responseCode = "200", description = "OK")
    @ResponseStatus(HttpStatus.OK)
    public List<TopMusicPiece> getTopMusicPieces() {
        return List.of(
                new TopMusicPiece(
                        "NOT IMPLEMENTED",
                        "NOT IMPLEMENTED",
                        new ArtistSummary(
                                "NOT IMPLEMENTED",
                                "NOT IMPLEMENTED",
                                "NOT IMPLEMENTED"
                        )
                )
        );
    }

}
