package be.cnoupoue.memoriavault.browser;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.Authenticator;
import java.net.ConnectException;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.PushPromiseHandler;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

class LocalMemoriaVaultInstanceProbeTest {

  @Test
  void detectsExistingHealthyMemoriaVaultInstance() throws IOException {
    LocalMemoriaVaultInstanceProbe instanceProbe =
        instanceProbe(
            Map.of(
                "/actuator/health", new StubResponse(200, "{\"status\":\"UP\"}"),
                "/actuator/info", new StubResponse(200, "{\"app\":{\"name\":\"memoria-vault\"}}")));

    InstanceProbeResult result = instanceProbe.probe(appUri());

    assertThat(result).isEqualTo(InstanceProbeResult.HEALTHY_MEMORIA_VAULT);
  }

  @Test
  void doesNotTreatUnrelatedServiceAsMemoriaVault() throws IOException {
    LocalMemoriaVaultInstanceProbe instanceProbe =
        instanceProbe(
            Map.of(
                "/actuator/health", new StubResponse(200, "{\"status\":\"UP\"}"),
                "/actuator/info", new StubResponse(200, "{\"app\":{\"name\":\"other\"}}")));

    InstanceProbeResult result = instanceProbe.probe(appUri());

    assertThat(result).isEqualTo(InstanceProbeResult.UNRELATED_SERVICE);
  }

  @Test
  void doesNotTreatInvalidHealthResponseAsMemoriaVault() throws IOException {
    LocalMemoriaVaultInstanceProbe instanceProbe =
        instanceProbe(
            Map.of(
                "/actuator/health", new StubResponse(200, "not-json"),
                "/actuator/info", new StubResponse(200, "{\"app\":{\"name\":\"memoria-vault\"}}")));

    InstanceProbeResult result = instanceProbe.probe(appUri());

    assertThat(result).isEqualTo(InstanceProbeResult.UNRELATED_SERVICE);
  }

  @Test
  void treatsConnectionFailureAsUnavailable() {
    LocalMemoriaVaultInstanceProbe instanceProbe =
        new LocalMemoriaVaultInstanceProbe(new StubHttpClient(Map.of(), true));

    InstanceProbeResult result = instanceProbe.probe(appUri());

    assertThat(result).isEqualTo(InstanceProbeResult.UNAVAILABLE);
  }

  private LocalMemoriaVaultInstanceProbe instanceProbe(Map<String, StubResponse> responses) {
    return new LocalMemoriaVaultInstanceProbe(new StubHttpClient(responses, false));
  }

  private URI appUri() {
    return URI.create("http://127.0.0.1:8080");
  }

  private record StubResponse(int status, String body) {}

  private static class StubHttpClient extends HttpClient {

    private final Map<String, StubResponse> responses;
    private final boolean connectionFailure;

    private StubHttpClient(Map<String, StubResponse> responses, boolean connectionFailure) {
      this.responses = responses;
      this.connectionFailure = connectionFailure;
    }

    @Override
    public Optional<CookieHandler> cookieHandler() {
      return Optional.empty();
    }

    @Override
    public Optional<Duration> connectTimeout() {
      return Optional.empty();
    }

    @Override
    public Redirect followRedirects() {
      return Redirect.NEVER;
    }

    @Override
    public Optional<ProxySelector> proxy() {
      return Optional.empty();
    }

    @Override
    public SSLContext sslContext() {
      return null;
    }

    @Override
    public SSLParameters sslParameters() {
      return new SSLParameters();
    }

    @Override
    public Optional<Authenticator> authenticator() {
      return Optional.empty();
    }

    @Override
    public Version version() {
      return Version.HTTP_1_1;
    }

    @Override
    public Optional<Executor> executor() {
      return Optional.empty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> HttpResponse<T> send(HttpRequest request, BodyHandler<T> responseBodyHandler)
        throws IOException {
      if (connectionFailure) {
        throw new ConnectException("connection refused");
      }

      StubResponse response =
          responses.getOrDefault(request.uri().getPath(), new StubResponse(404, ""));
      return (HttpResponse<T>) new StubHttpResponse(response.status(), response.body(), request);
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
        HttpRequest request, BodyHandler<T> responseBodyHandler) {
      return CompletableFuture.failedFuture(
          new UnsupportedOperationException("Not used by this test"));
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
        HttpRequest request,
        BodyHandler<T> responseBodyHandler,
        PushPromiseHandler<T> pushPromiseHandler) {
      return CompletableFuture.failedFuture(
          new UnsupportedOperationException("Not used by this test"));
    }
  }

  private record StubHttpResponse(int statusCode, String body, HttpRequest request)
      implements HttpResponse<String> {

    @Override
    public Optional<HttpResponse<String>> previousResponse() {
      return Optional.empty();
    }

    @Override
    public HttpHeaders headers() {
      return HttpHeaders.of(Map.of(), (name, value) -> true);
    }

    @Override
    public Optional<SSLSession> sslSession() {
      return Optional.empty();
    }

    @Override
    public URI uri() {
      return request.uri();
    }

    @Override
    public Version version() {
      return Version.HTTP_1_1;
    }
  }
}
