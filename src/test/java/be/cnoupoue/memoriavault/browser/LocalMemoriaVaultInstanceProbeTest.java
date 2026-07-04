package be.cnoupoue.memoriavault.browser;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LocalMemoriaVaultInstanceProbeTest {

  private final LocalMemoriaVaultInstanceProbe instanceProbe = new LocalMemoriaVaultInstanceProbe();

  @Test
  void detectsExistingHealthyMemoriaVaultInstance() throws IOException {
    try (TestHttpServer server =
        TestHttpServer.create()
            .responding("/actuator/health", 200, "{\"status\":\"UP\"}")
            .responding("/actuator/info", 200, "{\"app\":{\"name\":\"memoria-vault\"}}")) {

      InstanceProbeResult result = instanceProbe.probe(server.uri());

      assertThat(result).isEqualTo(InstanceProbeResult.HEALTHY_MEMORIA_VAULT);
    }
  }

  @Test
  void doesNotTreatUnrelatedServiceAsMemoriaVault() throws IOException {
    try (TestHttpServer server =
        TestHttpServer.create()
            .responding("/actuator/health", 200, "{\"status\":\"UP\"}")
            .responding("/actuator/info", 200, "{\"app\":{\"name\":\"other\"}}")) {

      InstanceProbeResult result = instanceProbe.probe(server.uri());

      assertThat(result).isEqualTo(InstanceProbeResult.UNRELATED_SERVICE);
    }
  }

  @Test
  void doesNotTreatInvalidHealthResponseAsMemoriaVault() throws IOException {
    try (TestHttpServer server =
        TestHttpServer.create()
            .responding("/actuator/health", 200, "not-json")
            .responding("/actuator/info", 200, "{\"app\":{\"name\":\"memoria-vault\"}}")) {

      InstanceProbeResult result = instanceProbe.probe(server.uri());

      assertThat(result).isEqualTo(InstanceProbeResult.UNRELATED_SERVICE);
    }
  }

  private static class TestHttpServer implements AutoCloseable {

    private final HttpServer server;

    private TestHttpServer(HttpServer server) {
      this.server = server;
      this.server.start();
    }

    static TestHttpServer create() throws IOException {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      return new TestHttpServer(server);
    }

    TestHttpServer responding(String path, int status, String body) {
      server.createContext(path, exchange -> writeResponse(exchange, status, body));
      return this;
    }

    URI uri() {
      return URI.create("http://127.0.0.1:%d".formatted(server.getAddress().getPort()));
    }

    @Override
    public void close() {
      server.stop(0);
    }

    private void writeResponse(HttpExchange exchange, int status, String body) throws IOException {
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(status, bytes.length);

      try (OutputStream responseBody = exchange.getResponseBody()) {
        responseBody.write(bytes);
      }
    }
  }
}
