/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.tools.results;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InMemoryObjectStore implements ObjectStore {

  private final ConcurrentHashMap<String, byte[]> results = new ConcurrentHashMap<>();

  @Override
  public void upload(String key, byte[] result) {
    results.put(key, result == null ? null : result.clone());
  }

  @Override
  public byte[] download(String key) {
    byte[] result = results.get(key);
    if (result == null) {
      throw new IllegalArgumentException("No result found for key: " + key);
    }
    return result.clone();
  }
}
