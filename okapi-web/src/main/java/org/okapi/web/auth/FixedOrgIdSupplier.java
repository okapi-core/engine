/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.auth;

import lombok.RequiredArgsConstructor;
import org.okapi.web.bootstrap.OkapiOrgProperties;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FixedOrgIdSupplier implements OrgIdSupplier {
  private final OkapiOrgProperties orgProperties;

  @Override
  public String getOrgId() {
    return orgProperties.getOrgId();
  }
}
