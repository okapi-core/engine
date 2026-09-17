/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.okapi.data.dao.UsersDao;
import org.okapi.web.auth.UserManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SmokeTests {

  @Autowired UserManager userManager;
  @Autowired UsersDao usersDao;

  @Test
  void contextLoads() {
    assertNotNull(userManager);
    assertNotNull(usersDao);
  }
}
