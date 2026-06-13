/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.dao;

public interface ResultUploader {
  String uploadResult(String orgId, String jobId, String resultData);

  String getRawResult(String orgId, String jobId);
}
