/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@Builder
@NoArgsConstructor
@Getter
@Setter
public class Organization {
  private String orgId;
  private String orgName;
  private String orgCreator;
  private Instant created;
}
