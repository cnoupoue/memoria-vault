package be.cnoupoue.snapmemoria.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.RequestDispatcher;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;

class SpaErrorViewResolverTest {

  private final SpaErrorViewResolver resolver = new SpaErrorViewResolver();

  @Test
  void forwardsUnknownFrontendRoutesToReactIndex() {
    MockHttpServletRequest request = requestFor("/settings/sources");

    ModelAndView modelAndView = resolver.resolveErrorView(request, HttpStatus.NOT_FOUND, Map.of());

    assertThat(modelAndView).isNotNull();
    assertThat(modelAndView.getViewName()).isEqualTo("forward:/index.html");
    assertThat(modelAndView.getStatus()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void keepsApiNotFoundResponsesAsApiErrors() {
    MockHttpServletRequest request = requestFor("/api/memories/missing");

    ModelAndView modelAndView = resolver.resolveErrorView(request, HttpStatus.NOT_FOUND, Map.of());

    assertThat(modelAndView).isNull();
  }

  @Test
  void keepsStaticAssetNotFoundResponsesAsNotFound() {
    MockHttpServletRequest request = requestFor("/assets/missing.js");

    ModelAndView modelAndView = resolver.resolveErrorView(request, HttpStatus.NOT_FOUND, Map.of());

    assertThat(modelAndView).isNull();
  }

  @Test
  void keepsActuatorNotFoundResponsesAsNotFound() {
    MockHttpServletRequest request = requestFor("/actuator/missing");

    ModelAndView modelAndView = resolver.resolveErrorView(request, HttpStatus.NOT_FOUND, Map.of());

    assertThat(modelAndView).isNull();
  }

  private MockHttpServletRequest requestFor(String path) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
    request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, path);
    return request;
  }
}
