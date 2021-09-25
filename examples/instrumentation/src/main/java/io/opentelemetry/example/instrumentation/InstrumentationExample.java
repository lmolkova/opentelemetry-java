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

    SdkTracerProvider provider = SdkTracerProvider.builder()
        .addSpanProcessor(SimpleSpanProcessor.create(new LoggingSpanExporter()))
        .build();

    OpenTelemetrySdk openTelemetrySdk =
        OpenTelemetrySdk.builder()
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .setTracerProvider(provider)
            .build();
    TextMapPropagator propagator = openTelemetrySdk.getPropagators().getTextMapPropagator();


    Tracer dbTracer = provider.tracerBuilder("instrumentation-of-my-amazing-library")
        // new method
        .setInstrumentationType(InstrumentationType.DB)
        .build();

    Tracer anotherDbTracer = provider.tracerBuilder("instrumentation-of-another-amazing-library")
        .setInstrumentationType(InstrumentationType.DB)
        .build();

    Tracer httpTracer = provider.tracerBuilder("instrumentation-http-client")
        .setInstrumentationType(InstrumentationType.HTTP)
        .build();

    System.out.println("Suppression strategy: " + System.getProperty("suppression-strategy"));

    // new method for optimization only:
    //   - instrumentation needs to know if attributes should to be collected beforehand
    //   - avoid context re-injection
    if (dbTracer.shouldStartSpan(SpanKind.CLIENT, Context.current())) {
      System.out.println("Starting DB CLIENT span");

      Span span =
          dbTracer
              .spanBuilder("query")
              .setSpanKind(SpanKind.CLIENT)
              .startSpan();
      try (Scope scope = span.makeCurrent()) {

        System.out.printf("current %s-%s", Span.current().getSpanContext().getTraceId(), Span.current().getSpanContext().getSpanId());

        propagator.inject(Context.current(), requestContext, setter);

        System.out.println(
            "Should start another DB span? "
                + anotherDbTracer.shouldStartSpan(SpanKind.CLIENT, Context.current()));

        // efficient instrumentation should stop if shouldStartSpan returns false
        // but doesn't have to - nothing bad happens, it's just a bit less efficient
        Span duplicateSpan =
            anotherDbTracer
                .spanBuilder("query")
                .setSpanKind(SpanKind.CLIENT)
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

        Context context = duplicateSpan.storeInContext(Context.current());
        propagator.inject(context, requestContext, setter);

        // assuming new child (non-DB) span starts
        System.out.println(
            "Should start HTTP span? "
                + httpTracer.shouldStartSpan(SpanKind.CLIENT, context));

        Span httpSpan =
            httpTracer
                .spanBuilder("HTTP POST")
                .setSpanKind(SpanKind.CLIENT)
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
