# My account (the logged-in person)

All calls: `Authorization: Bearer <access_token>`

## Read my profile
`GET /api/v1/me/profile` → `200` with name, picture, email. The answer has an `ETag` header: keep it for changes.

## Change my profile (name, picture)
`PATCH /api/v1/me/profile`
Header: `If-Match: <the ETag from the read>`
```json
{ "name": "Nimal P." }
```
→ `200`. `428` = If-Match missing, `412` = someone changed it meanwhile (read again, then retry).

## Change my password
`POST /api/v1/me/password`
```json
{ "current_password": "...", "new_password": "..." }
```
→ `200` with new tokens (same shape as log in). Other devices are logged out: save the new tokens.

## Change my email
`POST /api/v1/me/email-change`
```json
{ "new_email": "new@example.com", "current_password": "..." }
```
→ `202`. A confirm link goes to the new address.

Pages the email links open:
- `POST /api/v1/auth/email-change/confirm` `{ "token": "..." }` → `204`
- `POST /api/v1/auth/email-change/revert` `{ "token": "..." }` (the "this wasn't me" link to the old address) → `204`

## Download my data
`GET /api/v1/me/export` → `200`, a JSON file with everything auth holds about me.

## Delete my account
`DELETE /api/v1/me`
```json
{ "current_password": "..." }
```
→ `204`. Final: the account is removed everywhere.
