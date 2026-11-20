# Admin portal

All calls: `Authorization: Bearer <the admin's own access_token>`.
Call auth directly from the browser with the admin's token (never through a SwiftDrop backend login): auth records
which admin did it.

## Users
| What | Call | Permission |
|---|---|---|
| Find by email | `GET /api/v1/admin/users?email=nimal@example.com` | `user:read` |
| One user | `GET /api/v1/admin/users/{userId}` | `user:read` |
| Block | `POST /api/v1/admin/users/{userId}/block` `{ "reason": "..." }` | `user:manage-status` |
| Unblock | `POST /api/v1/admin/users/{userId}/unblock` `{ "reason": "..." }` | `user:manage-status` |
| Log out everywhere | `POST /api/v1/admin/users/{userId}/sessions/end` `{ "reason": "..." }` | `user:manage-status` |
| Delete | `DELETE /api/v1/admin/users/{userId}` `{ "reason": "..." }` | `user:delete` |
| Give a role | `PUT /api/v1/admin/users/{userId}/roles/{role}` | `user:manage-roles` |
| Remove a role | `DELETE /api/v1/admin/users/{userId}/roles/{role}` | `user:manage-roles` |
| Who has which role | `GET /api/v1/admin/access-review` | `user:read` |

Rules: an admin can't block, delete or remove ADMIN from themselves; the last admin can't be removed.

## Audit log (who did what)
| What | Call |
|---|---|
| Search | `GET /api/v1/admin/audit?actor=&target=&action=&from=&to=&count=50` |
| Next page | add `&before=<seq of the last row>` |
| Download CSV | `GET /api/v1/admin/audit/export?from=2026-11-01T00:00:00Z&to=2026-11-30T00:00:00Z` |
| Check nothing was changed | `GET /api/v1/admin/audit/verify` |

Permission: `audit:read`. Dates in ISO format (`2026-11-13T00:00:00Z`).

## Merchant approvals
| What | Call |
|---|---|
| Waiting list | `GET /api/v1/admin/merchants?status=PENDING` |
| Decide | `POST /api/v1/admin/merchants/{merchantId}/decision` `{ "decision": "APPROVE" }` (or `REJECT` with `"reason"`) |
| Suspend / reinstate | `POST /api/v1/admin/merchants/{merchantId}/status` `{ "change": "SUSPEND" }` (or `REINSTATE`) |

Permission: `merchant:approve`.
