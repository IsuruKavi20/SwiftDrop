# Merchants

## Apply to become a merchant (logged-in person)
`POST /api/v1/merchants/application`
```json
{ "businessName": "Ceylon Tea House", "registrationNumber": "PV12345", "website": "https://ceylontea.lk" }
```
`website` is optional. → `201`. An admin approves it (see [admin.md](admin.md)).

`GET /api/v1/merchants/application` → `200`, my application and its status.

## API keys for the merchant's own software (approved merchants, `merchant:api-access`)
| What | Call |
|---|---|
| Create a key | `POST /api/v1/merchants/me/clients` `{ "name": "Online shop" }` → `201`, shows the secret **once** |
| My keys | `GET /api/v1/merchants/me/clients` |
| Delete a key | `DELETE /api/v1/merchants/me/clients/{clientId}` → `204` |

## The merchant's software gets a token
`POST /oauth2/token`
- Header: `Authorization: Basic base64(client_id:client_secret)`
- Body (form data): `grant_type=client_credentials`

→ `200` `{ "access_token": "...", "token_type": "Bearer", "expires_in": 900 }`. Ask again when it ends (no refresh token).
