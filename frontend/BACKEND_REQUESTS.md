# Backend requests

Endpoints the frontend needs that aren't in `backend/docs/openapi.json` yet. The frontend mocks them until they ship.

## Account settings (Settings → Account)

Mocked in `src/utils/account.js`; set `ACCOUNT_MOCKED = false` once these exist. All need `Authorization: Bearer <token>`.
Errors use the usual `AuxServerError` body.

### GET /api/users/me/account

The logged-in user's email, which `UserSummary` doesn't include.

Response `200`:
```json
{ "email": "mo@example.com" }
```

### GET /api/users/me/check-email?email={email}

Checks while the user types, like `check-username`. Behind auth so it can't be used to test which emails have accounts.

Response `200`: `exists` is true when an account has this email, ignoring case.
```json
{ "exists": false }
```

### PUT /api/users/me/username

Request:
```json
{ "username": "new_name" }
```
Same rules as the onboarding `USERNAME` step (3-16 letters, numbers, underscores; unique ignoring case; reserved names
refused). A change of case alone of your own name (`mo` → `Mo`) should be allowed.

Response `200`: the updated `UserSummary`.

Errors: `400 INVALID_USERNAME`, `409 USERNAME_TAKEN` (also for reserved names).

### PUT /api/users/me/email

Request:
```json
{ "email": "new@example.com" }
```
Same rules as registration (3-254 chars).

Response `200`:
```json
{ "email": "new@example.com" }
```

Errors: `400 INVALID_FIELD` (parameter `email`), `409 EMAIL_TAKEN`.

### PUT /api/users/me/password

Request:
```json
{ "currentPassword": "old password", "newPassword": "new password" }
```
`newPassword` has the registration rules (8-32 chars, at most 72 bytes in UTF-8).

Response `204`, no body.

Errors: `400 INVALID_FIELD` (parameter `newPassword`), `403 WRONG_PASSWORD` when `currentPassword` is wrong. Please not
`401`: the frontend treats any 401 as an expired session and logs the user out.

### DELETE /api/users/me

Deletes the logged-in user's account and wipes their data: profile details, picture and banner, playlists, uploaded
MP3s (including the files in R2), favorites, follows and play history. After this the token should stop working.

Response `204`, no body.

### Open questions

- Should changing the email, or deleting the account, also require the current password?
- When an account is deleted, what happens to its songs that sit in other people's playlists?
- Should a password change end the user's other sessions? Tokens are stateless JWTs now, so they'd stay valid until
  they expire.
