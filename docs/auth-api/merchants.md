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

## What is inside that token (live since 21 Nov 2026)
It is a **machine** token: no person, no name or email.

| Claim | Value | Use it for |
|---|---|---|
| `sub`, `client_id` | `mc_` + 22 characters (the key's id) | which key is calling. People's tokens have a UUID here instead |
| `merchant_owner` | the merchant owner's user id (UUID) | **finding the Merchant**: `merchants.owner_id` |
| `merchant_id` | the auth service's own merchant id | not needed in the backend |
| `roles` | `["MERCHANT"]` | |
| `entitlements` | `merchant:api-access`, `merchant:api-booking`, `parcel:create`, `parcel:read-own` | booking needs **`merchant:api-booking`** (only machine tokens have it; no person's login ever does) |
| `aud` / `typ` | `swiftdrop-api` / `at+jwt` | already checked by the backend's token validator |

## Rules for the booking API (backend)
1. **Permission:** require `merchant:api-booking`.
2. **Still valid?** Tokens live 15 minutes. A deleted key, a suspended merchant or a blocked owner gets no new
   tokens, but one already issued keeps working until it ends. Booking creates real work, so first ask the auth
   service: `POST https://auth.getswiftdrop.me/oauth2/introspect` (Basic auth with our `svc_swiftdrop-backend`
   client, form `token=<the token>`). `{"active": false}` → answer `401`. `{"active": true, ...}` also contains
   `merchant_owner`.
3. **Which merchant:** read `merchant_owner` from the token (or the introspection answer) and find the Merchant by
   owner id. **Never** use a merchant or owner id sent in the request body: the caller writes the body, the auth
   service signs the token.
4. **No owner, no merchant row, or merchant not approved** → `403`.

## Merchant API shipment booking

The delivery backend exposes POST /api/merchant-integrations/deliveries. It accepts the same
shipment fields as personal Merchant booking, with a bearer access token and a required
Idempotency-Key header (1–128 letters, digits, dots, underscores, colons or hyphens).
It resolves the business from the authorized client identity. A Merchant ID in the request
does not select the owner. New shipments are priced server-side and remain PENDING
until the human Merchant confirms from the portal.

The key is scoped to the Merchant and OAuth client. A new booking returns 201.
Replaying the same key and shipment returns 200 with the same delivery ID and its
current status. Reusing the same key with different shipment fields returns 409.
A retry must use the same client, key and shipment data. The booking and its client
audit record are stored in one transaction.

The route denies authenticated requests until an issuer adapter is installed.
The adapter must establish the verified machine token type, booking permission,
stable client ID, authoritative business ownership, and revocation and suspension
rules. The current implementation includes a test-only contract.
