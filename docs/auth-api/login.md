# Login

## Sign up
`POST /api/v1/auth/signup`
```json
{ "name": "Nimal Perera", "email": "nimal@example.com", "password": "at least 15 characters" }
```
→ `202`. The person gets an email with a link to confirm their address.

## Confirm email (the page the email link opens)
`POST /api/v1/auth/email-verification`
```json
{ "token": "<token from the link>" }
```
→ `204`

## Send the confirmation email again
`POST /api/v1/auth/email-verification/resend`
```json
{ "email": "nimal@example.com" }
```
→ `202`

## Log in
`POST /api/v1/auth/login`
```json
{ "email": "nimal@example.com", "password": "..." }
```
→ `200`
```json
{ "access_token": "...", "token_type": "Bearer", "expires_in": 900, "refresh_token": "...", "refresh_expires_in": 2592000 }
```

## Log in with Google
1. Send the browser to `/oauth2/authorization/google?code_challenge=<challenge>`
2. Google sends the person back to the frontend page `/auth/callback#code=...`
3. `POST /api/v1/auth/google/exchange`
```json
{ "code": "<code from the page address>", "verifier": "<the verifier that made the challenge>" }
```
→ `200`, the same answer as log in.

## Get a new access token (every ~15 minutes)
`POST /api/v1/auth/refresh`
```json
{ "refresh_token": "..." }
```
→ `200`, the same answer as log in (with a new refresh token: always keep the newest one).

## Log out
`POST /api/v1/auth/logout`
```json
{ "refresh_token": "..." }
```
→ `204`

Also end the access token: `POST /oauth2/revoke` (form data) `token=<access_token>` → always `200`.

## Who am I
`GET /api/v1/auth/me` (Bearer token) → `200` with your id and roles.

## Forgot password (coming soon: not live yet)
`POST /api/v1/auth/password-reset`
```json
{ "email": "nimal@example.com" }
```
→ always `202` (also for unknown emails). The email has a link to the frontend page `/reset-password`.

`POST /api/v1/auth/password-reset/confirm`
```json
{ "token": "<token from the link>", "new_password": "..." }
```
→ `204`. The link works once, for 30 minutes. Then the person logs in normally.
