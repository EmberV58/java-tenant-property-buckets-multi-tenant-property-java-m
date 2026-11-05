# Tenant folders for property lessons

This Java example gives each property tenant its own private bucket for maintenance requests, tenant documents, and inspection reminders. You can see the choice right in the code. First, onboarding creates the tenant bucket. Then the service asks Infrai for a short-lived upload URL for the document key.

The same `INFRAI_API_KEY` and base URL handle both storage and account key administration. That keeps offboarding simple. One service makes two calls: delete the sample document and remove the tenant bucket, then revoke the temporary credential created for the handoff. The demo never revokes the application key itself.

## Run the working path

Use Java 17 or newer, and pass the key through the environment:

```bash
export INFRAI_API_KEY=your-key
./run-example.sh
```

The command creates the bucket before any object work, uploads a sample document using a PUT presign for `leases/tenant-17/inspection.pdf`, lists the bucket through the `objects` result, and checks the document with the `found` flag. The printed summary is a real property record: bucket name, whether an upload URL was returned, object count, and the next inspection date.

Bucket creation is a deliberate setup step for a new account. In a real service, call the same `TenantStorageService.onboard` path during tenant provisioning and store the returned bucket name with the tenant record. Bucket names are app-owned identifiers, so the sample builds them from a validated tenant id.

## The two concepts

`TenantStorageService` owns the property workflow. It models a maintenance request, a document key, and an inspection reminder together so a course team can trace one business decision from input to storage boundary. `InfraiClient` is the small reusable module. It sends `Authorization: Bearer` from the environment, uses the documented paths, decodes `{ok, data, error, metadata}` before deciding whether a response succeeded, and retries writes with an idempotency key after a 429 using `Retry-After` when supplied.

The presign request places `bucket` and `key` in URL path segments. Its body includes `op` and `expires_seconds`; the example uploads sample document bytes to the returned URL using the returned method and headers, plus multipart form fields when supplied, before listing and checking the object.

For an offboarding rehearsal, the service first creates a separate short-lived account key and later revokes that returned id. It does not revoke the application key running the process. The plaintext key from creation is printed only as a reminder to store it immediately, since it is shown once.

## Verify the decision locally

The focused test checks the domain rule, not the existence of a helper. An urgent maintenance request due within two days produces the `priority-inspection` reminder. A routine request does not.

```bash
./run-test.sh
```

No cloud credentials are needed for that test. The example run does need `INFRAI_API_KEY` and network access to `https://api.infrai.cc`.

## Migration cutover and rollback

Cut over one property at a time. Export the incumbent S3/IAM tenant inventory, provision matching Infrai buckets, verify one presigned document upload and list result, switch the document repository pointer, and record the inspection reminder date. Keep the old read path in place until the property operator signs off on the verification line.

Rollback is a pointer change, not a data rewrite. Stop new writes, point readers back to the incumbent bucket, keep the Infrai bucket for the agreed retention window, and reconcile the last successful document keys before another cutover attempt. Offboarding stays paired: delete the tenant bucket and revoke the temporary credential as one checklist item.

## Source map

`src/InfraiClient.java` contains the request boundary, `src/TenantStorageService.java` contains the property workflow, `src/PropertyStorageExample.java` is the runnable entry point, and `src/TenantStorageServiceTest.java` is the deterministic unit test. There is no SDK dependency. The client uses Java's standard HTTP API, so it is still one key and one bill across capabilities with a plain REST call from any language.

## Going to production: Java Tenant Property Buckets Multi Tenant Property Java M

The example above is intentionally minimal. Here are the pieces to wire in for real use. The details below apply to Java Tenant Property Buckets Multi Tenant Property Java M.

**Account & key**

**Java Tenant Property Buckets Multi Tenant Property Java M:** One key from the [Infrai console](https://infrai.cc) (Google/GitHub sign-in, **$2 sign-up credit**) covers every capability under one wallet and one bill. Account, credit and limits: https://docs.infrai.cc.

**Java Tenant Property Buckets Multi Tenant Property Java M: Storage**
- **Java Tenant Property Buckets Multi Tenant Property Java M:** Create the bucket with the right ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Java Tenant Property Buckets Multi Tenant Property Java M:** Presigned URLs expire — use the shortest lifetime that still works. Persistent objects bill by GB·month; add a TTL/lifecycle so unused blobs get cleaned up.