# Pricing and delivery zones API (admin)

The admin's Pricing and Delivery Zones pages. Every call needs the **ADMIN** role (`hasRole('ADMIN')`). Errors use
the shared problem JSON (`docs/ERRORS.md`): field errors as `errors.<field>`.

## Pricing (pricing module)
| Call | What |
|---|---|
| `GET /api/admin/pricing` | The tariff quotes use now: `{ "version", "publishedAt", "publishedBy", "rates": { … } }`. |
| `POST /api/admin/pricing` `{ "sameDistrictBase", "otherDistrictBase", "additionalKg", "expressSurcharge", "sameDaySurcharge" }` | Publish the next version → `201`, same shape. |

- Fee = base (same district or not) + `additionalKg` × kg of total weight above the first + surcharge (Express,
  Same day; none for Standard). All LKR.
- **Version 0** = nothing published yet: the server's settings apply (`swiftdrop.customer.pricing.*`, by default
  350 / 650 / 120 / 350 / 650). `publishedAt` and `publishedBy` are then `null`.
- Each publish adds a row to `pricing_version` (V16); old versions are kept. Rates: required, 0 to 999,999.99, at most
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

Tests: `AdminPricingApiTest`, `AdminZoneApiTest`, `CustomerPricingServiceTest`.
