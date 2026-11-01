# Login and permissions in SwiftDrop: developer guide

SwiftDrop does **not** have its own login code. Users log in at the separate **SwiftDrop auth service**
(`https://auth.getswiftdrop.me`, maintained by Isuru). It gives the browser an **access token**; our backend checks that
token on every request and decides what the user may do. This page shows how to build features on top of it.

```
React app ──login / signup / Google──► auth.getswiftdrop.me ──► access token (15 min)
React app ──Authorization: Bearer <token>──► our backend ──► checks the token itself ──► allow (200) / deny (403)
```

---

## 1. Run it locally (no auth service to install)
The auth service is already online; you only run our own two parts:

```powershell
# Terminal 1: backend (http://localhost:8080)
cd backend
.\mvnw spring-boot:run

# Terminal 2: frontend (http://localhost:5173)
cd frontend
npm install
npm run dev
```
Open http://localhost:5173, sign up, confirm the email, log in. Done.

> The browser app must run on **http://localhost:5173**: that's the address the auth service sends users back to
> after Google login and email links, and the one allowed to call our API (CORS).

## 2. Test accounts and roles
- **Sign up with a real email you can read.** Fake addresses don't work: the account must be verified, and bounced
  emails hurt our email reputation.
- **Need several accounts?** Gmail ignores everything after `+`: `you+courier@gmail.com`, `you+dispatcher@gmail.com`
  all land in your inbox but are separate SwiftDrop users.
- **Every new account is a CUSTOMER.** For COURIER or DISPATCHER, send Isuru the email; an admin grants it. Then log
  out and in again (the role is in the next token).

| Role | Permissions in the token |
|---|---|
| CUSTOMER | `parcel:create`, `parcel:read-own` |
| COURIER | `delivery:read-own`, `delivery:update-status` |
| DISPATCHER | `parcel:read-all`, `parcel:assign`, `delivery:read-all` |
| ADMIN | dispatcher's + `merchant:approve`, `user:read`, `user:manage-roles`, `audit:read` |
| MERCHANT | `parcel:create`, `parcel:read-own`, `merchant:api-access` |

**Need a new permission** (e.g. `payment:refund`)? Ask Isuru: permissions are defined in the auth service, then they
appear in the tokens of the right roles. Don't invent permission names only in our code.

## 3. Building a backend feature
Every endpoint is already protected (no token → 401). Add the rule with one annotation, and use the user id from
the token to know **who** is calling:

```java
@RestController
@RequestMapping("/api/parcels")
class ParcelController {

    @PostMapping
    @PreAuthorize("hasAuthority('parcel:create')")            // what they may do: a permission, not a role name
    Parcel create(@Valid @RequestBody CreateParcelRequest request, @AuthenticationPrincipal Jwt user) {
        UUID customerId = UUID.fromString(user.getSubject());  // who: the user id, stored in OUR table
        return parcels.create(customerId, request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('parcel:read-all', 'parcel:read-own')")
    Parcel get(@PathVariable UUID id, JwtAuthenticationToken caller) {
        Parcel parcel = parcels.find(id);
        // "own" permissions always need an ownership check: the id in the URL is chosen by the client
        if (!hasAuthority(caller, "parcel:read-all") && !parcel.customerId().toString().equals(caller.getName())) {
            throw new AccessDeniedException("Not your parcel");   // → 403
        }
        return parcel;
    }
}
```
Full working example: `backend/src/main/java/com/swiftdrop/backend/delivery/DeliveryController.java`.

**Our database stores only the user id** (`customer_id`, `courier_id`, … as UUID). Never store passwords, and don't
copy the users table: the auth service owns identities. Extra data we need (phone, addresses) goes in our own
tables, keyed by the user id.

## 4. Testing a backend feature (no login needed)
Tests fake a token with the same claims the auth service sends:
```java
mockMvc.perform(get("/api/parcels/{id}", id)
        .with(jwt().jwt(t -> t.subject(customerId.toString()))
                   .authorities(new SimpleGrantedAuthority("parcel:read-own"))))
       .andExpect(status().isOk());
```
Always add the **"someone else's data" test** (another user → 403). Example: `DeliveryAccessTest.java`.

## 5. Calling our API from Postman / curl
Log in through the API to get a token, then send it:
```powershell
# 1. Log in (use your own test account); copy "access_token" from the answer
curl.exe -s -H "Content-Type: application/json" --data-binary "@login.json" https://auth.getswiftdrop.me/api/v1/auth/login
#    login.json: {"email":"you+courier@gmail.com","password":"your passphrase"}

# 2. Call our backend with it (valid 15 minutes)
curl.exe -H "Authorization: Bearer <access_token>" http://localhost:8080/api/deliveries/mine
```
Never paste tokens into chats, issues or commits. They are passwords for 15 minutes.

## 6. Building a frontend feature
Use the helpers from `src/features/auth/`:
```ts
import { authorizedFetch } from '../auth/api/authApi';       // adds the token, refreshes it when it expires
import { getAccessToken, readClaims } from '../auth/lib/session';

const response = await authorizedFetch(`${env.apiUrl}/api/parcels`, { method: 'POST', body: JSON.stringify(parcel),
  headers: { 'Content-Type': 'application/json' } });

const canAssign = readClaims(getAccessToken()!).entitlements?.includes('parcel:assign');   // show/hide buttons
```
Hiding a button is only convenience: **the backend is what enforces the rule** (a user who types the URL still gets
403). New pages go in `src/features/<feature>/pages/`, their API calls in `src/features/<feature>/api/`.

## 7. Settings
| Where | Setting | Default |
|---|---|---|
| backend `application.properties` | `SWIFTDROP_AUTH_ISSUER` | `https://auth.getswiftdrop.me` |
| backend | `SWIFTDROP_ALLOWED_ORIGINS` | `http://localhost:5173` |
| frontend `.env` (copy `.env.example`) | `VITE_AUTH_URL`, `VITE_API_URL` | the auth service, `http://localhost:8080` |

The full API of the auth service: `openapi.json` from the auth repo (import into Swagger UI or Postman).
