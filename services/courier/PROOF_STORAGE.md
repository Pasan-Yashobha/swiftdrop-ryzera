# Courier private proof storage (Stage A)

The existing `ProofStorageService` selects exactly one adapter. `local` remains the
default for development/tests; `s3` and `minio` select `S3ProofStorageService` using
AWS SDK for Java v2 with path-style addressing. Unknown types fail startup.
Selecting S3 never falls back to local storage.

## Configuration

| Spring property under `swiftdrop.storage` | Environment | Meaning |
|---|---|---|
| `type` | `STORAGE_TYPE` | `local` (default), `s3`, or `minio` |
| `local-dir` | `STORAGE_LOCAL_DIR` | Filesystem directory; unchanged local behavior |
| `s3-endpoint` | `S3_ENDPOINT` | S3 origin; default `http://localhost:9000` |
| `s3-bucket` | `S3_BUCKET` | Dedicated private bucket; default `proof-photos` |
| `s3-region` | `S3_REGION` | Signing region; default `us-east-1` |
| `s3-access-key` | `S3_ACCESS_KEY` | Required for S3; no credential default |
| `s3-secret-key` | `S3_SECRET_KEY` | Required for S3; no credential default |

Supply credentials through the local environment or the deployment secret store.
Do not put them in this repository or command logs. The adapter does not consult
the SDK's ambient credential chain. Configuration errors identify property names,
never credential values. Its configuration string representation redacts credentials.
Endpoint URIs cannot contain embedded credentials, paths, query strings or fragments.
HTTPS is required except for loopback (`localhost`, `127.0.0.1`, `[::1]`) development.
Container-to-container endpoints therefore need TLS; this stage does not add an
insecure remote-HTTP override.

## Provisioning and privacy (M1 deployment work)

Provision MinIO/S3 and a dedicated private bucket **before uploading proofs**. No
bucket, policy, ACL, shared Compose or deployment configuration is created/changed
by Courier. Provision a least-privilege identity scoped to that bucket with
`s3:GetBucketPolicy`, `s3:PutObject`, and `s3:GetObject` permissions
(object operations limited to `proofs/*`). AWS additionally needs `s3:GetBucketAcl`.
MinIO's legacy ACL handler authorizes that read through `s3:GetBucketPolicy`; do not
add unsupported IAM actions if the chosen MinIO version rejects them.
Do not give Courier policy-write or
bucket-creation permissions. Configure the existing environment keys above and
network/TLS access. For AWS deployments, also enable Block Public Access and
bucket-owner-enforced object ownership; ensure no legacy public object ACLs exist.

Before each read/upload, Courier verifies an owner-only bucket ACL (or MinIO's
single CanonicalUser FULL_CONTROL placeholder with empty IDs) and checks the
bucket policy. The placeholder never bypasses the policy check. No bucket policy
is acceptable (`NoSuchBucketPolicy`); policies
granting wildcard/anonymous access, `Allow` with `NotPrincipal`, malformed policies,
and denied/unavailable privacy checks fail closed. Wildcard grants are rejected
even when a condition might constrain them. Explicit authenticated-principal policies
and deny statements are supported. Provisioning must allow the privacy reads; a
403 is never assumed to mean private. Administrators remain responsible for keeping
the bucket private and preventing outside policy/ACL changes.

Uploads use the existing bounded content validation and return only
`proofs/{parcelId}/{randomUUID}.jpg|png|webp`. Caller filenames are never used as
object paths. Conditional writes (`If-None-Match: *`) prevent replacing an existing
object. No public ACL, public URL, presigner, image bytes in Parcel requests, or BLOB
storage is introduced. Parcel and the database continue receiving only the object
key. Existing-key checks download at most the accepted image size and revalidate
content; malformed keys are rejected without network access. A missing object is
false; bucket failures/permission failures/outages raise an error rather than a
successful or fabricated result. The synchronous client has 3-second connection/read
timeouts, a 5-second attempt deadline and a 10-second API-call deadline.

Startup validates configuration without network access. Bucket privacy/existence and
server reachability are checked at operation time, so startup alone does **not** prove
that storage works. Existing-key support assumes keys created in this private bucket;
do not migrate public legacy objects without first securing them. Filesystem proofs
are not copied automatically when switching adapters.

## Verification

Ordinary tests use mocked S3 interactions and need neither Docker nor MinIO:

```powershell
mvn test '-Dtest=S3ProofStorageServiceTest,ProofStorageConfigurationTest,FileSystemProofStorageServiceTest'
mvn verify
```

The real local HTTP tests for Parcel remain separate and unchanged. Mocked S3 tests
are **not live MinIO integration evidence**. This stage does not require or start a
MinIO server and makes no live integration claim. Before deployment, M1/M7 should
exercise an isolated private bucket with real credentials: upload/read-back a test
image, confirm anonymous access is denied, verify an outage fails, and verify the
supported server's conditional writes. Do not use production or customer data.

## Production PWA boundary

See [DEPLOYMENT.md](DEPLOYMENT.md) for the reviewed shared nginx upload-size, blob-preview
CSP and camera permissions prerequisites. The configured storage adapter cannot fix
a request rejected by nginx before it reaches Courier. Object-store tests do not prove
a live MinIO deployment; no live MinIO success was established in finalization.
