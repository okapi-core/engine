/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.okapi.data.model.EntityType.ORG;
import static org.okapi.data.model.EntityType.USER;
import static org.okapi.data.model.RelationType.ORG_ADMIN;
import static org.okapi.data.model.RelationType.ORG_MEMBER;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.okapi.data.dao.OrgDao;
import org.okapi.data.dao.RelationGraphDao;
import org.okapi.data.model.EntityId;
import org.okapi.data.model.Organization;
import org.okapi.exceptions.NotFoundException;
import org.okapi.exceptions.UnAuthorizedException;

class AccessManagerTest {
  private final OrgDao orgDao = mock(OrgDao.class);
  private final RelationGraphDao relationGraphDao = mock(RelationGraphDao.class);
  private final AccessManager accessManager = new AccessManager(orgDao, relationGraphDao);

  @Test
  void rejectsAccessToUnknownOrganization() {
    when(orgDao.findById("missing")).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> accessManager.checkOrgMember("user", "missing"));
  }

  @Test
  void acceptsMembersAndAdminsAsOrganizationMembers() {
    var user = EntityId.of(USER, "user");
    var org = EntityId.of(ORG, "org");
    when(orgDao.findById("org")).thenReturn(Optional.of(organization()));
    when(relationGraphDao.hasRelationBetween(user, org, ORG_MEMBER)).thenReturn(true);

    assertDoesNotThrow(() -> accessManager.checkOrgMember("user", "org"));

    when(relationGraphDao.hasRelationBetween(user, org, ORG_MEMBER)).thenReturn(false);
    when(relationGraphDao.hasRelationBetween(user, org, ORG_ADMIN)).thenReturn(true);

    assertDoesNotThrow(() -> accessManager.checkOrgMember("user", "org"));
  }

  @Test
  void rejectsOutsidersAndNonAdminMembers() {
    var user = EntityId.of(USER, "user");
    var org = EntityId.of(ORG, "org");
    when(orgDao.findById("org")).thenReturn(Optional.of(organization()));

    assertThrows(UnAuthorizedException.class, () -> accessManager.checkOrgMember("user", "org"));
    assertThrows(UnAuthorizedException.class, () -> accessManager.checkOrgAdmin("user", "org"));
  }

  private static Organization organization() {
    return Organization.builder().orgId("org").orgName("Organization").build();
  }
}
