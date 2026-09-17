/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.tools.results;

import org.springframework.stereotype.Component;

@Component
public class DefaultResultKeyMaker implements ResultKeyMaker {

  @Override
  public String createKeyForPage(String toolCallId, int pageNumber) {
    if (toolCallId == null || toolCallId.isBlank()) {
      throw new IllegalArgumentException("toolCallId must be present");
    }
    if (pageNumber < 1) {
      throw new IllegalArgumentException("pageNumber must be one-based");
    }
    return "tool-results/" + toolCallId + "/pages/" + pageNumber + ".json";
  }
}
