/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.agents;

public interface SreResearchAgent {

  void respond(String sessionId, long streamId, String userMessage);
}
