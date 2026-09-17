/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.demo.rest;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Getter
public class CharCountRequest {
  String sentence;
}
