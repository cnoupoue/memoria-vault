package be.cnoupoue.snapmemoria.browser;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Pattern;

public class LocalSnapmemoriaInstanceProbe {

  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(2);
  private static final Pattern HEALTHY_STATUS_PATTERN =
      Pattern.compile("\"status\"\\s*:\\s*\"UP\"");
  private static final Pattern MEMORIA_VAULT_APP_NAME_PATTERN =
      Pattern.compile("\"name\"\\s*:\\s*\"memoria-vault\"");

  private final HttpClient httpClient;

  public LocalSnapmemoriaInstanceProbe() {
    this(HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build());
  }

  LocalSnapmemoriaInstanceProbe(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  public InstanceProbeResult probe(URI appUrl) {
    try {
      if (!isHealthy(appUrl)) {
        return InstanceProbeResult.UNRELATED_SERVICE;
      }

      if (!hasMemoriaVaultIdentity(appUrl)) {
        return InstanceProbeResult.UNRELATED_SERVICE;
      }

      return InstanceProbeResult.HEALTHY_SNAPMEMORIA;
    } catch (ConnectException exception) {
      return InstanceProbeResult.UNAVAILABLE;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return InstanceProbeResult.UNAVAILABLE;
    } catch (IOException | RuntimeException exception) {
      return InstanceProbeResult.UNRELATED_SERVICE;
    }
  }

  private boolean isHealthy(URI appUrl) throws IOException, InterruptedException {
    HttpResponse<String> response = get(appUrl.resolve("/actuator/health"));

    if (!isSuccess(response)) {
      return false;
    }

    return HEALTHY_STATUS_PATTERN.matcher(response.body()).find();
  }

  private boolean hasMemoriaVaultIdentity(URI appUrl) throws IOException, InterruptedException {
    HttpResponse<String> response = get(appUrl.resolve("/actuator/info"));

    if (!isSuccess(response)) {
      return false;
    }

    return MEMORIA_VAULT_APP_NAME_PATTERN.matcher(response.body()).find();
  }

  private HttpResponse<String> get(URI uri) throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder(uri)
            .timeout(REQUEST_TIMEOUT)
            .GET()
            .header("Accept", "application/json")
            .build();

    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private boolean isSuccess(HttpResponse<String> response) {
    return response.statusCode() >= 200 && response.statusCode() < 300;
  }
}
