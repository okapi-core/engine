/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.query;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.okapi.ingester.client.PromQlQueryClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PromQlService {

  private final PromQlQueryClient promQlQueryClient;

  public String queryPromQlInstant(String query, String time, String timeout) {
    return promQlQueryClient.queryInstant(query, time, timeout);
  }

  public String queryPromQlInstantPost(String query, String time, String timeout) {
    return promQlQueryClient.queryInstantPost(query, time, timeout);
  }

  public String queryPromQlRange(
      String query, String start, String end, String step, String timeout) {
    return promQlQueryClient.queryRange(query, start, end, step, timeout);
  }

  public String queryPromQlRangePost(
      String query, String start, String end, String step, String timeout) {
    return promQlQueryClient.queryRangePost(query, start, end, step, timeout);
  }

  public String queryPromQlLabels(String start, String end, List<String> matchers) {
    return promQlQueryClient.listLabels(start, end, matchers);
  }

  public String queryPromQlLabelValues(
      String label, String start, String end, List<String> matchers) {
    return promQlQueryClient.listLabelValues(label, start, end, matchers);
  }

  public String queryPromQlMetadata(String metric, Integer limit) {
    return promQlQueryClient.metadata(metric, limit);
  }
}
