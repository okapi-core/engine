/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.chat;

import java.util.List;
import org.okapi.rest.chat.CHAT_ROLE;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {

  interface MessageRoleCount {
    String getSessionId();

    CHAT_ROLE getRole();

    long getCount();
  }

  List<ChatMessageEntity> findBySessionIdAndTsMillisBetweenOrderByTsMillisAsc(
      String sessionId, Long fromMillis, Long toMillis);

  List<ChatMessageEntity> findBySessionIdAndEventStreamIdOrderByTsMillisAsc(
      String sessionId, long eventStreamId);

  @Query(
      """
      SELECT m.sessionId AS sessionId, m.role AS role, COUNT(m) AS count
      FROM ChatMessageEntity m
      WHERE m.sessionId IN :sessionIds
      GROUP BY m.sessionId, m.role
      """)
  List<MessageRoleCount> countBySessionIdInGroupByRole(
      @Param("sessionIds") List<String> sessionIds);
}
