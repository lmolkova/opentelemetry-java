/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.trace;

// TODOs
//   - switch to extensible enum as adding to enum is potentially a breaking change (vNext
// instrumentation -> vPrev OTel API/SDK)
//   - add all conventions

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InstrumentationType {
  private static final Map<String, InstrumentationType> VALUES = new ConcurrentHashMap<>();

  public static final InstrumentationType NONE = InstrumentationType.fromString("NONE");
  public static final InstrumentationType HTTP = InstrumentationType.fromString("HTTP");
  public static final InstrumentationType DB = InstrumentationType.fromString("DB");
  public static final InstrumentationType RPC = InstrumentationType.fromString("RPC");
  //public static final InstrumentationType MESSAGING = InstrumentationType.fromString("MESSAGING");
  // VS
  //public static final InstrumentationType MESSAGING = InstrumentationType.fromString("MESSAGING_RECEIVE");  - CONSUMER
  //public static final InstrumentationType MESSAGING = InstrumentationType.fromString("MESSAGING_PROCESS");  - CONSUMER

  private static InstrumentationType fromString(String type) {
    return VALUES.computeIfAbsent(type, (t) -> new InstrumentationType(t));
  }

  private final String type;
  private InstrumentationType(String type) {
    this.type = type;
  }

  @Override
  public String toString() {
    return type;
  }
}
