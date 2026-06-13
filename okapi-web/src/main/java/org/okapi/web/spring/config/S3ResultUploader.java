/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.spring.config;

import java.util.Objects;
import org.okapi.data.dao.ResultUploader;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

public class S3ResultUploader implements ResultUploader {
  private final S3Client s3Client;
  private final String bucket;
  private final String rootPrefix;

  public S3ResultUploader(S3Client s3Client, String bucket, String rootPrefix) {
    this.s3Client = Objects.requireNonNull(s3Client, "s3Client");
    this.bucket = Objects.requireNonNull(bucket, "bucket");
    this.rootPrefix = trimSlashes(rootPrefix == null ? "" : rootPrefix);
  }

  @Override
  public String uploadResult(String orgId, String jobId, String resultData) {
    var objectKey = buildObjectKey(orgId, jobId);
    s3Client.putObject(
        PutObjectRequest.builder()
            .bucket(bucket)
            .key(objectKey)
            .contentType("application/json")
            .build(),
        RequestBody.fromString(resultData == null ? "" : resultData));
    return objectKey;
  }

  @Override
  public String getRawResult(String orgId, String jobId) {
    var objectKey = buildObjectKey(orgId, jobId);
    return s3Client
        .getObjectAsBytes(builder -> builder.bucket(bucket).key(objectKey))
        .asUtf8String();
  }

  private String buildObjectKey(String orgId, String jobId) {
    if (rootPrefix.isBlank()) return String.join("/", orgId, jobId, "result.json");
    return String.join("/", rootPrefix, orgId, jobId, "result.json");
  }

  private static String trimSlashes(String value) {
    var trimmed = value;
    while (trimmed.startsWith("/")) trimmed = trimmed.substring(1);
    while (trimmed.endsWith("/")) trimmed = trimmed.substring(0, trimmed.length() - 1);
    return trimmed;
  }
}
