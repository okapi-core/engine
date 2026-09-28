/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.okapi.ch.tables.ChTableSpec;

class ChTableSpecTest {
  @Test
  void everyTableSpecIsPresentAndNonEmpty() {
    var specs = ChTableSpec.getAllTables();

    assertEquals(10, specs.size());
    for (var spec : specs) {
      assertNotNull(spec);
      assertFalse(spec.isBlank());
      assertFalse(spec.trim().isEmpty());
    }
  }

  @Test
  void allIndividualSpecsAreIncludedInMigrationOrder() {
    List<String> specs = ChTableSpec.getAllTables();

    assertEquals(ChTableSpec.getGaugeTableSpec(), specs.get(0));
    assertEquals(ChTableSpec.getHistoTableSpec(), specs.get(1));
    assertEquals(ChTableSpec.getExponentialHistoTableSpec(), specs.get(2));
    assertEquals(ChTableSpec.getSumTableSpec(), specs.get(3));
    assertEquals(ChTableSpec.getMetricEventsMetaTableSpec(), specs.get(4));
    assertEquals(ChTableSpec.getExemplarsTableSpec(), specs.get(5));
    assertEquals(ChTableSpec.getTracesTableSpec(), specs.get(6));
    assertEquals(ChTableSpec.getSpansIngestedAttribsTableSpec(), specs.get(7));
    assertEquals(ChTableSpec.getServiceRedEventsTableSpec(), specs.get(8));
    assertEquals(ChTableSpec.getLogsTableSpec(), specs.get(9));
  }
}
