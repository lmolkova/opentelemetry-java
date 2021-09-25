/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.trace;

import io.opentelemetry.api.trace.InstrumentationType;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerBuilder;
import io.opentelemetry.sdk.internal.ComponentRegistry;

class SdkTracerBuilder implements TracerBuilder {

  private final ComponentRegistry<SdkTracer> registry;
  private final String instrumentationName;
  private String instrumentationVersion;
  private String schemaUrl;
  private String instrumentationType;

  SdkTracerBuilder(ComponentRegistry<SdkTracer> registry, String instrumentationName) {
    this.registry = registry;
    this.instrumentationName = instrumentationName;
  }

  @Override
  public TracerBuilder setSchemaUrl(String schemaUrl) {
    this.schemaUrl = schemaUrl;
    return this;
  }

  @Override
  public TracerBuilder setInstrumentationVersion(String instrumentationVersion) {
    this.instrumentationVersion = instrumentationVersion;
    return this;
  }

  @Override
  public TracerBuilder setInstrumentationType(InstrumentationType type) {
    this.instrumentationType = type.toString();
    return this;
  }

  @Override
  public Tracer build() {
    // todo same name and version -> same type?
    return registry.get(instrumentationName, instrumentationVersion, schemaUrl, instrumentationType);
  }
}
