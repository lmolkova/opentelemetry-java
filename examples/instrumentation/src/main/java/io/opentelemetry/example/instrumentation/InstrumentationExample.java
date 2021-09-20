/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.opentelemetry.example.instrumentation;

import io.opentelemetry.api.trace.InstrumentationType;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.HashMap;
import java.util.Map;

class InstrumentationExample {

  private static Map<String, String> requestContext = new HashMap<>();
  private static TextMapSetter setter = new ContextSetter();

  public static void main(String[] args) {

    OpenTelemetrySdk openTelemetrySdk =
        OpenTelemetrySdk.builder()
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .setTracerProvider(
                SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(new LoggingSpanExporter()))
                    .build())
            .build();

    Tracer tracer = openTelemetrySdk.getTracer("instrumentation-of-my-amazing-library");
    TextMapPropagator propagator = openTelemetrySdk.getPropagators().getTextMapPropagator();
    System.out.println("Suppression strategy: " + System.getProperty("suppression-strategy"));

    // new method for optimization only:
    //   - instrumentation needs to know if attributes should to be collected beforehand
    //   - avoid context re-injection
    if (tracer.shouldStartSpan(SpanKind.CLIENT, InstrumentationType.DB, Context.current())) {

      System.out.println("Starting DB CLIENT span");

      Span span =
          tracer
              .spanBuilder("query")
              .setSpanKind(SpanKind.CLIENT)
              // new method: spans have types
              .setType(InstrumentationType.DB)
              .startSpan();

      Context context = span.storeInContext(Context.current());
      propagator.inject(context, requestContext, setter);

      try (Scope scope = span.makeCurrent()) {

        System.out.printf("current %s-%s", Span.current().getSpanContext().getTraceId(), Span.current().getSpanContext().getSpanId());

        System.out.println(
            "Should start another DB span? "
                + tracer.shouldStartSpan(SpanKind.CLIENT, InstrumentationType.DB, Context.current()));

        // efficient instrumentation should stop if shouldStartSpan returns false
        // but doesn't have to - nothing bad happens, it's just a bit less efficient
        Span duplicateSpan =
            tracer
                .spanBuilder("query")
                .setSpanKind(SpanKind.CLIENT)
                .setType(InstrumentationType.DB)
                .startSpan();

        // noop
        try (Scope scope2 = duplicateSpan.makeCurrent()) {
          System.out.printf("duplicate current %s-%s\n", Span.current().getSpanContext().getTraceId(), Span.current().getSpanContext().getSpanId());
          Span.current().setAttribute("foo", 1);
        }

        Span.current().setAttribute("bar", 2);

        // duplicate span is propagating and not recording
        // context is valid, so it will be injected
        // good news: it's the same one as was already injected
        // bad news: it's not super-efficient, so it's better to pre-check with shouldStartSpan
        System.out.println("DuplicateSpan isRecording? " + duplicateSpan.isRecording());

        context = duplicateSpan.storeInContext(context);
        propagator.inject(context, requestContext, setter);

        // assuming new child (non-DB) span starts
        System.out.println(
            "Should start HTTP span? "
                + tracer.shouldStartSpan(SpanKind.CLIENT, InstrumentationType.HTTP, context));

        Span httpSpan =
            tracer
                .spanBuilder("HTTP POST")
                .setSpanKind(SpanKind.CLIENT)
                .setType(InstrumentationType.HTTP)
                .setParent(context)
                .startSpan();

        System.out.println("HTTP span isRecording? " + httpSpan.isRecording());
        httpSpan.end();

        // noop
        duplicateSpan.end();
      }

      span.end();
    }
  }

  static class ContextSetter implements TextMapSetter<Map<String, String>> {
    @Override
    public void set(Map<String, String> carrier, String key, String value) {
      String previous = carrier.get(key);
      if (previous != null) {
        System.out.printf(
            "There's already '%s' on the context, overriding '%s' -> '%s'\n", key, previous, value);
      }
      carrier.put(key, value);
    }
  }
}
