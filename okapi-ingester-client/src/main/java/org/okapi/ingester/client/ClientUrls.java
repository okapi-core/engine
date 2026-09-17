/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ingester.client;

final class ClientUrls {

  private ClientUrls() {}

  static String concat(String endpoint, String path) {
    var sb = new StringBuilder();
    var arr = endpoint.toCharArray();
    int n = arr.length;
    if (endpoint.endsWith("/")) {
      n--;
    }
    for (int i = 0; i < n; i++) {
      sb.append(arr[i]);
    }
    if (path.startsWith("/")) {
      sb.append(path);
    } else {
      sb.append('/').append(path);
    }
    return sb.toString();
  }
}
