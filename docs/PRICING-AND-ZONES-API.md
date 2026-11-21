# Pricing and delivery zones API (admin)

The admin's Pricing and Delivery Zones pages. Every call needs the **ADMIN** role (`hasRole('ADMIN')`). Errors use
the shared problem JSON (`docs/ERRORS.md`): field errors as `errors.<field>`.

## Pricing (pricing module)
| Call | What |
|---|---|
| `GET /api/admin/pricing` | The tariff quotes use now: `{ "version", "publishedAt", "publishedBy", "rates": { … } }`. |
| `POST /api/admin/pricing` `{ "distanceBase", "includedKm", "perKm", "sameDistrictBase", "otherDistrictBase", "additionalKg", "expressSurcharge", "sameDaySurcharge" }` | Publish the next version → `201`, same shape. |

- **Fee = `distanceBase` + `perKm` × road km above `includedKm` + `additionalKg` × kg of total weight above the
  first + surcharge** (Express, Same day; none for Standard). All LKR except `includedKm` (km).
- **Road distance: Google Maps Routes API** (`directions/v2:computeRoutes`, driving, no traffic, region `lk`), asked
  by the server with the full pickup and destination addresses ("street, city, X District, postal code, Sri Lanka").
  The client never sends a distance. Key: `swiftdrop.maps.google.api-key` (env `GOOGLE_MAPS_API_KEY`); enable
  "Routes API" for it in Google Cloud and restrict it to that API. Answers are cached for 24 h per address pair, so
  an estimate and its booking cost one Google call and get the same fee.
- **Fallback:** with no key, Google failing (3 s connect / 5 s read timeout), no route, or a route over
  `swiftdrop.maps.max-route-km` (800; a wrongly placed address), the district base (`sameDistrictBase` /
  `otherDistrictBase`) replaces `distanceBase` + per-km charge. A quote never fails because of Google.
- Quote (`POST /api/customers/me/deliveries/estimate`): `{ "currency", "distanceKm", "routeFrom", "routeTo",
  "baseFee", "distanceCharge", "weightCharge", "serviceCharge", "totalFee", "explanation" }`.
  `distanceKm` is `null` on the fallback. `routeFrom` / `routeTo` are the addresses exactly as
  Google was asked; the booking page links them to Google Maps directions so anyone can check the km.
- **Version 0** = nothing published yet: the server's settings apply (`swiftdrop.customer.pricing.*`, by default
  250 for the first 5 km, 6 per km, fallback 350 / 650, 120 per kg, 350 / 650 surcharges). `publishedAt` and
  `publishedBy` are then `null`.
- Each publish adds a row to `pricing_version` (V16; distance columns V18, which gave older versions the default
  distance rates); old versions are kept. Rates: required, 0 to 999,999.99 (`includedKm` under 10,000), at most
  2 decimals → `400`. Two admins publishing at the same moment: the second gets `409`, publish again.
- **Bookings keep their fee.** `delivery.estimated_fee` is stored when the booking is made, so a new tariff never
  reprices a parcel. New quotes (`POST /api/customers/me/deliveries/estimate`) and bookings use it at once; the
  quote's `explanation` names the version.
- The customer module reads rates through the pricing module's front door (`TariffSource`). Pricing can't live in
  dispatcher: dispatcher already uses customer, so customer using dispatcher would be a circle.

## Delivery zones (dispatcher module)
| Call | What |
|---|---|
| `GET /api/admin/zones` | Every zone by name: `[{ "id", "name", "code", "districts", "active" }]`. |
| `POST /api/admin/zones` `{ "name", "code", "districts": ["Colombo", …], "active": true }` | Create → `201`. |
| `PUT /api/admin/zones/{id}` (same body) | Edit. |

- Name: required, max 60. Code: 2 to 4 letters, stored in capitals, unique → `409`.
- Districts: at least one, only Sri Lanka's 25 (any letter case; returned in province order) → `400` for others.
  Each district is in **one zone at most** → `409` naming the district and its zone. To move a district, take it
  out of its zone first.
- Tables `delivery_zone` and `delivery_zone_district` (V17); the UNIQUE `district` column backs the one-zone rule.
- **Zones don't change prices or bookings yet.** What an inactive zone should do (refuse bookings there? tie couriers
  to zones?) is still a team decision.

Tests: `AdminPricingApiTest`, `AdminZoneApiTest`, `CustomerPricingServiceTest`, `GoogleRoutesDistanceSourceTest`.
