/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.tools.results;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public class GsonLogsResultSerializer implements LogsResultSerializer {

  private static final Gson GSON = new Gson();

  @Override
  public byte[] serialize(SearchLogsResultDetail detail) {
    return GSON.toJson(detail).getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public SearchLogsResultDetail deserialize(byte[] bytes) {
    return GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), SearchLogsResultDetail.class);
  }
}
