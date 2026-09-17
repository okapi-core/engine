/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.context;

import static org.okapi.validation.OkapiChecks.checkArgument;

public record OrgRequestContext(String orgId) {
  public OrgRequestContext {
    checkArgument(orgId != null && !orgId.isBlank(), "Organization ID is required");
  }
}
