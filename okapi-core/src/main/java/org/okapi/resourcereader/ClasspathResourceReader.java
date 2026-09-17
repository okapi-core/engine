/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.resourcereader;

import java.io.IOException;
import java.io.InputStream;

public class ClasspathResourceReader {
  public static String readResource(String resourcePath) {
    try (InputStream inputStream =
        Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
      if (inputStream == null) {
        throw new IllegalArgumentException("Resource not found: " + resourcePath);
      } else {
        return new String(inputStream.readAllBytes());
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to read resource: " + resourcePath, e);
    }
  }
}
