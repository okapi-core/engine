/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.auth;

import org.okapi.data.dao.UsersDao;
import org.okapi.web.dtos.dashboards.PersonalName;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsManager {

  UsersDao usersDao;

  public UserDetailsManager(UsersDao usersDao) {
    this.usersDao = usersDao;
  }

  public PersonalName getUserPersonalName(String userId) {
    var user = usersDao.get(userId);
    if (user.isEmpty()) {
      return PersonalName.builder().build();
    }
    return PersonalName.builder()
        .userId(userId)
        .firstName(user.get().getFirstName())
        .lastName(user.get().getLastName())
        .build();
  }
}
