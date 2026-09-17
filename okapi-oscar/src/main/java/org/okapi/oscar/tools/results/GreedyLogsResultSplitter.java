/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.tools.results;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.okapi.rest.logs.ChLogRow;
import org.okapi.rest.logs.ChLogsQueryResponse;
import org.springframework.stereotype.Component;

@Component
public class GreedyLogsResultSplitter implements LogsResultSplitter {

  private final LogsResultSerializer serializer;

  public GreedyLogsResultSplitter(LogsResultSerializer serializer) {
    this.serializer = serializer;
  }

  @Override
  public List<SearchLogsResultDetail> split(
      String toolCallId, ChLogsQueryResponse response, int maxSizePerPage) {
    if (maxSizePerPage < 1) {
      throw new IllegalArgumentException("maxSizePerPage must be positive");
    }
    List<ChLogRow> rows =
        response == null || response.getItems() == null ? List.of() : response.getItems();
    if (rows.isEmpty()) {
      return List.of();
    }

    List<List<ChLogRow>> pageLines = new ArrayList<>();
    List<ChLogRow> current = new ArrayList<>();
    for (var row : rows) {
      current.add(row);
      int sizeWithRow = serializedSize(toolCallId, pageLines.size() + 1, current);
      if (sizeWithRow <= maxSizePerPage || current.size() == 1) {
        continue;
      }
      current.remove(current.size() - 1);
      pageLines.add(List.copyOf(current));
      current = new ArrayList<>();
      current.add(row);
    }
    if (!current.isEmpty()) {
      pageLines.add(List.copyOf(current));
    }

    int totalPages = pageLines.size();
    List<SearchLogsResultDetail> details = new ArrayList<>(totalPages);
    for (int i = 0; i < totalPages; i++) {
      details.add(new SearchLogsResultDetail(toolCallId, i + 1, totalPages, pageLines.get(i)));
    }
    return Collections.unmodifiableList(details);
  }

  private int serializedSize(String toolCallId, int pageNumber, List<ChLogRow> lines) {
    var estimate = new SearchLogsResultDetail(toolCallId, pageNumber, 1, lines);
    return serializer.serialize(estimate).length;
  }
}
