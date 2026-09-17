/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.ch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.okapi.rest.traces.FlameGraphNode;
import org.okapi.rest.traces.SpansFlameGraphResponse;

public class ChSpansFlameGraphServiceTest {

  @Test
  void buildFlameGraph_noSpans() {
    SpansFlameGraphResponse response =
        ChSpansFlameGraphService.buildFlameGraph("t1", 100, 200, List.of());

    assertEquals("t1", response.getTraceId());
    assertEquals(100, response.getTraceStartNs());
    assertEquals(200, response.getTraceEndNs());
    assertEquals(0, response.getRoots().size());
  }

  @Test
  void buildFlameGraph_missingLabelFieldsFallbackToSpanId() {
    var span = SpanInfo.builder().spanId("s1").parentSpanId(null).startNs(10).endNs(20).build();

    SpansFlameGraphResponse response =
        ChSpansFlameGraphService.buildFlameGraph("t2", 0, 100, List.of(span));

    FlameGraphNode root = response.getRoots().get(0);
    assertEquals("s1", root.getSpanId());
  }

  @Test
  void buildFlameGraph_singleLevelNestingSortedByStart() {
    var rootA =
        SpanInfo.builder()
            .spanId("a")
            .startNs(10)
            .endNs(50)
            .serviceName("svc")
            .kind("SERVER")
            .build();
    var childB =
        SpanInfo.builder()
            .spanId("b")
            .parentSpanId("a")
            .startNs(20)
            .endNs(30)
            .serviceName("svc")
            .kind("CLIENT")
            .build();
    var rootC =
        SpanInfo.builder()
            .spanId("c")
            .startNs(5)
            .endNs(15)
            .serviceName("svc")
            .kind("SERVER")
            .build();

    SpansFlameGraphResponse response =
        ChSpansFlameGraphService.buildFlameGraph("t3", 0, 100, List.of(rootA, childB, rootC));

    var roots = response.getRoots();
    assertEquals(2, roots.size());
    assertEquals("c", roots.get(0).getSpanId());
    assertEquals("a", roots.get(1).getSpanId());
    assertEquals(1, roots.get(1).getChildren().size());
    assertEquals("b", roots.get(1).getChildren().get(0).getSpanId());
  }

  @Test
  void buildFlameGraph_threeLevelNestingComputesOffsets() {
    var root =
        SpanInfo.builder()
            .spanId("r")
            .startNs(0)
            .endNs(100)
            .serviceName("svc")
            .kind("SERVER")
            .build();
    var child =
        SpanInfo.builder()
            .spanId("c1")
            .parentSpanId("r")
            .startNs(10)
            .endNs(90)
            .serviceName("svc")
            .kind("CLIENT")
            .build();
    var grandchild =
        SpanInfo.builder()
            .spanId("c2")
            .parentSpanId("c1")
            .startNs(20)
            .endNs(30)
            .serviceName("svc")
            .kind("CLIENT")
            .build();

    SpansFlameGraphResponse response =
        ChSpansFlameGraphService.buildFlameGraph("t4", 0, 200, List.of(root, child, grandchild));

    var rootNode = response.getRoots().get(0);
    var childNode = rootNode.getChildren().get(0);
    var grandchildNode = childNode.getChildren().get(0);

    assertNotNull(rootNode);
    assertEquals(0, rootNode.getOffsetNs());
    assertEquals(10, childNode.getOffsetNs());
    assertEquals(20, grandchildNode.getOffsetNs());
  }
}
