package be.cnoupoue.snapmemoria.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.boot.webmvc.autoconfigure.error.ErrorViewResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ModelAndView;

@Component
public class SpaErrorViewResolver implements ErrorViewResolver {

  private static final String ERROR_REQUEST_URI = "jakarta.servlet.error.request_uri";

  @Override
  public ModelAndView resolveErrorView(
      HttpServletRequest request, HttpStatus status, Map<String, Object> model) {
    if (status != HttpStatus.NOT_FOUND || !isFrontendRoute(request)) {
      return null;
    }

    ModelAndView modelAndView = new ModelAndView("forward:/index.html");
    modelAndView.setStatus(HttpStatus.OK);
    return modelAndView;
  }

  private boolean isFrontendRoute(HttpServletRequest request) {
    String path = requestPath(request);

    return !path.startsWith("/api/")
        && !path.equals("/api")
        && !path.startsWith("/actuator/")
        && !path.equals("/actuator")
        && !path.contains(".");
  }

  private String requestPath(HttpServletRequest request) {
    Object errorRequestUri = request.getAttribute(ERROR_REQUEST_URI);

    if (errorRequestUri instanceof String uri && !uri.isBlank()) {
      return uri;
    }

    String requestUri = request.getRequestURI();
    String contextPath = request.getContextPath();

    if (!contextPath.isBlank() && requestUri.startsWith(contextPath)) {
      return requestUri.substring(contextPath.length());
    }

    return requestUri;
  }
}
