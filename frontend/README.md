# SwiftDrop frontend

Angular 20 workspace. Every web app of the platform is a project under `projects/`, so they share one
`package.json`, one lockfile, one lint and one test setup. Today it holds the **merchant portal**
(`projects/merchant-portal`); the other members add their apps next to it.

## Requirements

- Node 22.12 or newer (Node 20.19+ also works) and npm 10.
- Google Chrome (or Chromium) for the unit tests. Point `CHROME_BIN` at it when it is not on the default path.

## Workspace layout

```
frontend/
  angular.json                 one entry per project (build, serve, test, lint)
  package.json                 shared scripts and dependencies
  proxy.conf.json              dev-server proxy to the backend (see "How the portal talks to the backend")
  eslint.config.js             shared lint rules (Angular + template accessibility rules)
  Dockerfile.merchant-portal   production image of the merchant portal
  nginx/                       nginx config template of that image
  projects/
    merchant-portal/
      src/
        index.html, main.ts, styles.css      design tokens and shared building blocks (.btn, .card, .table, ...)
        environments/                        environment.ts (development) and environment.production.ts
        app/
          app.ts, app.routes.ts, app.config.ts
          layout/                            header, skip link, footer
          core/                              auth, HTTP interceptors, API services, models, guards, merchant store
          shared/                            reusable UI (dialogs, toast, form field, badges, ...), forms, pipes, utils
          features/                          one folder per page group: auth, onboarding, status, profile,
                                             dashboard, orders, developers (API keys, webhooks, docs)
```

Conventions: standalone components, signals, new control flow, lazy-loaded routes, `OnPush` everywhere, reactive
forms, `inject()` instead of constructor injection. Selectors start with `app-`. Pages are `*.page.ts` classes
that end in `Page`. Each feature keeps its own components, helpers and specs in its own folder; code that more than
one feature needs lives in `shared/`.

## Commands

Run everything from this folder.

| Task | Command |
|---|---|
| Install exactly what the lockfile says | `npm ci` |
| Merchant portal dev server with the backend proxy (http://localhost:4200) | `npm run start:merchant` |
| Unit tests once, headless (what CI runs) | `npm test -- --watch=false --browsers=ChromeHeadless` |
| Unit tests while developing | `npm test` |
| Lint (TypeScript and templates, including accessibility rules) | `npm run lint` |
| Format with Prettier | `npm run format` |
| Production build of every project into `dist/` | `npm run build` |
| Production build of the merchant portal only | `npm run build:merchant` |
| Development build (no optimisation, source maps) | `npx ng build merchant-portal --configuration development` |

On Windows PowerShell set Chrome for the tests with
`$env:CHROME_BIN='C:\Program Files\Google\Chrome\Application\chrome.exe'`; in Git Bash use
`export CHROME_BIN='C:\Program Files\Google\Chrome\Application\chrome.exe'`.

### Docker image

```bash
docker build -f Dockerfile.merchant-portal -t swiftdrop/merchant-portal .
docker run -p 4201:8080 -e API_UPSTREAM=http://merchant:8080 swiftdrop/merchant-portal
```

- The build context is this folder. `NG_CONFIGURATION` selects the Angular configuration and defaults to
  `production`: `docker build --build-arg NG_CONFIGURATION=development ...`. The root `docker-compose.yml` passes
  `development` (variable `MERCHANT_PORTAL_CONFIGURATION`) so the developer sign-in works before Identity exists.
  Never ship a `development` image.
- The runtime image is `nginxinc/nginx-unprivileged`: it runs as a non-root user and **listens on 8080**, so map a
  host port to 8080 (compose uses 4201).
- Run-time settings are environment variables, substituted into the nginx template at start-up:
  `API_UPSTREAM` (default `http://merchant:8080`, no path and no trailing slash) and `DNS_RESOLVER` (default
  `127.0.0.11`, Docker's embedded DNS; use the cluster DNS service in Kubernetes). No secret is baked into the image.
- `GET /healthz` answers `200 ok` for health checks.

## Environments

The Angular build swaps `src/environments/environment.ts` for `environment.production.ts` in the `production`
configuration. Both have the same shape (`AppConfig` in `core/config/app-config.ts`):

| Setting | development | production | Meaning |
|---|---|---|---|
| `apiBase` | `/api/v1` | `/api/v1` | Relative base URL of every API call. There is no host in the bundle. |
| `devLogin` | `true` | `false` | Shows the "Developer sign-in" panel on the login page. It signs in through the merchant service's dev token endpoint (`POST /api/v1/dev/tokens`, only present in its `dev` profile) so the portal can be used before Identity exists. |

The portal reads its configuration from the injected `APP_CONFIG` token, never from `environment` directly, so
specs can override it. With `devLogin: false` the panel is not rendered and nothing calls the dev token endpoint.

Browser storage: the session (access token, refresh token, user) lives in memory and in `sessionStorage` under
`swiftdrop.merchant-portal.session`, which ends with the tab. Nothing is written to `localStorage`, cookies or the
URL, and one-time secrets (API keys, client secrets, webhook secrets) are held only while their dialog is open.

## How the merchant portal talks to the backend

Every request goes to the **same origin** under `/api/v1`, so the browser needs no CORS and the bundle needs no
host name.

- **In development** (`npm run start:merchant`) the dev server proxies according to `proxy.conf.json`:
  `/api/v1/auth` goes to `http://localhost:8080` (Identity or the gateway) and everything else under `/api` goes to
  `http://localhost:8083` (merchant service). The more specific `/api/v1/auth` entry must stay first.
- **In the container** nginx serves the build and reverse-proxies `/api/` to `API_UPSTREAM` with the request path
  unchanged (`/api/v1/merchants/me` reaches the API as `/api/v1/merchants/me`). The upstream is resolved at request
  time, so the portal can start before the API. Unknown paths fall back to `index.html` (client-side routes), hashed
  build files are cached for a year and `index.html` never is, and the responses carry a restrictive
  Content-Security-Policy plus the usual security headers (`script-src 'self'`; styles may be inline because Angular
  injects component styles at run time).
- **Authentication**: Identity's `POST /api/v1/auth/login`, `/signup` and `/refresh` are wrapped by one class,
  `core/auth/auth-api.service.ts`. The access token is attached by `auth.interceptor.ts`; a `401` triggers one
  token refresh and then a redirect to the login page. Route guards (`core/guards`) send a user without a
  merchant to `/onboarding` and an unapproved merchant to `/status`.
- **Merchant API calls** live in four typed services under `core/api` (`MerchantApi`, `CredentialApi`, `OrderApi`,
  `WebhookApi`). Their request and response types are in `core/models` and mirror `docs/merchant-service/CONTRACT.md`.
- **Errors**: `error.interceptor.ts` turns every failed call into an `AppError` (status, code, a message that is safe
  to show, field details, correlation id). Components show it with `<app-error-alert>`; they never print raw server
  text.

## Adding another app to the workspace

1. Generate it from this folder: `npx ng generate application <name> --style=css --routing --ssr=false`
   (use the project name the team agreed on, for example `customer-app`). The CLI adds the `angular.json` entry and
   the project folder under `projects/<name>`.
2. Give it the same targets as `merchant-portal` in `angular.json`: a `test` target with `zone.js/testing`
   polyfills, and a `lint` target that points at `projects/<name>/eslint.config.js`. Copy that file from
   `merchant-portal` (it extends the root config) and keep the `app` selector prefix or choose your own.
3. Add the project's tsconfig files to the `references` list in the root `tsconfig.json`.
4. Add npm scripts next to `start:merchant` / `build:merchant`, for example `"start:customer"` and
   `"build:customer"`. `npm run lint`, `npm test` and `npm run build` already run every project.
5. If it is deployed as a container, add `Dockerfile.<name>` (copy `Dockerfile.merchant-portal` and change the
   project name) and an nginx template. Each app needs its own image because its build output and proxy rules differ.
6. Add its own dev-server proxy entries to `proxy.conf.json` only if it needs different routes; otherwise reuse the
   file. Keep longer, more specific prefixes before shorter ones.
7. Shared code between apps should go into a library (`npx ng generate library shared-ui`) instead of being copied.
   The merchant portal's `shared/` folder is a good starting point to extract from, but it is an app-internal folder
   today.

Keep the workspace green: `npm ci`, `npm run lint`, `npm test -- --watch=false --browsers=ChromeHeadless` and
`npm run build` must all pass before merging.

## Quality notes

- The unit tests run in real Chrome (Karma and Jasmine) and cover services, interceptors, guards, shared
  components and every page. Add a spec with each change.
- Accessibility is part of the definition of done: every control has a label, tables have captions, dialogs use the
  native `<dialog>` element (focus trap, Escape, focus return), colour is never the only signal (status badges also
  carry a symbol and a word), headings stay in order with one `h1` per page, and results of asynchronous actions are
  announced through a live region or by moving focus to them. After a page change focus moves to the main area;
  list filters and in-page links keep focus where it is.
- Never use `innerHTML` or `bypassSecurityTrust*`, and never log secrets or tokens.
