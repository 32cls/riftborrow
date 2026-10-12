# AGENTS.md

## Repo

- Standalone repo (remote: `github.com/32cls/riftbound`): "Riftborrow", a Riftbound card-collection/lending API forked from the Quarkus `getting-started-dev-services` quickstart.
- This directory is nested inside an unrelated `quarkus-quickstarts` checkout. Ignore the parent folder's README, CI, and config — they do not apply here.
- `README.md` is stale quickstart boilerplate. Trust `pom.xml` and `src/main/resources/application.properties`.
- Stack: Quarkus 3.39.1, Java 17 compiler release (builds fine on newer JDKs), single Maven module (no monorepo), Panache + Postgres/PostGIS, REST + Jackson + Bean Validation. No lint/formatter/typecheck tooling — compile/test is the check.

## Commands (Windows: use `mvnw.cmd`, not `./mvnw`)

- Dev mode (live reload; Dev UI `http://localhost:8080/q/dev/`, OpenAPI `/q/openapi/`): `mvnw.cmd quarkus:dev`
- Unit tests: `mvnw.cmd test` · single test: `mvnw.cmd test -Dtest=CardReferenceClientLookupTest`
- Integration tests are skipped by default (`skipITs=true` in pom): `mvnw.cmd verify -DskipITs=false`
- Package: `mvnw.cmd package` → `target/quarkus-app/quarkus-run.jar` · Native: `mvnw.cmd package -Dnative` (GraalVM or `-Dquarkus.native.container-build=true`)
- The last recorded `test` run (2026-09-28) **failed at Quarkus boot** (all tests skipped), and `GreetingResourceTest` predates the auth migration (it expects 200 on a `@RolesAllowed("user")` endpoint unauthenticated). Don't assume the suite passes.

## Local prerequisites (none of these are in git)

- `.env` at repo root is **gitignored** but auto-loaded by Quarkus, and the app **cannot boot without it**: `MEILI_MASTER_KEY` is a required `@ConfigProperty` (in `ConfigBean`), and `QUARKUS_OIDC_CLIENT_ID` / `QUARKUS_OIDC_CREDENTIALS_SECRET` (Discord app credentials) were moved out of `application.properties` into `.env`. Fresh clones must recreate it.
- Meilisearch is **not** a dev service — start it with `docker compose up -d` (port 7700, data in `./meili_data`, master key from `.env`).
- Postgres **is** a dev service, but pinned: image `postgis/postgis`, **port 5432** (`quarkus.datasource.devservices.*`). If 5432 is taken, startup fails. Docker required.
- The `%prod` profile hardcodes DB credentials in `application.properties` (`leopold`/`bloom`, `localhost:5432/mydatabase`) — override via env vars in real deployments.

## How it's wired

- Auth is a **work-in-progress migration to Discord OIDC** (`quarkus-oidc`, `quarkus.oidc.provider=discord`, userinfo required; git log: "moving auth to discord (doesn't work)"). The old smallrye-jwt cookie flow is gone. `RegisteredUserAugmentor` (new, untracked) auto-creates the `app_users` row on first login (JWT issuer + sub + username) and adds a `userId` attribute (Caffeine-cached 10 min).
- Current-user resolution has two paths — keep both working: resources use `userInfo.getString("id")` → `User.findByDiscordId(...)`; `UserResource` uses `identity.getAttribute("userId")` (set by the augmentor). All endpoints are `@RolesAllowed("user")`; the role comes from `app_users.role` (security-jpa `@UserDefinition`).
- Domain (Panache): `Card` (owner/borrower `User`, `CardReference`), `Deck` (owner, cards), `Borrow` (owner, borrower, cards). `CardReference` is the card catalog.
- `CardReference` bulk-import: a `@Scheduled` cron (every 10 min, `CardReferenceUpdaterBean`) pages `https://api.riftcodex.com/cards` **only when the table is empty** — and that external API has been flaky (git log: "riftcodex down").
- `CardReference` `@PostPersist`/`@PostUpdate` push to the Meilisearch index `card_references` — so **any CardReference persist fails if Meilisearch is down** (including the scheduler import).
- Geo: `User.location` is a PostGIS point (SRID 4326); `Card.findBorrowableCards` uses the custom `distance_meters` SQM function from `PostgisDistanceFunctionContributor`, registered via `src/main/resources/META-INF/services` (not via a Quarkus extension).
- `import.sql` seeds 1 user + 1 cardreference + 2 cards and resets sequences. Quarkus auto-loads it **in dev and test modes, not in prod** (verified in the 3.39.1 config metadata). The seeded row also keeps the scheduler from re-importing in dev/test.
- Card lookups are exact-name matches. Import (`CardReference.fromCardReferenceDto`) and the bulk list upload normalize names (strip `,` and ` -`), but `CardResource.addCard` does not — keep the two sides in sync.

## Gotchas

- `quarkus-hibernate-search-orm-elasticsearch` is in the pom but no entity is indexed — apparently unused (removal candidate; it may still start an Elasticsearch dev service, which would add a Docker dependency).
- Root-level `io/` and `META-INF/` dirs are untracked leftovers from jar extraction, not source.
- `quarkus.log.level=DEBUG` is global — expect noisy startup logs; don't read log volume as an error.
