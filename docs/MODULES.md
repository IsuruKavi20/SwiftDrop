# Modules: who may use what

The backend is one app made of **modules** (one folder each under `com.swiftdrop.backend`). A test
(`ModularityTest`, Spring Modulith) checks the rules on every PR. If it is red, CI blocks the merge.

## The rules
1. Use another module only through its **front door** (below). Everything else in it is private.
2. **No circles:** if A uses B, B may not use A. To tell another module "something happened", **publish an event**
   and let it listen (see `CustomerStatusChange` → `notification/service/CustomerStatusListener`).
3. `shared` and `security` are open to everyone and must not use any business module.

## Front doors
| Module | Others may use | Used by |
|---|---|---|
| identity | `entity` (AppUser), `UserDirectory`, `event`, `exception` | customer, dispatcher, merchant |
| customer | `entity`, the `CustomerStatusChange` event | dispatcher, merchant, notification |
| merchant | `entity` (Merchant), the `MerchantStatusChange` event | notification |
| dispatcher | nothing yet | |
| notification | nothing (it only listens) | |
| shared, security | everything (open) | all |

A front door is marked in `package-info.java` with `@NamedInterface` (or on one class, like `UserDirectory`).
To open more of your module: add it there **and** say why in the PR.

## Known shortcuts (fix later)
- dispatcher and notification use customer's **entities** (database tables) directly. Better: customer offers a small
  read API or events, and each module keeps the data it needs.

## Picture
`docs/modules/all-modules.puml` (open with a PlantUML viewer). It is drawn from the real code: run
`./mvnw test -Dtest=ModularityTest` and copy `target/spring-modulith-docs/components.puml` to update it.

## When the test fails
The message names the file and the rule, e.g. *"Module 'dispatcher' depends on non-exposed type
...customer.repository.CustomerDeliveryRepository"*. Use the front door instead, publish/listen to an event, or ask
the module owner to open a front door.
