/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.engine.ch;

import java.util.List;

public interface ChQueryNode {
  List<ChQueryNode> getChildren();
}
