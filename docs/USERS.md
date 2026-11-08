# Users: where to get what you need

Login, names, emails and roles belong to our **auth service**. SwiftDrop keeps a read-only copy of people
(`AppUser`). So **don't add your own user table or name/email columns**: get it from the places below.

## The only identity classes you use
```java
import com.swiftdrop.backend.identity.entity.AppUser;          // the person
import com.swiftdrop.backend.identity.service.UserDirectory;   // find people
import com.swiftdrop.backend.identity.event.UserDeleted;       // + UserDeactivated, RoleRevoked
```
Everyone who has called SwiftDrop with a login is in `AppUser` automatically (you never add people yourself).
Everything else in `identity/` is its machinery: a test (`IdentityBoundaryTest`) fails the build if you use it.

## Quick answer: "I need …"

| I need | Get it from | Code |
|---|---|---|
| **who is calling** (their id) | the token | `UUID id = UUID.fromString(jwt.getSubject());` |
| **the caller's name** | the token | `jwt.getClaimAsString("name")` |
| **the caller's email** | the token | `jwt.getClaimAsString("email")` (and `jwt.getClaimAsBoolean("email_verified")`) |
| **the caller as a person** (to link in an entity) | `userDirectory` | `AppUser me = userDirectory.caller(jwt);` |
| **another person** (e.g. a courier) | `userDirectory` | `AppUser courier = userDirectory.require(courierId);` (fails if unknown or blocked) |
| **another person's name / photo** | `AppUser` | `person.getName()`, `person.getPictureUrl()` (can be empty) |
| **is this person allowed to work?** | `AppUser` | `person.isActive()` |
| **does this person have a role?** | `AppUser` | `person.hasRole("COURIER")` |
| **all couriers** (e.g. for an assign list) | `userDirectory` | `userDirectory.listByRole("COURIER")` |
| **another person's email** (to send them mail) | `userDirectory`, asked live | `userDirectory.email(id)`: only when you really send an email; never store it |
| **what the caller may do** | the token's permissions | `@PreAuthorize("hasAuthority('parcel:create')")` (see `AUTH.md`) |
| **phone, addresses** | the customer module | ask its service, e.g. `customerService.requireCustomer(id)` |
| **a person was deleted / blocked / lost a role** | identity messages | listen to `UserDeleted`, `UserDeactivated`, `RoleRevoked` (see `AUTH.md` section 7) |

> `userDirectory.email(id)` asks the auth service live. On your laptop it only works if you have the directory
> setting (ask Isuru); without it you get `DirectoryUnavailableException`, so handle that (e.g. skip the email).

## Linking a person in your entity
```java
@ManyToOne
@JoinColumn(name = "courier_id")
private AppUser courier;          // a real link to the person; name, photo, roles come with it
```
Your own data about the person (vehicle, availability, notes) goes in **your** table, linked the same way:
```java
@Entity
class CourierProfile {
    @Id UUID id;
    @OneToOne @MapsId private AppUser user;   // same id as the person
    private String vehicleNumber;             // your module's data
}
```

## Don't
- ❌ add `firstName`, `lastName`, `email` columns: they get out of date (people change them in their account)
- ❌ ask the user to type their name or email in your forms: it's already in the token
- ❌ take a user id from the request body or URL for "me": always from the token
- ❌ write to `app_users` or `app_user_roles`: only the identity module does

## Exception: order contacts
The **sender and receiver** of a delivery (name + phone typed in the order form) are **order data**, not accounts:
the receiver may not even use SwiftDrop. Those stay in the delivery's own address rows. Pre-fill the sender from
`jwt.getClaimAsString("name")`.

## Testing
- In tests, make people with `TestPeople.saved(em, "Kasun Silva", "COURIER")` (or `.unsaved(...)` without a database).
- In fake tokens, add the claims you read: `jwt().jwt(t -> t.subject(id.toString()).claim("name", "Test User").claim("email", "t@example.com"))`.

Questions: ask Isuru (identity owner).
