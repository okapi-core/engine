/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.ch.template;

import lombok.Builder;
import lombok.Value;
import org.okapi.rest.traces.TimestampFilter;

@Value
@Builder
public class ChServiceRedOpsTemplate {
  String table;
  String serviceName;
  TimestampFilter timestampFilter;
  Integer limit;
}
