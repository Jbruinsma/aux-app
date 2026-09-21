package com.aux.controller;

import com.aux.dto.users.UserExistance;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aux.repository.UserRepository;

// Port of backend/routes/users.py
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository users;

    public UserController(UserRepository users) {
        this.users = users;
    }

    @GetMapping("/check-username/{username}")
    public UserExistance checkUsernameExists(@PathVariable String username) {
        return new UserExistance(users.existsByUsername(username));
    }

    // TODO GET  /{username}/delete
    // TODO GET  /profile/{username}
    // TODO POST /{username}/update/profile-picture       multipart: profile_picture
    // TODO POST /{username}/update-username/{new_username}
    // TODO POST /{username}/update-password              json: old_password, new_password
    // TODO GET  /{username}/get-last-playback
    // TODO POST /{username}/update-last-playback         json: playback data
    // TODO POST /{username}/add-public-playlist          json: playlist_uuid, playlist_owner
    // TODO POST /{username}/remove-public-playlist       json: playlist_uuid, playlist_owner
    // TODO POST /{username}/remove-added-to-playlist     json: playlist_uuid, playlist_owner
}
