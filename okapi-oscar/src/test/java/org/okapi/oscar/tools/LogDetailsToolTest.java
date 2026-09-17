/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.okapi.oscar.tools.results.DefaultResultKeyMaker;
import org.okapi.oscar.tools.results.GreedyLogsResultSplitter;
import org.okapi.oscar.tools.results.GsonLogsResultSerializer;
import org.okapi.oscar.tools.results.InMemoryObjectStore;
import org.okapi.oscar.tools.results.ObjectStoreSearchLogsResultDao;
import org.okapi.rest.logs.ChLogRow;
import org.okapi.rest.logs.ChLogsQueryResponse;

class LogDetailsToolTest {

  @Test
  void fetchesPersistedLogPageThroughDao() {
    var dao = newDao(8192);
    dao.persist(
        "tool-call-1",
        ChLogsQueryResponse.builder()
            .items(List.of(ChLogRow.builder().body("important failure").build()))
            .build());
    var tool = new LogDetailsTool(dao);

    var detail = tool.getLogDetails("tool-call-1", 1);

    assertEquals("tool-call-1", detail.toolCallId());
    assertEquals(1, detail.pageNumber());
    assertEquals(1, detail.totalPages());
    assertEquals("important failure", detail.lines().getFirst().getBody());
  }

  @Test
  void rejectsZeroBasedPageNumbers() {
    var tool = new LogDetailsTool(newDao(8192));

    assertThrows(IllegalArgumentException.class, () -> tool.getLogDetails("tool-call-1", 0));
  }

  @Test
  void errorsWhenPageDoesNotExist() {
    var tool = new LogDetailsTool(newDao(8192));

    assertThrows(IllegalArgumentException.class, () -> tool.getLogDetails("missing", 1));
  }

  private static ObjectStoreSearchLogsResultDao newDao(int maxSizePerPage) {
    var serializer = new GsonLogsResultSerializer();
    return new ObjectStoreSearchLogsResultDao(
        new GreedyLogsResultSplitter(serializer),
        serializer,
        new InMemoryObjectStore(),
        new DefaultResultKeyMaker(),
        maxSizePerPage);
  }
}
