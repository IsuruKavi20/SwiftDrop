# Errors: one format for the whole API

Every error from the SwiftDrop backend is JSON in the same shape (RFC 9457, like the auth service):

```json
{ "type": "https://swiftdrop.dev/problems/not-found", "title": "Not Found", "status": 404,
  "detail": "Delivery not found", "code": "NOT_FOUND", "requestId": "1f3c9a2e-..." }
```

| Field | Use it for |
|---|---|
| `code` | your program's `if`/`switch` (stable) |
| `detail` | the sentence to show the user |
| `errors` | only on `VALIDATION_FAILED`: `{ "field": "message" }`, show it under each form field |
| `requestId` | show it small on error screens; it finds the exact server log lines |

## Codes
| Status | code | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | a `@Valid` body is wrong (see `errors`) |
| 400 | `BAD_REQUEST` | `IllegalArgumentException`, unreadable JSON |
| 401 | `UNAUTHENTICATED` | no token, expired or invalid token → log in again / refresh |
| 403 | `FORBIDDEN` | logged in, but no permission |
| 404 | `NOT_FOUND` | not there, or not yours (same answer on purpose) |
| 405 / 415 | `METHOD_NOT_ALLOWED` / `UNSUPPORTED_MEDIA_TYPE` | wrong HTTP method / content type |
| 409 | `CONFLICT` | clashes with current state (already registered, ...) |
| 503 | `SERVICE_UNAVAILABLE` | a service we depend on is down; try again |
| 500 | `INTERNAL_ERROR` | a bug; the details are only in the log |

## Backend: how to use it (any module)
Just throw, no handler needed:
```java
throw new NotFoundException("Delivery not found");          // com.swiftdrop.backend.shared.error
throw new ConflictException("This delivery is already confirmed");
throw new IllegalArgumentException("Weight must be positive");
```
- Don't write your own `@RestControllerAdvice`; `shared/error/ApiExceptionHandler` covers every module.
- Never put internals in a message (SQL, class names, other people's data). Unknown errors already say only
  "Something went wrong" and log the details.
