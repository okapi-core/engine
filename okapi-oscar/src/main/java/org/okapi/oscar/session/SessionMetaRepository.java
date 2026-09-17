/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.session;

import java.util.List;
import org.okapi.rest.session.SESSION_STATE;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface SessionMetaRepository extends JpaRepository<SessionMetaEntity, String> {

  @Modifying
  @Transactional
  @Query("UPDATE SessionMetaEntity s SET s.lastRecordedPing = :ts WHERE s.sessionId = :sessionId")
  void updateLastRecordedPing(String sessionId, long ts);

  @Modifying
  @Transactional
  @Query("UPDATE SessionMetaEntity s SET s.ongoingStream = :stream WHERE s.sessionId = :sessionId")
  void updateOngoingStream(String sessionId, StreamMetaEntity stream);

  @Modifying
  @Transactional
  @Query("UPDATE SessionMetaEntity s SET s.state = :state WHERE s.sessionId = :sessionId")
  void updateState(String sessionId, SESSION_STATE state);

  List<SessionMetaEntity> findByOwnerIdAndStartTimeBetweenOrderByStartTimeDesc(
      String ownerId, long startTime, long endTime);

  List<SessionMetaEntity> findByOwnerIdAndStartTimeBetweenOrderByStartTimeDesc(
      String ownerId, long startTime, long endTime, Pageable pageable);
}
