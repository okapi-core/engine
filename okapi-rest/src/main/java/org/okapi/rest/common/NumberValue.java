/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
public class NumberValue {
  Long anInteger;
  Double aDouble;
}
