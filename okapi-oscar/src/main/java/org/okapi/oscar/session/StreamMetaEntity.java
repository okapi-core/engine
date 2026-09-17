/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.session;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.okapi.rest.session.STREAM_STATE;

@Entity
@Table(name = "oscar_stream_meta")
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class StreamMetaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long streamId;

  @Column(name = "session_id", nullable = false)
  String sessionId;

  @Column(name = "start_time", nullable = false)
  long startTime;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  STREAM_STATE state;
}
