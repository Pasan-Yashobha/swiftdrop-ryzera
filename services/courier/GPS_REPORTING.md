# Courier GPS reporting

`POST /api/v1/courier/location` requires the existing authenticated COURIER or ADMIN role.
The courier identity is always the authenticated subject, including for administrators;
this is not an API for reporting another courier's position.

JSON: `{"lat":6.9,"lng":79.8,"recordedAt":"2026-11-18T00:00:00Z"}`.
Coordinates must be in WGS84 ranges and the device timestamp must be a valid, nonfuture instant.
No parcel identifier, courier identifier, accuracy field, or tracking number is accepted as an ownership claim.
An existing Courier profile is required. Server coordinates are rounded to four decimal places;
Tracking applies its own existing three-decimal privacy rounding.

Success is HTTP 204 with no body, after a positive RabbitMQ publisher confirmation and no
mandatory return. This confirms broker acceptance/routing, not completion by Tracking.
The publisher sends an actual JSON object, not JSON wrapped inside a string, using the M9
version-1 envelope, producer `courier-service`, routing key `courier.location.updated`, and
the configured events exchange (default `swiftdrop.events`). Data contains only
`courierId`, `lat`, `lng`, `recordedAt`. Correlation ID follows the existing request filter.
Messages are persistent; publisher confirms and returns must remain enabled.

A persisted successful-report timestamp and a pessimistic Courier-profile row lock
prevent concurrent successful reports less than ten seconds apart across service instances.
HTTP 429 means wait before reporting again. Broker failure, nack, unroutable delivery,
or a three-second confirmation timeout returns HTTP 503. Connection establishment has
a three-second timeout. No outbox is used for this high-frequency event, as allowed by
the event catalogue. Publication and database commit are not atomic: a lost confirmation,
lost HTTP response, or database failure after publication can leave a report delivered
without API success. A manual retry can therefore duplicate a position; there is no
exactly-once guarantee. No automatic publisher retry or background location collection is added.

The PWA requests one fresh browser position only after pressing **Report my location**.
It uses a ten-second GPS timeout, prevents overlapping actions and imposes a ten-second
cooldown after success. It does not request permission on page load, use watchPosition,
store location coordinates in the database, or log coordinates. Production geolocation
requires HTTPS; browsers normally allow localhost during development.

Deployment requires migration V3, a working RabbitMQ connection, permission to publish to
the configured exchange, and the existing Tracking queue binding `courier.location.#`.
No shared broker definitions or other services need changing for this implementation.
Unit tests mock RabbitMQ; they do not demonstrate live delivery to M8. Verify manually
with noncustomer test accounts: report a position, observe 204, then confirm the M8
projection for an assigned parcel in an allowed location-sharing status. Confirm 503
when the broker is down and no success message in the PWA.

## Deployment boundary review

See [DEPLOYMENT.md](DEPLOYMENT.md): the current shared nginx permissions policy denies
geolocation, and production needs a secure context plus explicit user permission.
Non-dev Courier now requires Identity JWKS and validates the configured JWT issuer.
Publisher unit/contract tests and fixture UI screenshots do not prove a live broker
ACK, M8 consumption or physical-device GPS accuracy.
