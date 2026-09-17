/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.datagen.users;

import java.util.List;
import org.okapi.web.dtos.auth.CreateUserRequest;

public class UserCredsGenerator {
  public static final String KUSHAL_EMAIL = "kushal@okapi.test";
  public static final String KUSHAL_PASSWORD = "password123";
  public static final String OSCAR_EMAIL = "oscar@okapi.test";
  public static final String OSCAR_PASSWORD = "oscar123";

  public static List<CreateUserRequest> createDefaultUsers() {
    return List.of(
        CreateUserRequest.builder()
            .firstName("Kushal")
            .lastName("Sharma")
            .email(KUSHAL_EMAIL)
            .password(KUSHAL_PASSWORD)
            .build(),
        CreateUserRequest.builder()
            .firstName("Oscar")
            .lastName("TheOkapi")
            .email(OSCAR_EMAIL)
            .password(OSCAR_PASSWORD)
            .build());
  }
}
