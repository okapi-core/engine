/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.ch;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ChSpansQueryTemplate {
  String table;
  String traceId;
  String spanId;
  String kind;
  String serviceName;
  String servicePeerName;
  String httpMethod;
  Integer httpStatusCode;
  String httpOrigin;
  String httpHost;
  String dbSystem;
  String dbCollection;
  String dbNamespace;
  String dbOperation;
  Long tsStartNs;
  Long tsEndNs;
  Long durMinNs;
  Long durMaxNs;
  Integer limit;
  List<ChSpanStringAttributeFilter> stringAttributeFilters;
  List<ChSpanNumberAttributeFilter> numberAttributeFilters;
}
