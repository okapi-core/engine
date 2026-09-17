/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.tools.results;

import org.springframework.stereotype.Component;

@Component
public class DefaultLogsResultSummarizer implements LogsResultSummarizer {

  @Override
  public SearchLogsResultSummary getSummary(PagedResultWrite write) {
    return new SearchLogsResultSummary(write.toolCallId(), write.totalPages(), write.totalItems());
  }
}
