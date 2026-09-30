# Backend requests

Endpoints the frontend needs that aren't in `backend/docs/openapi.json` yet. The frontend mocks them until they ship.

______________________________________________________

### DELETE /api/users/me

Deletes the logged-in user's account and wipes their data: profile details, picture and banner, playlists, uploaded
MP3s (including the files in R2), favorites, follows and play history. After this the token should stop working.

Response `204`, no body.

### Open questions

- Should deleting the account also require the current password? (Email and password changes now do.)
- When an account is deleted, what happens to its songs that sit in other people's playlists?
- Should a password change end the user's other sessions? Tokens are stateless JWTs now, so they'd stay valid until
  they expire.
