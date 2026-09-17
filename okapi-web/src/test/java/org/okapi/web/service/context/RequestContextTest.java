/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.okapi.exceptions.BadRequestException;

class RequestContextTest {

  @Test
  void preservesTransportValues() {
    var context = new DashboardVersionRequestContext("org-1", "dashboard-1", "version-1");

    assertEquals("org-1", context.orgId());
    assertEquals("dashboard-1", context.dashboardId());
    assertEquals("version-1", context.versionId());
  }

  @Test
  void dashboardContextsRequireTheirFullResourceHierarchy() {
    assertThrows(BadRequestException.class, () -> new OrgRequestContext(""));
    assertThrows(BadRequestException.class, () -> new DashboardRequestContext("org-1", null));
    assertThrows(
        BadRequestException.class,
        () -> new DashboardVersionRequestContext("org-1", "dashboard-1", " "));
    assertThrows(
        BadRequestException.class,
        () -> new DashboardRowRequestContext("org-1", "dashboard-1", "version-1", ""));
    assertThrows(
        BadRequestException.class,
        () -> new DashboardPanelRequestContext("org-1", "dashboard-1", "version-1", "row-1", null));
  }
}
