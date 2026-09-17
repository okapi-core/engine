/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.metrics;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.okapi.rest.traces.SpanQueryRequest;

public class SpanQueryRequestTest {

  @Test
  void testUnMarshalling() {
    var json = "{\"start\":1771188973672,\"end\":1771192573672}";
    var gson = new Gson();
    var req = gson.fromJson(json, SpanQueryRequest.class);
  }
}
