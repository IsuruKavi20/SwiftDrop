# Login and permissions: how to use them in SwiftDrop

## The idea in 30 seconds
- We **don't write login code** in this project. Login, sign-up and Google login are done by our separate
  **auth service** at `https://auth.getswiftdrop.me` 
- After login, the user gets a **token**: a small signed "ticket" that says who they are and what they may do.
  It lasts 15 minutes and renews itself.
- The frontend sends this ticket with every request. Our backend **checks the ticket** and allows or blocks the request.

```
Frontend ──log in──► auth service ──► gives a ticket (token)
Frontend ──request + ticket──► our backend ──► checks the ticket ──► OK, or "not allowed"
```

---

## 1. Run it on your laptop
The auth service is already online. You only start our two parts:

```powershell
# Window 1: backend (http://localhost:8080)
cd backend
.\mvnw spring-boot:run

# Window 2: frontend (http://localhost:5173)
cd frontend
npm install
npm run dev
```
Open http://localhost:5173, sign up, open the email link, log in. That's it.

> Keep the frontend on **http://localhost:5173**. The auth service only sends people back to that address.

## 2. Test accounts and roles
- **Use a real email you can open.** You must click the link in the email before you can log in.
- **Want more than one account?** With Gmail, `you+courier@gmail.com` and `you+dispatcher@gmail.com` both arrive in
  your inbox, but they count as different users.
- **New accounts are always CUSTOMER.** Need COURIER or DISPATCHER? Send Isuru the email address. Once the role is changed,
  log out and log in again.

What each role is allowed to do:

| Role | Allowed |
|---|---|
| CUSTOMER | `parcel:create`, `parcel:read-own` |
| COURIER | `delivery:read-own`, `delivery:update-status` |
| DISPATCHER | `parcel:read-all`, `parcel:assign`, `delivery:read-all` |
| ADMIN | everything a dispatcher can + `merchant:approve`, `user:read`, `user:manage-roles`, `audit:read` |
| MERCHANT | `parcel:create`, `parcel:read-own`, `merchant:api-access` |

**Need a new one** (for example `payment:refund`)? Ask Isuru to add it in the auth service.
Don't make up new names only in our code: they won't be in anyone's token.

## 3. Backend: protect your endpoint
Every endpoint needs a login already. You add **one line** that says *which permission* is needed, and you take
the user's id **from the token** (never from the URL):

```java
@PostMapping
@PreAuthorize("hasAuthority('parcel:create')")            // only users allowed to create parcels
Parcel create(@Valid @RequestBody CreateParcelRequest request, @AuthenticationPrincipal Jwt user) {
    UUID customerId = UUID.fromString(user.getSubject());  // who is calling (their user id)
    return parcels.create(customerId, request);
}
```

**"own" permissions need one more check.** `parcel:read-own` means "only *your* parcels". Someone can type any
id in the URL, so check that the item belongs to the caller, and answer 403 if it doesn't.

Real example in our code: `backend/src/main/java/com/swiftdrop/backend/customer/controller/CustomerDeliveryController.java`.

**Forgot the permission line?** The build will stop and tell you which endpoint needs one.
Only endpoints under `/api/public/` are allowed without it.

**Don't store user accounts.** Save only the user's id (like `customer_id`). Never store passwords. Extra details we
need (phone, addresses) go in our own tables, linked by that id.

## 4. Backend: test it (no real login needed)
In tests you can pretend to be a logged-in user:
```java
mockMvc.perform(get("/api/parcels/{id}", id)
        .with(jwt().jwt(t -> t.subject(customerId.toString()))
                   .authorities(new SimpleGrantedAuthority("parcel:read-own"))))
       .andExpect(status().isOk());
```
Always add one test where **another user** tries to see the data and gets **403**.
Example: `CustomerModuleApiTest.java`.

## 5. Try the API from Postman or the terminal
1. Log in to get a token:
   ```powershell
   curl.exe -s -H "Content-Type: application/json" --data-binary "@login.json" https://auth.getswiftdrop.me/api/v1/auth/login
   ```
   `login.json` contains: `{"email":"you+courier@gmail.com","password":"your password"}`.
   Copy the `access_token` from the answer.
2. Call our backend with it (works for 15 minutes):
   ```powershell
   curl.exe -H "Authorization: Bearer <access_token>" http://localhost:8080/api/customers/me/dashboard
   ```
**Never share a token** in chats, issues or commits. For 15 minutes it works like your password.

## 6. Frontend: call the API
Use `authorizedFetch`. It adds the token for you and renews it when it runs out:
```ts
import { authorizedFetch } from '../auth/api/authApi';

const response = await authorizedFetch(`${env.apiUrl}/api/parcels`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(parcel),
});
```
To show or hide a button by permission:
```ts
import { getAccessToken, readClaims } from '../auth/lib/session';

const canAssign = readClaims(getAccessToken()!).entitlements?.includes('parcel:assign');
```
Hiding a button is only for looks. **The backend is what really blocks** people without permission.

Put new pages in `src/features/<feature>/pages/` and their API calls in `src/features/<feature>/api/`.

## 7. Settings (you normally don't change these)
| Where | Setting | Default |
|---|---|---|
| backend | `SWIFTDROP_AUTH_ISSUER` | `https://auth.getswiftdrop.me` |
| backend | `SWIFTDROP_ALLOWED_ORIGINS` | `http://localhost:5173` |
| frontend `.env` (copy `.env.example`) | `VITE_AUTH_URL`, `VITE_API_URL` | the auth service, `http://localhost:8080` |

The full list of auth service endpoints is in `openapi.json` in the auth repo (open it in Swagger UI or Postman).
