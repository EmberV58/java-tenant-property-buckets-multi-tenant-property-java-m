import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.io.IOException;

public final class InfraiClientTest {
    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int[] calls = {0}, status = {429};
        String[] body = {"{\"ok\": false,\"error\":{}}"};
        server.createContext("/", exchange -> {
            calls[0]++;
            int code = status[0];
            String response = body[0];
            if (code == 429 && calls[0] > 1) { code = 200; response = "{\"ok\":true,\"data\":{}}"; }
            exchange.getResponseHeaders().add("Retry-After", "0");
            byte[] bytes = response.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.createContext("/upload", exchange -> {
            String received = new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            boolean valid = exchange.getRequestMethod().equals("POST")
                    && "signed-value".equals(exchange.getRequestHeaders().getFirst("X-Upload-Token"))
                    && received.contains("name=\"policy\"\r\n\r\nsigned-policy")
                    && received.contains("sample-document");
            exchange.sendResponseHeaders(valid ? 204 : 400, -1);
            exchange.close();
        });
        server.start();
        try {
            InfraiClient client = new InfraiClient("http://127.0.0.1:" + server.getAddress().getPort(), "test");
            client.uploadDocument("{\"url\":\"http://127.0.0.1:" + server.getAddress().getPort()
                    + "/upload\",\"method\":\"POST\",\"headers\":{\"X-Upload-Token\":\"signed-value\"},"
                    + "\"fields\":{\"policy\":\"signed-policy\"}}", "sample-document".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            client.createBucket("test");
            if (calls[0] != 2) throw new AssertionError("429 envelope must retry");
            for (int code : new int[]{200, 400, 500}) {
                status[0] = code;
                body[0] = code == 200 ? "{\"ok\" : false}" : "{\"ok\":true,\"data\":{}}";
                try { client.createBucket("test"); throw new AssertionError("must reject " + code); }
                catch (IOException expected) { }
            }
            status[0] = 200; body[0] = "{broken";
            try { client.createBucket("test"); throw new AssertionError("must reject invalid JSON"); }
            catch (IOException expected) { }
            String envelope = "{\"ok\":true,\"data\":{\"key_id\" : \"a\\u002db\",\"objects\":[{\"key\":\"a,b\"}]}}";
            if (!client.idFromKeyResponse(envelope).equals("a-b")) throw new AssertionError("decode key id");
            if (InfraiClient.objectCount(envelope) != 1) throw new AssertionError("decode objects");
            System.out.println("InfraiClientTest: passed");
        } finally { server.stop(0); }
    }
}
