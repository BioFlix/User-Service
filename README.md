# user-service

Identitet + auth. Validerer Auth0-tokens (JWKS), ejer brugertabellen og publicerer `user.*`-events,
så de andre services (fx notification-service) kan holde deres egen kopi af kontaktoplysninger.

Port **8081** · PostgreSQL `users_db` · RabbitMQ (producer) · Auth0.
Datamodellen følger `cinema-data-model.html` (afsnittet *users database*).

## Kør lokalt

```bash
docker compose up --build
```

| Hvad | URL |
|---|---|
| API | http://localhost:8081/api/users/me (header `Authorization: Bearer <token>`) |
| Dev-token | `POST http://localhost:8081/api/dev/token` (kun profil `dev`) |
| RabbitMQ UI | http://localhost:15672 (guest / guest) |
| Postgres | `localhost:5435`, db `users_db` (postgres / postgres) |

Compose starter med profilen **`dev`**: tokens udstedes lokalt i stedet for af Auth0, så alt kan afprøves uden en
Auth0-tenant. Brugere med e-mail i `ADMIN_EMAILS` (standard `admin@bioflix.local`) bliver `ADMIN` ved første login.

Køres servicen fra IntelliJ: sæt `SPRING_PROFILES_ACTIVE=dev` (eller Auth0-variablerne nedenfor) og sørg for at
Postgres og RabbitMQ kører (`DB_HOST`, `DB_PORT`, `RABBITMQ_HOST`, `RABBITMQ_PORT`).
Kører notification-service’s compose allerede RabbitMQ på 5672, så start kun databasen herfra:
`docker compose up -d user-db` og sæt `DB_PORT=5435` – så bruger begge services samme RabbitMQ.

### Prøv det af
```bash
# 1. Hent et token (som hvis Anna loggede ind via Auth0)
TOKEN=$(curl -s -X POST localhost:8081/api/dev/token -H 'Content-Type: application/json' \
  -d '{"sub":"auth0|anna","email":"anna@example.com","name":"Anna Hansen"}' | jq -r .access_token)

# 2. Første kald opretter brugeren -> user.registered
curl -H "Authorization: Bearer $TOKEN" localhost:8081/api/auth/validate

# 3. Opdater profil -> user.updated
curl -X PUT -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' localhost:8081/api/users/me \
  -d '{"email":"anna@example.com","fullName":"Anna Hansen","phone":"+4512345678","smsOptIn":true}'

# 4. Slet konto -> user.deleted
curl -X DELETE -H "Authorization: Bearer $TOKEN" localhost:8081/api/users/me
```
Kører notification-service mod samme RabbitMQ, dukker velkomstmailen op i Mailpit (http://localhost:8025).

## Arkitektur

```
 API gateway ──GET /api/auth/validate──► user-service ──► users (Postgres)
                                              │  samme transaktion
                                              ▼
                                         outbox_events ──OutboxRelay (hvert sekund)──► cinema.events (topic)
                                                                                         ├─► notification-service.events
                                                                                         └─► (andre consumers)
```

### Tabeller
| Tabel | Indhold |
|---|---|
| `users` | `id` (UUID), `auth0_sub` (unik), `email`, `full_name`, `phone`, `role`, `sms_opt_in`, `created_at`, `updated_at`, `deleted_at` |
| `outbox_events` | `id` (= eventId), `aggregate_id` (users.id), `event_type`, `payload` (jsonb, hele envelope’en), `created_at`, `published_at` (+ `attempts`, `last_error`) |

E-mail er unik uden forskel på store/små bogstaver blandt brugere der ikke er slettet
(`users_email_active` i `schema.sql` – et partielt indeks, som Hibernate ikke selv kan lave).
Afvigelse fra HTML-modellen: `role` er `varchar` + check i stedet for en Postgres-enum.

### Login-flow
1. Brugeren logger ind hos Auth0 og får et access token.
2. Gatewayen kalder `GET /api/auth/validate` med tokenet. Spring Security tjekker signatur (JWKS), issuer, audience og udløb.
3. Første gang oprettes brugeren i `users` (+ `user.registered`). Svaret indeholder vores interne `userId` (UUID)
   og `role`, som gatewayen sender videre som `X-User-Id` / `X-User-Role`.

Tokenet skal have e-mail-claimet (se Auth0-opsætning), ellers `401`. Mangler navnet, bruges e-mailen.
Er e-mailen allerede brugt af en anden aktiv bruger, `409`.

Logger samme bruger ind med flere requests på én gang, prøver de alle at oprette brugeren. Unik constraint på
`auth0_sub` sikrer at kun én lykkes – de andre læser bare den række der blev oprettet (én bruger, ét event).

### Transactional outbox
Ændringen i `users` og eventet i `outbox_events` gemmes i **samme transaktion**. `OutboxRelay` sender derefter
uafsendte events til RabbitMQ og markerer dem som sendt, når RabbitMQ har bekræftet modtagelsen (publisher confirms).

| Situation | Hvad sker der |
|---|---|
| RabbitMQ er nede | Requesten lykkes stadig. Eventet bliver i outboxen (`attempts` + `last_error`) og sendes når RabbitMQ er oppe igen. |
| Transaktionen rulles tilbage | Intet event – det blev aldrig gemt. |
| Crash efter send, før "sendt" er gemt | Eventet sendes igen (at-least-once). Consumers er idempotente på `eventId`. |
| Flere instanser af user-service | `FOR UPDATE SKIP LOCKED` – hver instans tager sine egne rækker. |

Events sendes i den rækkefølge de skete; fejler ét, stopper batchen, så et senere event ikke overhaler et tidligere.
Sendte events slettes efter 7 dage (`app.outbox.keep-published-for`).

### Sletning
Soft delete: `DELETE /me` anonymiserer persondata (e-mail → `deleted-<id>@deleted.invalid`, navn → `Deleted user`,
telefon fjernes) men beholder rækken med `deleted_at`, så gamle bookinger stadig peger på en rigtig række.
E-mailen bliver dermed fri til en ny konto. Uden tombstone ville gatewayens næste `validate`-kald med samme
Auth0-token bare oprette brugeren igen – et slettet Auth0-id får `403`. (Brugeren skal også slettes i Auth0 – Management API, ikke lavet endnu.)

## REST-endpoints
Alle kræver `Authorization: Bearer <token>`.

| Metode | Sti | Adgang |
|---|---|---|
| GET | `/api/auth/validate` | Alle (kaldes af gatewayen) – returnerer `userId`, `role` |
| GET / PUT / DELETE | `/api/users/me` | Egen profil (oprettes ved første kald). PUT-body: `{"email","fullName","phone","smsOptIn"}` – `smsOptIn: true` kræver telefonnummer |
| GET | `/api/users` | `ADMIN` |
| GET | `/api/users/{id}` | `ADMIN` eller brugeren selv (`id` er en UUID) |
| PUT | `/api/users/{id}/role` | `ADMIN` – body `{"role":"ADMIN"}` (kan ikke fjerne sin egen admin-rolle) |
| DELETE | `/api/users/{id}` | `ADMIN` |
| POST | `/api/dev/token` | Kun profil `dev` – body `{"sub","email","name"}` |

Rollen gemmes i vores egen `users`-tabel (ikke i Auth0). Første admin: `ADMIN_EMAILS`, derefter `PUT /{id}/role`.

## Events (producer)
Publiceres på topic exchange `cinema.events` med routing key = `type`. Samme envelope som resten af systemet:

```json
{
  "eventId": "e992dda9-fa03-4166-bcef-bfb2fd947c3d",
  "type": "user.updated",
  "version": 1,
  "occurredAt": "2026-11-17T10:24:15.646Z",
  "producer": "user-service",
  "correlationId": "2406aab3-a9c7-41fe-ae40-17ba2e7c1ae5",
  "data": { "userId": "2406aab3-a9c7-41fe-ae40-17ba2e7c1ae5", "email": "anna@example.com",
            "fullName": "Anna Hansen", "phone": "+4512345678", "smsOptIn": true }
}
```

| Event | Hvornår | data |
|---|---|---|
| `user.registered` | Første login | `userId`, `email`, `fullName`, `phone`, `smsOptIn` |
| `user.updated` | `PUT /me` ændrer noget | `userId`, `email`, `fullName`, `phone`, `smsOptIn` (hele den nye tilstand) |
| `user.deleted` | `DELETE /me` eller admin-sletning | `userId` (ingen persondata) |

Felter uden værdi udelades. `user.updated` indeholder altid hele tilstanden, ikke kun det der ændrede sig.

> Bemærk: Er der ingen consumer-kø bundet endnu (fx notification-service har aldrig kørt), smider RabbitMQ eventet
> væk. Kø og bindings oprettes af consumeren selv ved opstart.

## Auth0-opsætning
Sæt `AUTH0_DOMAIN` (fx `my-tenant.eu.auth0.com`) og `AUTH0_AUDIENCE` (jeres API identifier), og kør **uden** profilen `dev`.
Access tokens har ikke e-mail/navn som standard – tilføj dem med en Auth0 *Post-Login Action*:
```js
exports.onExecutePostLogin = async (event, api) => {
  api.accessToken.setCustomClaim('https://cinema.local/email', event.user.email);
  api.accessToken.setCustomClaim('https://cinema.local/name', event.user.name);
};
```

## Konfiguration
| Variabel | Standard |
|---|---|
| `DB_HOST` / `DB_PORT` / `DB_USER` / `DB_PASSWORD` | `localhost` / `5432` / `postgres` / `postgres` |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` / `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | `localhost` / `5672` / `guest` / `guest` |
| `AUTH0_DOMAIN` / `AUTH0_AUDIENCE` | – (påkrævet uden `dev`) |
| `AUTH0_EMAIL_CLAIM` / `AUTH0_NAME_CLAIM` | `https://cinema.local/email` / `https://cinema.local/name` |
| `ADMIN_EMAILS` | tom (compose: `admin@bioflix.local`) |
| `SPRING_PROFILES_ACTIVE` | tom (compose: `dev`) |
| `DEV_JWT_SECRET` | en lokal standardnøgle – kun brugt i `dev` |
| `OUTBOX_POLL_INTERVAL` | `1000` ms |

> Skemaet oprettes med `ddl-auto: update` + `schema.sql`. Har du en gammel `users_db` hvor `id` er et tal,
> så nulstil den én gang (`docker compose down -v && docker compose up -d user-db`) – id’et er nu en UUID.
