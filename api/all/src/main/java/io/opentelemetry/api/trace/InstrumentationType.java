/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.trace;

// TODOs
//   - switch to extensible enum as adding to enum is potentially a breaking change (vNext
// instrumentation -> vPrev OTel API/SDK)
//   - add all conventions
public enum InstrumentationType {
  NONE,
  HTTP,
  DB,
  RPC,
  MESSAGING
}
