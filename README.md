# user-service

Identitet + auth. Validerer Auth0-tokens (JWKS) og ejer brugertabellen. Port **8081**, PostgreSQL `users_db`.

## Endpoints (kræver `Authorization: Bearer <Auth0 access token>`)
| Metode | Sti | Beskrivelse |
|---|---|---|
| GET | `/api/auth/validate` | Kaldes af API gateway – returnerer intern `userId` |
| GET / PUT / DELETE | `/api/users/me` | Egen profil (oprettes ved første kald) |
| GET | `/api/users`, `/api/users/{id}` | TODO: kun admin |

## Events (når RabbitMQ-repoet er klar)
Skal publicere `user.registered`, `user.updated`, `user.deleted` – se `TODO (RabbitMQ)` i `UserService`.

## Auth0-opsætning
Sæt miljøvariablerne `AUTH0_DOMAIN` (fx `my-tenant.eu.auth0.com`) og `AUTH0_AUDIENCE` (jeres API identifier).
Access tokens har ikke e-mail/navn som standard – tilføj dem med en Auth0 *Post-Login Action*:
```js
exports.onExecutePostLogin = async (event, api) => {
  api.accessToken.setCustomClaim('https://cinema.local/email', event.user.email);
  api.accessToken.setCustomClaim('https://cinema.local/name', event.user.name);
};
```

## Docker
```
docker build -t bioflix-user-service .
```
GitHub Actions (`.github/workflows/docker-image.yml`) bygger billedet ved hver push og pusher det til Docker Hub
som `<namespace>/bioflix-user-service:latest`, når secrets `DOCKERHUB_USERNAME` og `DOCKERHUB_TOKEN` er sat.
