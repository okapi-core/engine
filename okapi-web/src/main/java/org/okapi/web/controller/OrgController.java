/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.controller;

import org.okapi.web.dtos.org.GetOrgResponse;
import org.okapi.web.service.orgs.OrgService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class OrgController {

  private final OrgService orgService;

  public OrgController(OrgService orgService) {
    this.orgService = orgService;
  }

  @GetMapping("/orgs/{orgId}")
  public GetOrgResponse getOrg(@PathVariable("orgId") String orgId) {
    return orgService.get(orgId);
  }
}
