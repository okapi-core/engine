/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg;

import static org.okapi.data.pg.PgKeys.key;

import java.util.List;
import java.util.Optional;
import org.okapi.data.dao.UserEntityRelationsDao;
import org.okapi.data.model.EntityRelationId;
import org.okapi.data.model.UserEntityRelation;

public final class UserEntityRelationsDaoPg implements UserEntityRelationsDao {
  private final JdbcRecordStore store;

  public UserEntityRelationsDaoPg(JdbcRecordStore store) {
    this.store = store;
  }

  private String edge(EntityRelationId edge) {
    return key(edge.entityType().name(), edge.entityId(), edge.relationType().name());
  }

  public Optional<UserEntityRelation> getRelation(String user, EntityRelationId edge) {
    return store.get("user-entity-relation", key(user, edge(edge)), UserEntityRelation.class);
  }

  public List<UserEntityRelation> listUserRelations(String user) {
    return store.list("user-entity-relation", user, null, UserEntityRelation.class);
  }

  public void createRelation(UserEntityRelation relation) {
    store.put(
        "user-entity-relation",
        key(relation.getUserId(), edge(relation.getEdgeId())),
        relation.getUserId(),
        null,
        null,
        null,
        relation);
  }

  public void deleteRelation(String user, EntityRelationId edge) {
    store.delete("user-entity-relation", key(user, edge(edge)));
  }
}
