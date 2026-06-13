/*
 * Copyright The OkapiCore Authors
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
    return derived(id.labels().tags());
  }

  public static SeriesId derived(Map<String, String> sourceTags) {
    Map<String, String> tags = new HashMap<>(sourceTags);
    tags.remove("__name__");
    tags.remove("__type__");
    tags.remove("__unit__");
    return new SeriesId("", new Labels(tags));
  }
}
