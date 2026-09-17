/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.ch;

import java.util.Map;

public record ChSumSample(
    long tsStart,
    long tsEnd,
    double value,
    CH_SUM_TYPE sumType,
    Map<String, String> tags,
    String unit) {}
