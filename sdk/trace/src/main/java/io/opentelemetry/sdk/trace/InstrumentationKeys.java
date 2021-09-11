/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.trace;

import io.opentelemetry.api.trace.InstrumentationType;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// proof of concept of instrumentation layering, needs optimization, better naming, etc
class InstrumentationKeys {

  private enum SuppressionStrategy {
    NONE,
    KIND,
    KIND_AND_TYPE
  }

  private InstrumentationKeys() {}

  private static final SuppressionStrategy SUPPRESSION_STRATEGY =
      SuppressionStrategy.valueOf(
          System.getProperty("suppression-strategy", SuppressionStrategy.NONE.toString()));
  private static final Map<String, ContextKey<Boolean>> keys = new ConcurrentHashMap<>();

  public static boolean exists(SpanKind kind, InstrumentationType type, Context context) {
    if (kind == SpanKind.INTERNAL || type == InstrumentationType.NONE) {
      return false;
    }

    ContextKey<Boolean> key = keys.get(getContextKeyName(kind, type));
    if (key == null) {
      return false;
    }

    Boolean value = context.get(key);
    return value == null ? false : value;
  }

  public static Context storeInContext(SpanKind kind, InstrumentationType type, Context context) {
    if (kind == SpanKind.INTERNAL
        || type == InstrumentationType.NONE
        || SUPPRESSION_STRATEGY == SuppressionStrategy.NONE) {
      return context;
    }

    String contextKeyName = getContextKeyName(kind, type);
    ContextKey<Boolean> key =
        keys.computeIfAbsent(contextKeyName, (name) -> ContextKey.named(name));

    // todo: if present and already set?

    return context.with(key, true);
  }

  private static String getContextKeyName(SpanKind kind, InstrumentationType type) {
    switch (SUPPRESSION_STRATEGY) {
      case KIND:
        return kind.toString();
      case KIND_AND_TYPE:
        return kind.toString() + "-" + type.toString();
      default:
        return "none";
    }
  }
}
