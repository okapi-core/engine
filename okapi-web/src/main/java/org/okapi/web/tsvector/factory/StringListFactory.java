/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.tsvector.factory;

import java.util.List;

public interface StringListFactory<T> {
  List<String> getStringList(T body);
}
