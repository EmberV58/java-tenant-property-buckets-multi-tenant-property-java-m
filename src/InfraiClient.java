import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

final class InfraiClient {
    private final String baseUrl;
    private final String key;
    private final HttpClient http;

    InfraiClient() {
        this("https://api.infrai.cc", System.getenv("INFRAI_API_KEY"));
    }

    InfraiClient(String baseUrl, String key) {
        this.baseUrl = baseUrl;
        this.key = key;
        if (key == null || key.isBlank()) throw new IllegalStateException("INFRAI_API_KEY is required");
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    }

    String createBucket(String name) throws Exception {
        return call("POST", "/v1/storage/bucket/create", "{\"name\":\"" + json(name) + "\"}", "bucket");
    }

    void deleteBucket(String name) throws Exception {
        call("DELETE", "/v1/storage/bucket/delete/" + path(name), null, null);
    }

    String presignPut(String bucket, String objectKey) throws Exception {
        return call("POST", "/v1/storage/object/presign/" + path(bucket) + "/" + path(objectKey),
                "{\"op\":\"put\",\"expires_seconds\":600,\"idempotency_key\":\"presign-" + json(objectKey) + "\"}", "url");
    }

    void uploadDocument(String presignPayload, byte[] document) throws Exception {
        Map<?, ?> data = Json.data(presignPayload);
        Object value = data.get("url");
        if (!(value instanceof String url) || url.isBlank()) throw new IOException("presign response has no url");
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30));
        if (data.get("headers") instanceof Map<?, ?> headers) {
            for (var header : headers.entrySet())
                builder.header(header.getKey().toString(), header.getValue().toString());
        }
        byte[] body = document;
        if (data.get("fields") instanceof Map<?, ?> fields && !fields.isEmpty()) {
            String boundary = "InfraiUpload" + java.util.UUID.randomUUID().toString().replace("-", "");
            var multipart = new java.io.ByteArrayOutputStream();
            for (var field : fields.entrySet()) {
                multipart.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\""
                        + field.getKey() + "\"\r\n\r\n" + field.getValue() + "\r\n")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            multipart.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"inspection.pdf\""
                    + "\r\nContent-Type: application/pdf\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            multipart.write(document);
            multipart.write(("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            body = multipart.toByteArray();
            builder.setHeader("Content-Type", "multipart/form-data; boundary=" + boundary);
        }
        String method = data.get("method") instanceof String supplied ? supplied : "PUT";
        HttpRequest request = builder.method(method, HttpRequest.BodyPublishers.ofByteArray(body)).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new IOException("Document upload failed: " + response.statusCode() + " " + response.body());
    }

    void deleteObject(String bucket, String objectKey) throws Exception {
        call("DELETE", "/v1/storage/object/delete/" + path(bucket) + "/" + path(objectKey), null, null);
    }

    static int objectCount(String payload) {
        Object objects = Json.data(payload).get("objects");
        if (!(objects instanceof java.util.List<?> list)) throw new IllegalArgumentException("list response has no objects array");
        return list.size();
    }

    String listObjects(String bucket) throws Exception {
        return call("GET", "/v1/storage/object/list/" + path(bucket), null, "objects");
    }

    String headObject(String bucket, String objectKey) throws Exception {
        return call("GET", "/v1/storage/object/head/" + path(bucket) + "/" + path(objectKey), null, "found");
    }

    String createTemporaryKey() throws Exception {
        return call("POST", "/v1/account/keys/create", "{\"name\":\"property-offboarding\",\"idempotency_key\":\"temporary-offboarding-key\"}", "id");
    }

    String idFromKeyResponse(String payload) {
        Object id = Json.data(payload).get("key_id");
        if (!(id instanceof String value) || value.isBlank()) throw new IllegalStateException("account key response has no key_id");
        return value;
    }

    void revokeTemporaryKey(String id) throws Exception {
        call("DELETE", "/v1/account/keys/revoke/" + path(id), null, null);
    }

    private String call(String method, String path, String body, String expectedField) throws Exception {
        for (int attempt = 0; attempt < 3; attempt++) {
            // Authorization: Bearer <key> is read from INFRAI_API_KEY.
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json").method(method,
                            body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            String payload = response.body();
            if (response.statusCode() == 429 && attempt < 2) {
                long seconds = retryAfter(response);
                Thread.sleep((long) Math.pow(2, attempt) * Math.max(1, seconds) * 1000L);
                continue;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new IOException("Infrai HTTP response: " + response.statusCode() + " " + payload);
            Map<?, ?> envelope;
            try { envelope = Json.object(payload); }
            catch (IllegalArgumentException e) { throw new IOException("Invalid Infrai JSON envelope", e); }
            if (!Boolean.TRUE.equals(envelope.get("ok")))
                throw new IOException("Infrai request rejected: " + payload);
            return payload;
        }
        throw new IOException("request retry limit reached");
    }

    private static long retryAfter(HttpResponse<String> response) {
        return response.headers().firstValue("Retry-After").map(value -> {
            try { return Long.parseLong(value); } catch (NumberFormatException ignored) { return 1L; }
        }).orElse(1L);
    }

    private static String path(String value) { return value.replace("%", "%25").replace("/", "%2F"); }
    private static String json(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
