# SwiftDrop Admin Console

Angular 20 app for SwiftDrop administrators. It starts with the Member 9 pages (Notification Service and
Webhook Delivery Service); other members add their own admin pages to the same shell.

Same conventions as `merchant-portal`: standalone components, signals, `@if`/`@for`, lazy routes, reactive
forms, typed `HttpClient`, `OnPush`, no UI library (design tokens in `src/styles.css`), Karma + Jasmine.

## Run

From `frontend/`:

```bash
npm ci
npm run start:admin          # ng serve admin-console -> http://localhost:4202 (uses proxy.conf.json)
npm run build:admin          # production build -> dist/admin-console
npx ng test admin-console --watch=false --browsers=ChromeHeadless
npx ng lint admin-console
```

`ng serve` proxies (see `proxy.conf.json`):

| Path                          | Target                  |
| ----------------------------- | ----------------------- |
| `/api/v1/auth`                | `http://localhost:8081` |
| `/api/v1/dev`                 | `http://localhost:8083` |
| `/api/v1/admin/notifications` | `http://localhost:8088` |
| `/api/v1/admin/webhooks`      | `http://localhost:8089` |

Container: `docker build -f Dockerfile.admin-console -t swiftdrop/admin-console .` (from `frontend/`).
nginx serves the build and proxies `/api/` to `API_UPSTREAM` (compose sets `http://gateway:8080`).
Pass `--build-arg NG_CONFIGURATION=development` to keep the dev sign-in in the image.

## Sign-in

- `POST /api/v1/auth/login {email,password}` -> `{accessToken, refreshToken, expiresIn, user}`.
- Dev sign-in (only when `environment.devLogin` is true, i.e. development builds):
  `POST /api/v1/dev/tokens {"sub":"admin-1","role":"ADMIN"}` -> `{accessToken, expiresIn}`.
- The token lives in memory and `sessionStorage` and is sent as `Authorization: Bearer ...`.
  A 401 tries one refresh, then returns to `/login`. Only role `ADMIN` gets past `adminGuard`;
  other roles see `/not-authorised`.

## Routes

| Route                                | Page                                                        |
| ------------------------------------ | ----------------------------------------------------------- |
| `/login`                             | Sign in (and dev sign-in)                                   |
| `/not-authorised`                    | Signed in without the ADMIN role                            |
| `/admin/notifications`               | Notification log (filters, paging, Resend on FAILED)        |
| `/admin/notifications?tab=sms`       | Mock SMS inbox                                              |
| `/admin/notifications?tab=templates` | Templates (edit subject, body, active)                      |
| `/admin/webhooks`                    | Webhook deliveries (filters, paging, details, Replay)       |

## Endpoints used

Notification Service:

- `GET /api/v1/admin/notifications?status=&channel=&eventType=&from=&to=&page=&size=` (dates are sent as
  UTC day boundaries, e.g. `2026-11-01T00:00:00Z` .. `2026-11-14T23:59:59.999Z`)
- `POST /api/v1/admin/notifications/{id}/resend`
- `GET /api/v1/admin/notifications/sms-inbox?page=&size=`
- `GET /api/v1/admin/notifications/templates`
- `PUT /api/v1/admin/notifications/templates/{id}` with `{subject, body, active}`

Webhook Delivery Service:

- `GET /api/v1/admin/webhooks/deliveries?status=&merchantId=&eventType=&page=&size=`
- `GET /api/v1/admin/webhooks/deliveries/{id}` (headers, payload, response snippet, attempt log)
- `POST /api/v1/admin/webhooks/deliveries/{id}/replay` (asks for confirmation first)

Errors `{status, code, message, correlationId}` are shown with their `message` (5xx get a generic text).

## Adding your admin pages

1. Create your page under `src/app/features/<your-area>/`.
2. Add a lazy child route under the `admin` route in `src/app/app.routes.ts` (it inherits `adminGuard` and
   the shell layout).
3. Add a section with your links to `ADMIN_NAV` in `src/app/layout/admin-nav.ts`.
4. If your service needs its own dev proxy, add it to `proxy.conf.json`.
