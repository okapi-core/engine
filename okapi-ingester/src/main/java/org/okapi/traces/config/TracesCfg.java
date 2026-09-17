/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.config;

public interface TracesCfg {
  // disk
  String getDataDir();

  // page config
  int getMaxPageBytes();

  long getMaxPageWindowMs();

  int getExpectedInsertions();

  double getBloomFpp();

  // s3
  String getS3Bucket();

  String getS3BasePrefix();

  long getS3UploadGraceMs();

  long getIdxExpiryDuration();

  // buffer pool
  int getSealedPageCap();

  long getSealedPageTtlMs();

  long getBufferPoolFlushEvalMillis();
}
