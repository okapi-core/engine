/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OkapiIngester {
  public static void main(String[] args) {
    SpringApplication.run(OkapiIngester.class, args);
  }
}
