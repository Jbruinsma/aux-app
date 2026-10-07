package com.aux_app.services;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** Sends mail through Resend's REST API (https://resend.com/docs/api-reference/emails/send-email). */
@Service
public class EmailService {

    private final RestClient resend;
    private final String from;

    public EmailService(@Value("${aux.resend.api-key}") String apiKey, @Value("${aux.resend.from}") String from) {
        this.resend = RestClient.builder()
                .baseUrl("https://api.resend.com")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        this.from = from;
    }

    /** Throws RestClientResponseException on a non-2xx from Resend (bad key, unverified sender, rate limit). */
    public void send(String to, String subject, String text) {
        resend.post()
                .uri("/emails")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("from", from, "to", List.of(to), "subject", subject, "text", text))
                .retrieve()
                .toBodilessEntity();
    }

    public void sendOtpCode(String to, String code) {
        send(to, "Your Aux code: " + code, "Your Aux verification code is " + code + ". It expires in 10 minutes.");
    }

    public void sendPlaylistInvitation(String to, String playlistName, String from) {
        send(to, "You've been invited to a playlist on Aux", "You've been invited to the playlist " + playlistName + " on Aux.");
    }

    public void sendEmailTakenNotice(String to) {
        send(to, "Someone tried to use this email on Aux",
                "Someone asked to move an Aux account to this address, but it already has an Aux account, so nothing changed. "
                        + "If that was you, log in with this address instead. If not, you can ignore this email.");
    }
}
