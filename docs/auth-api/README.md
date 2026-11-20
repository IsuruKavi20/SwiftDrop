# Auth service API (for SwiftDrop teammates)

Base URL: `https://auth.getswiftdrop.me`

| File | For |
|---|---|
| [login.md](login.md) | sign up, log in, Google, refresh, log out, forgot password |
| [my-account.md](my-account.md) | the logged-in person: profile, password, email, export, delete |
| [role-applications.md](role-applications.md) | "Become a courier / dispatcher" + approving them |
| [admin.md](admin.md) | admin portal: users, block, roles, audit log, merchants |
| [merchants.md](merchants.md) | merchant application and merchant API keys |
| [merchant-screens.md](merchant-screens.md) | which merchant screen calls which API (frontend guide) |

## Rules for every call
- Use the JSON field names **exactly as written** in these files (most are snake_case like `refresh_token`).
- Logged-in calls send the access token: `Authorization: Bearer <access_token>`.
- Errors come as JSON with `status`, `code` and `detail`. `401` = not logged in, `403` = no permission.
- Never put tokens in logs, chats or screenshots.

## Roles and permissions
| Role | Permissions |
|---|---|
| CUSTOMER | `parcel:create`, `parcel:read-own` |
| MERCHANT | `parcel:create`, `parcel:read-own`, `merchant:api-access` |
| COURIER | `delivery:read-own`, `delivery:update-status` |
| DISPATCHER | `parcel:read-all`, `parcel:assign`, `delivery:read-all`, `role:approve-courier` |
| ADMIN | everything DISPATCHER has + `merchant:approve`, `user:read`, `user:manage-roles`, `user:manage-status`, `user:delete`, `audit:read`, `role:approve-dispatcher` |
