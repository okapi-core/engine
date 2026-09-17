/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.demo.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import jakarta.servlet.http.HttpServletRequest;
import okhttp3.Request;
import org.springframework.stereotype.Component;

@Component
public class TracingHelper {
  private final Tracer tracer;
  private final TextMapPropagator propagator;

  private static final TextMapGetter<HttpServletRequest> SERVLET_GETTER =
      new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(HttpServletRequest carrier) {
          return carrier.getHeaderNames() == null
              ? java.util.List.of()
              : java.util.Collections.list(carrier.getHeaderNames());
        }

        @Override
        public String get(HttpServletRequest carrier, String key) {
          return carrier.getHeader(key);
        }
      };

  private static final io.opentelemetry.context.propagation.TextMapSetter<Request.Builder>
      OKHTTP_SETTER =
          new io.opentelemetry.context.propagation.TextMapSetter<>() {
            @Override
            public void set(Request.Builder carrier, String key, String value) {
              if (carrier != null) {
                carrier.addHeader(key, value);
              }
            }
          };

  public TracingHelper(Tracer tracer, TextMapPropagator propagator) {
    this.tracer = tracer;
    this.propagator = propagator;
  }

  public Span startServerSpan(HttpServletRequest request, String spanName) {
    Context parent = propagator.extract(Context.current(), request, SERVLET_GETTER);
    var span =
        tracer.spanBuilder(spanName).setSpanKind(SpanKind.SERVER).setParent(parent).startSpan();
    span.setAttribute("http.method", request.getMethod());
    span.setAttribute("http.route", request.getRequestURI());
    return span;
  }

  public Span startClientSpan(String spanName, String url) {
    var span = tracer.spanBuilder(spanName).setSpanKind(SpanKind.CLIENT).startSpan();
    span.setAttribute("http.url", url);
    return span;
  }

  public void inject(Request.Builder builder, Span span) {
    propagator.inject(Context.current().with(span), builder, OKHTTP_SETTER);
  }

  public void recordError(Span span, Exception exception) {
    span.recordException(exception);
    span.setStatus(StatusCode.ERROR, exception.getMessage());
  }
}
