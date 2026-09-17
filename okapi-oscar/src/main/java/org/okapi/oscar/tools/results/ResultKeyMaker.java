/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.tools.results;

public interface ResultKeyMaker {
  String createKeyForPage(String toolCallId, int pageNumber);
}
