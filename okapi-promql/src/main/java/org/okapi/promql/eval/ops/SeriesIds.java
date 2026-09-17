/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.ops;

import java.util.HashMap;
import java.util.Map;
import org.okapi.promql.eval.VectorData.Labels;
import org.okapi.promql.eval.VectorData.SeriesId;

/** Helpers for constructing identities for values derived from an input series. */
public final class SeriesIds {
  private SeriesIds() {}

  public static SeriesId derived(SeriesId id) {
    return new SeriesId(id.metric(), cleanedLabels(id.labels().tags()), true);
  }

  public static SeriesId derived(SeriesId id, Map<String, String> sourceTags) {
    return new SeriesId(id.metric(), cleanedLabels(sourceTags), true);
  }

  public static SeriesId materialize(SeriesId id) {
    return id.dropMetricName()
        ? new SeriesId("", id.labels())
        : new SeriesId(id.metric(), id.labels());
  }

  public static SeriesId withMetric(SeriesId id, String metric) {
    return new SeriesId(metric, id.labels(), false);
  }

  private static Labels cleanedLabels(Map<String, String> sourceTags) {
    Map<String, String> tags = new HashMap<>(sourceTags);
    tags.remove("__name__");
    tags.remove("__type__");
    tags.remove("__unit__");
    return new Labels(tags);
  }
}
