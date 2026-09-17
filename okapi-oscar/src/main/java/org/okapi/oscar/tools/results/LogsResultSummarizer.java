/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.tools.results;

public interface LogsResultSummarizer {
  SearchLogsResultSummary getSummary(PagedResultWrite write);
}
