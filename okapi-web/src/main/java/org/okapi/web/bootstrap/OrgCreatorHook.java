/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.bootstrap;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.okapi.data.dao.OrgDao;
import org.okapi.data.model.Organization;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrgCreatorHook implements ApplicationRunner {
  public static final String OKAPI_ROOT = "okapi_root";
  private final OrgDao orgDao;
  private final OkapiOrgProperties orgProperties;

  @Override
  public void run(ApplicationArguments args) throws Exception {
    log.info("Creating org: {} with name {}", orgProperties.getOrgId(), orgProperties.getOrgName());
    var created =
        orgDao.createIfNotExists(
            Organization.builder()
                .orgCreator(OKAPI_ROOT)
                .orgId(orgProperties.getOrgId())
                .orgName(orgProperties.getOrgName())
                .created(Instant.now())
                .build());
    if (created) {
      log.info("Created org");
    } else {
      log.info("Org already exists");
    }
  }
}
