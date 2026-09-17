/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.tools.results;

public interface ObjectStore {
  void upload(String key, byte[] result);

  byte[] download(String key);
}
