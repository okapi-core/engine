/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.okapi.ingester.client.IngesterClient;
import org.okapi.oscar.tools.results.DefaultLogsResultSummarizer;
import org.okapi.oscar.tools.results.DefaultResultKeyMaker;
import org.okapi.oscar.tools.results.GreedyLogsResultSplitter;
import org.okapi.oscar.tools.results.GsonLogsResultSerializer;
import org.okapi.oscar.tools.results.InMemoryObjectStore;
import org.okapi.oscar.tools.results.ObjectStoreSearchLogsResultDao;
import org.okapi.rest.logs.ChLogRow;
import org.okapi.rest.logs.ChLogsQueryRequest;
import org.okapi.rest.logs.ChLogsQueryResponse;

class LogsSearchToolTest {
  @Test
  void delegatesToClientPersistsPagesAndReturnsSummary() {
    var request = ChLogsQueryRequest.builder().tsStartNanos(1L).tsEndNanos(2L).limit(10).build();
    var response =
        ChLogsQueryResponse.builder()
            .items(List.of(ChLogRow.builder().body("failed").build()))
            .build();
    var client = new FakeIngesterClient(response);
    var reporter = mock(ToolCallReporter.class);
    var tool = newTool(client, reporter, 8192);

    var actual = tool.searchLogs(request);

    assertEquals(1, actual.totalPages());
    assertEquals(1, actual.totalLines());
    assertSame(request, client.request);
    verify(reporter)
        .reportRequest("searchLogs", request, "Searching logs: timeNs=[1,2] filters=0 limit=10");
    verify(reporter)
        .reportResponseSummaryOnly(
            "searchLogs", "Search logs results: logs=1 pages=1 toolCallId=" + actual.toolCallId());
  }

  @Test
  void splitsLogResultsIntoOneBasedPages() {
    var response =
        ChLogsQueryResponse.builder()
            .items(
                List.of(
                    ChLogRow.builder().body("first").build(),
                    ChLogRow.builder().body("second").build()))
            .build();
    var client = new FakeIngesterClient(response);
    var reporter = mock(ToolCallReporter.class);
    var serializer = new GsonLogsResultSerializer();
    var oneRowSize =
        serializer.serialize(
                new org.okapi.oscar.tools.results.SearchLogsResultDetail(
                    "estimate", 1, 1, List.of(ChLogRow.builder().body("first").build())))
            .length;
    var fixture = newFixture(client, reporter, oneRowSize + 1);

    var summary =
        fixture
            .logsSearchTool()
            .searchLogs(
                ChLogsQueryRequest.builder().tsStartNanos(1L).tsEndNanos(2L).limit(10).build());

    assertEquals(2, summary.totalPages());
    assertEquals(2, summary.totalLines());
    var firstPage = fixture.logDetailsTool().getLogDetails(summary.toolCallId(), 1);
    var secondPage = fixture.logDetailsTool().getLogDetails(summary.toolCallId(), 2);
    assertEquals("first", firstPage.lines().getFirst().getBody());
    assertEquals("second", secondPage.lines().getFirst().getBody());
    assertEquals(2, firstPage.totalPages());
    assertEquals(2, secondPage.totalPages());
  }

  @Test
  void returnsZeroPagesWhenNoLogsMatch() {
    var response = ChLogsQueryResponse.builder().items(List.of()).build();
    var client = new FakeIngesterClient(response);
    var reporter = mock(ToolCallReporter.class);
    var tool = newTool(client, reporter, 8192);

    var summary =
        tool.searchLogs(
            ChLogsQueryRequest.builder().tsStartNanos(1L).tsEndNanos(2L).limit(10).build());

    assertEquals(0, summary.totalPages());
    assertEquals(0, summary.totalLines());
  }

  private static LogsSearchTool newTool(
      IngesterClient client, ToolCallReporter reporter, int maxSizePerPage) {
    return newFixture(client, reporter, maxSizePerPage).logsSearchTool();
  }

  private static ToolFixture newFixture(
      IngesterClient client, ToolCallReporter reporter, int maxSizePerPage) {
    var serializer = new GsonLogsResultSerializer();
    var dao =
        new ObjectStoreSearchLogsResultDao(
            new GreedyLogsResultSplitter(serializer),
            serializer,
            new InMemoryObjectStore(),
            new DefaultResultKeyMaker(),
            maxSizePerPage);
    return new ToolFixture(
        new LogsSearchTool(client, reporter, dao, new DefaultLogsResultSummarizer()),
        new LogDetailsTool(dao));
  }

  private record ToolFixture(LogsSearchTool logsSearchTool, LogDetailsTool logDetailsTool) {}

  private static class FakeIngesterClient extends IngesterClient {
    private final ChLogsQueryResponse response;
    private ChLogsQueryRequest request;

    FakeIngesterClient(ChLogsQueryResponse response) {
      super("http://localhost", new OkHttpClient(), null);
      this.response = response;
    }

    @Override
    public ChLogsQueryResponse searchLogs(ChLogsQueryRequest request) {
      this.request = request;
      return response;
    }
  }
}
