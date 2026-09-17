/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@Getter
@NoArgsConstructor
@Builder
public class TimeInterval {
  long start;
  long end;
}
