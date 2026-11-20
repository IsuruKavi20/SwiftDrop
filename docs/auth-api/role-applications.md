# Role applications ("Become a courier / dispatcher")

All calls: `Authorization: Bearer <access_token>`

## For the person applying
**Apply**
`POST /api/v1/me/role-applications`
```json
{ "role": "COURIER", "note": "I have a motorbike and a licence" }
```
`role`: `COURIER` or `DISPATCHER`. `note` is optional (max 500). → `201`

**My applications**
`GET /api/v1/me/role-applications` → `200`, a list with `id`, `role`, `status` (`PENDING`, `APPROVED`, `REJECTED`, `WITHDRAWN`).

**Withdraw**
`DELETE /api/v1/me/role-applications/{id}` → `204`

## For the approver
| Who | Approves |
|---|---|
| DISPATCHER (`role:approve-courier`) | COURIER |
| ADMIN (`role:approve-courier`, `role:approve-dispatcher`) | COURIER, DISPATCHER |

**Waiting list**
`GET /api/v1/admin/role-applications?status=PENDING&role=COURIER` → `200`, only the roles you may approve.

**Decide**
`POST /api/v1/admin/role-applications/{id}/decision`
```json
{ "decision": "APPROVE", "reason": "optional, max 500" }
```
`decision`: `APPROVE` or `REJECT`. → `200`

## Rules (the server enforces them)
- Nobody approves their own application.
- One open application per role.
- COURIER and DISPATCHER are never held together.
- After a rejection: apply again after 7 days.
- After approval the new role is in the person's token at their next refresh (within 15 minutes).
