/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.session;

import org.okapi.rest.session.STREAM_STATE;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface StreamMetaRepository extends JpaRepository<StreamMetaEntity, Long> {

  @Modifying
  @Transactional
  @Query("UPDATE StreamMetaEntity s SET s.state = :state WHERE s.streamId = :streamId")
  void updateState(Long streamId, STREAM_STATE state);
}
