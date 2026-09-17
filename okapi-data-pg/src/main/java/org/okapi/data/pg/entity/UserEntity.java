/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.okapi.data.model.User;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity {
  @Id
  @Column(name = "user_id")
  private String userId;

  private String email;

  @Enumerated(EnumType.STRING)
  private User.Status status;

  @Column(name = "first_name")
  private String firstName;

  @Column(name = "last_name")
  private String lastName;

  @Column(name = "hashed_password")
  private String hashedPassword;

  @Column(name = "org_id")
  private String orgId;
}
