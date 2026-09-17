/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.okapi.promql.testing.PromQlTestAst.LoadCmd;
import org.okapi.promql.testing.PromQlTestAst.NumberPoint;
import org.okapi.promql.testing.PromQlTestAst.PointExpr;
import org.okapi.promql.testing.TestMetricClassifier.MetricType;

class InMemoryPromQlTestIngestorTest {
  @Test
  void ingestsLoadIntoSeriesList() {
    String input =
        """
        load 10s
          metric{env="prod"} 1 2 3
          metric{env="stage"} 4 5
        """;

    LoadCmd loadCmd = parseSingleLoad(input);
    InMemoryPromQlTestIngestor ingestor = new InMemoryPromQlTestIngestor();

    ingestor.ingestLoad(0L, loadCmd);
    List<PromQlTestIngestor.IngestedSeries> series = ingestor.series();

    assertEquals(2, series.size());
    assertEquals("metric", series.get(0).metric());
    assertEquals(Map.of("env", "prod"), series.get(0).labels());
    assertEquals(10_000L, series.get(0).stepMs());
    assertEquals(MetricType.GAUGE, series.get(0).metricType());
    assertEquals(3, series.get(0).points().size());
  }

  @Test
  void clearResetsState() {
    String input =
        """
        load 1m
          metric{env="prod"} 1 2 3
        """;

    LoadCmd loadCmd = parseSingleLoad(input);
    InMemoryPromQlTestIngestor ingestor = new InMemoryPromQlTestIngestor();

    ingestor.ingestLoad(0L, loadCmd);
    assertEquals(1, ingestor.series().size());
    ingestor.clear();
    assertEquals(0, ingestor.series().size());
  }

  @Test
  void preservesPointsAndNhcbFlag() {
    String input =
        """
        load_with_nhcb 5m
          metric{env="prod"} 1 2 3
        """;

    LoadCmd loadCmd = parseSingleLoad(input);
    InMemoryPromQlTestIngestor ingestor = new InMemoryPromQlTestIngestor();

    ingestor.ingestLoad(1_000L, loadCmd);
    PromQlTestIngestor.IngestedSeries series = ingestor.series().get(0);

    assertEquals(true, series.withNhcb());
    assertEquals(MetricType.HISTOGRAM, series.metricType());
    PointExpr first = series.points().get(0);
    NumberPoint number = assertInstanceOf(NumberPoint.class, first);
    assertEquals(1.0, number.value());
  }

  private LoadCmd parseSingleLoad(String input) {
    PromQlTestDataParser parser = new PromQlTestDataParser(input);
    PromQlTestAst.TestFile file = parser.parse();
    return assertInstanceOf(LoadCmd.class, file.commands().get(0));
  }
}
