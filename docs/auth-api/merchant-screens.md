# Merchant screens (frontend guide)

Use `authorizedFetch` for every call (it adds the access token and renews it).
Auth calls go to `env.authUrl`, SwiftDrop calls to `env.apiUrl`.

## How people get here (already built)
- Login and sign-up pages: link **"Are you a business? Sell with SwiftDrop"** → `/signup?next=/merchant/apply`
- After logging in (password or Google) the app opens **`/merchant/apply`** (`routes.merchantApply`)
- `features/merchant/pages/MerchantApplyPage.tsx` is a placeholder: build Screens 1 + 2 there
- To add another "continue to" page, add it to `ALLOWED_NEXT` in `features/auth/lib/landing.ts` (exact paths only)

## Screen 1: Become a merchant (any logged-in person)
Form: business name, registration number, website (optional) → **Submit**
```
POST {authUrl}/api/v1/merchants/application
{ "businessName": "Ceylon Tea House", "registrationNumber": "PV12345", "website": "https://ceylontea.lk" }
```
→ `201` → go to Screen 2.

## Screen 2: My application
```
GET {authUrl}/api/v1/merchants/application
```
Show by `status`:
| status | Show |
|---|---|
| `PENDING` | "We're reviewing your application" |
| `APPROVED` | "You're a merchant!" + button **Open merchant dashboard** |
| `REJECTED` | the reason + "You can apply again after 7 days" |

**After APPROVED:** call `refresh()` from `authApi` once (the old token has no MERCHANT yet), then open Screen 4.
No need to log out.

## Screen 3: Merchant approvals (admin portal, ADMIN only)
```
GET  {authUrl}/api/v1/admin/merchants?status=PENDING
POST {authUrl}/api/v1/admin/merchants/{merchantId}/decision   { "decision": "APPROVE" }
POST {authUrl}/api/v1/admin/merchants/{merchantId}/decision   { "decision": "REJECT", "reason": "..." }
```
A table: business name, registration no., date, **Approve** / **Reject** (Reject asks for a reason).
Show the page only if the token has `merchant:approve` (the server checks it anyway).

## Screen 4: Merchant dashboard + business profile (MERCHANT)
```
GET {apiUrl}/api/merchants/me     → created automatically on the first visit
PUT {apiUrl}/api/merchants/me     → save the business details
```
If `complete` is `false`: show **"Complete your business profile"** (business name, business email, contact number,
address line 1 + 2, city, province, postal code, country). Required: name, email, contact number, address line 1,
city, country. Errors come back per field (`errors.city` etc.).
`ownerName` (from the login account) can be shown as "Owner".

## Screen 5 (later): API keys for the merchant's own software
```
GET    {authUrl}/api/v1/merchants/me/clients
POST   {authUrl}/api/v1/merchants/me/clients   { "name": "Online shop" }   → shows the secret ONCE
DELETE {authUrl}/api/v1/merchants/me/clients/{clientId}
```
Show the secret once with a "Copy" button and the warning "You won't see this again".

## Which page for whom
- Show "Become a merchant" when the token has no `merchant:api-access`.
- Show the merchant dashboard when it has `merchant:api-access`.
- Tokens: `readClaims(getAccessToken()).entitlements` (in `auth/lib/session.ts`).
