package com.aux_app.controller;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import com.aux_app.auth.CurrentUser;
import com.aux_app.dto.auth.*;
import com.aux_app.dto.users.OnboardingStep;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import com.aux_app.services.OtpService;
import com.aux_app.services.RefreshTokenService;
import com.aux_app.services.SessionService;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.aux_app.dto.users.UserSummary;
import com.aux_app.entity.UserEntity;
import com.aux_app.error.AuxException;
import com.aux_app.repository.UserRepository;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository users;
    private final SessionService sessions;
    private final OtpService otps;
    private final RefreshTokenService refreshTokens;
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
    private final String dummyHash = bcrypt.encode("dummy-password");

    public AuthController(UserRepository users, SessionService sessions, OtpService otps, RefreshTokenService refreshTokens) {
        this.users = users;
        this.sessions = sessions;
        this.otps = otps;
        this.refreshTokens = refreshTokens;
    }

    @PostMapping("/register")
    @Operation(
            summary = "Register a new account",
            description = """
                    JSON body: `email` (3-254 chars) and `password` (8-32 chars, at most 72 bytes in UTF-8).
                    Creates the account and logs it in. Returns a session token and the new user, whose
                    `onboardingStep` is `USERNAME`; send the user through POST /api/users/onboarding/step next.
                    """)
    @ApiResponse(responseCode = "201", description = "Account created; body holds the session token and the new user")
    @ApiResponse(responseCode = "400", description = "Missing, too short or too long email or password (codes INVALID_FIELD, MALFORMED_BODY)")
    @ApiResponse(responseCode = "409", description = "Email already registered (code EMAIL_TAKEN), or the account could not be created (code USERNAME_TAKEN)")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegistrationCredentials credentials) {

        checkPasswordBytes(credentials.password(), "password");

        String registrationEmail = credentials.email();

        if (users.existsByEmail(registrationEmail)) {
            throw new AuxException(
                    HttpStatus.CONFLICT,
                    "EMAIL_TAKEN",
                    "Email is already associated with an account",
                    "email"
            );
        }

        String newUserIdentifier = UUID.randomUUID().toString();

        try {

            String hash = bcrypt.encode(credentials.password());
            UserEntity user = users.save(
                    new UserEntity(
                            newUserIdentifier,
                            registrationEmail,
                            hash,
                            OnboardingStep.USERNAME
                    )
            );

            return startSession(user);

        } catch (Exception accountCreationException) {
            throw new AuxException(
                    HttpStatus.CONFLICT,
                    "USERNAME_TAKEN",
                    "There has been an error creating your account. Please try again.",
                    "username"
            );
        }
    }

    @PostMapping("/login")
    @Operation(
            summary = "Log in",
            description = """
                    JSON body: `email` and `password`. Returns a session token and the user.
                    Send the token as `Authorization: Bearer <token>` on authenticated endpoints.
                    A wrong email and a wrong password give the same error.
                    """)
    @ApiResponse(responseCode = "200", description = "Logged in; body holds the session token and the user")
    @ApiResponse(responseCode = "400", description = "Missing or badly sized email or password (codes INVALID_FIELD, MALFORMED_BODY)")
    @ApiResponse(responseCode = "401", description = "Wrong email or password (code INVALID_CREDENTIALS)")
    @ResponseStatus(HttpStatus.OK)
    public AuthResponse login(@Valid @RequestBody LoginCredentials credentials) {
        UserEntity user = users.findByEmail(credentials.email());

        String hash = user != null ? user.getPasswordHash() : dummyHash;
        if (!bcrypt.matches(credentials.password(), hash) || user == null) {
            throw new AuxException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username or password");
        }

        return startSession(user);
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Refresh the session",
            description = """
                    JSON body: `refreshToken` from the last register, login or refresh response. Returns a new token,
                    a new refresh token and the user, in the same shape as login. No Authorization header needed, so it
                    works after the token has expired. Each refresh token works once: keep the new one. Reusing an old
                    one (more than 30 seconds after it was traded) ends that session everywhere.
                    """)
    @ApiResponse(responseCode = "200", description = "Refreshed; body holds the new tokens and the user")
    @ApiResponse(responseCode = "400", description = "Missing refresh token (codes INVALID_FIELD, MALFORMED_BODY)")
    @ApiResponse(responseCode = "401", description = "Refresh token unknown, expired, revoked or reused (code INVALID_REFRESH_TOKEN): log in again")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        RefreshTokenService.Rotation rotation = refreshTokens.rotate(request.refreshToken());
        // Deleting the account cascades its refresh tokens, so this is only a race with a deletion
        UserEntity user = users.findById(rotation.userId()).orElseThrow(() -> {
            refreshTokens.revoke(rotation.next().token());
            return new AuxException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Session expired; log in again");
        });
        return response(user, rotation.familyId(), rotation.next());
    }

    @PostMapping("/logout")
    @Operation(
            summary = "Log out",
            description = """
                    JSON body: `refreshToken`. Ends that session so its refresh token stops working. The current
                    token still works until it expires (at most an hour), so the client should drop it too.
                    Always 204, also for an unknown token.
                    """)
    @ApiResponse(responseCode = "204", description = "Logged out")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        refreshTokens.revoke(request.refreshToken());
    }

    @PostMapping("/password/request")
    @Operation(
            summary = "Start a password change",
            description = """
                    JSON body: `currentPassword`. Emails a 6-digit code to the user's current address.
                    Finish with POST /api/auth/password/confirm.
                    """)
    @ApiResponse(responseCode = "204", description = "Code sent")
    @ApiResponse(responseCode = "403", description = "Wrong current password (code WRONG_PASSWORD)")
    @ApiResponse(responseCode = "429", description = "A code was sent less than a minute ago (code OTP_COOLDOWN), or too many requests from this IP (code RATE_LIMITED)")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void requestPasswordChange(@CurrentUser UserEntity user, @Valid @RequestBody PasswordChangeRequest request) {
        checkCurrentPassword(user, request.currentPassword());
        otps.send(user.getUserId(), user.getEmail(), null);
    }

    @PostMapping("/password/confirm")
    @Operation(
            summary = "Finish a password change",
            description = """
                    JSON body: `code` from the email and `newPassword` (same rules as registration).
                    Logs out every other session of the account; this one stays logged in.
                    """)
    @ApiResponse(responseCode = "204", description = "Password changed")
    @ApiResponse(responseCode = "400", description = "Wrong, expired or used code (code INVALID_OTP), badly formatted code or bad password (code INVALID_FIELD), or unreadable body (code MALFORMED_BODY)")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmPasswordChange(
            @CurrentUser UserEntity user,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody PasswordChange change
    ) {
        checkPasswordBytes(change.newPassword(), "newPassword");
        // An email-change code carries a pendingEmail; it must not unlock a password change
        if (otps.consume(user.getUserId(), change.code()) != null) throw OtpService.invalid();
        user.setPasswordHash(bcrypt.encode(change.newPassword()));
        users.save(user);
        // Whoever knew the old password can't stay logged in with it. @CurrentUser already checked the header
        refreshTokens.revokeOthers(user.getUserId(), sessions.sessionId(authorization.substring("Bearer ".length())));
    }

    @PostMapping("/email/request")
    @Operation(
            summary = "Start an email change",
            description = """
                    JSON body: `newEmail` and `currentPassword`. Emails a 6-digit code to the new address,
                    proving the user owns it. Finish with POST /api/auth/email/confirm.
                    If the address already has an account, it gets a notice instead of a code and the response is
                    the same, so this endpoint can't be used to find out which emails are registered.
                    """)
    @ApiResponse(responseCode = "204", description = "Code (or, for a registered address, a notice) sent to the new address")
    @ApiResponse(responseCode = "403", description = "Wrong current password (code WRONG_PASSWORD)")
    @ApiResponse(responseCode = "429", description = "A code was sent less than a minute ago (code OTP_COOLDOWN), or too many requests from this IP (code RATE_LIMITED)")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void requestEmailChange(@CurrentUser UserEntity user, @Valid @RequestBody EmailChangeRequest request) {
        // Password stops a stolen session token from moving the account to an attacker's inbox
        checkCurrentPassword(user, request.currentPassword());
        if (users.existsByEmail(request.newEmail())) {
            otps.sendTakenNotice(user.getUserId(), request.newEmail());
        } else {
            otps.send(user.getUserId(), request.newEmail(), request.newEmail());
        }
    }

    @PostMapping("/email/confirm")
    @Operation(summary = "Finish an email change", description = "JSON body: `code` from the email sent to the new address. Returns the updated user.")
    @ApiResponse(responseCode = "200", description = "Email changed")
    @ApiResponse(responseCode = "400", description = "Wrong, expired or used code (code INVALID_OTP), badly formatted code (code INVALID_FIELD), or unreadable body (code MALFORMED_BODY)")
    @ApiResponse(responseCode = "409", description = "Email was registered after the code was sent (code EMAIL_TAKEN); only the inbox owner can get here")
    public UserSummary confirmEmailChange(@CurrentUser UserEntity user, @Valid @RequestBody OtpCode otpCode) {
        String newEmail = otps.consume(user.getUserId(), otpCode.code());
        if (newEmail == null) throw OtpService.invalid();
        checkEmailFree(newEmail);
        user.setEmail(newEmail);
        return UserSummary.of(users.save(user));
    }

    // A new login: a new refresh-token family, whose id the access token carries as 'sid'
    private AuthResponse startSession(UserEntity user) {
        String familyId = UUID.randomUUID().toString();
        return response(user, familyId, refreshTokens.issue(user.getUserId(), familyId));
    }

    private AuthResponse response(UserEntity user, String familyId, RefreshTokenService.Issued refresh) {
        SessionToken access = sessions.issueSymmetricToken(user.getUserId(), Map.of(SessionService.SESSION_CLAIM, familyId));
        return new AuthResponse(access.token(), access.expiresAt(), refresh.token(), refresh.expiresAt(), UserSummary.of(user));
    }

    // 403, not 401: the frontend treats any 401 as an expired session and logs out
    private void checkCurrentPassword(UserEntity user, String password) {
        if (!bcrypt.matches(password, user.getPasswordHash())) {
            throw new AuxException(HttpStatus.FORBIDDEN, "WRONG_PASSWORD", "Current password is wrong", "currentPassword");
        }
    }

    private void checkEmailFree(String email) {
        if (users.existsByEmail(email)) {
            throw new AuxException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Email is already associated with an account", "email");
        }
    }

    // BCrypt only reads the first 72 bytes; @Size counts chars, and emoji are 4 bytes each
    private void checkPasswordBytes(String password, String field) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new AuxException(HttpStatus.BAD_REQUEST, "INVALID_FIELD", field + " is too long", field);
        }
    }
}
