/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.spring.configs.properties;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@NoArgsConstructor
public class AbstractBaseTelemetryConfig {
  @Min(value = 1, message = "expectedInsertions must be > 0")
  int expectedInsertions;

  @Min(value = 1, message = "maxPageWindowMs must be > 0")
  long maxPageWindowMs;

  @Min(value = 1, message = "maxPageBytes must be > 0")
  int maxPageBytes;

  @DecimalMin(value = "0.0", inclusive = true, message = "bloomFpp must be ≥ 0.0")
  @DecimalMax(value = "1.0", inclusive = true, message = "bloomFpp must be ≤ 1.0")
  double bloomFpp;

  @Min(value = 1, message = "sealedPageCap must be > 0")
  int sealedPageCap;

  @Min(value = 1, message = "sealedPageTtlMs must be > 0")
  long sealedPageTtlMs;

  @NotBlank(message = "dataDir must not be blank")
  String dataDir;

  @Min(value = 1, message = "idxExpiryDuration must be > 0")
  long idxExpiryDuration;

  @NotBlank(message = "s3Bucket must not be blank")
  String s3Bucket;

  @NotBlank(message = "s3BasePrefix must not be blank")
  String s3BasePrefix;

  @Min(value = 0, message = "s3UploadGraceMs must be ≥ 0")
  long s3UploadGraceMs;

  @Min(value = 100, message = "bufferPoolFlushEvalMillis must be ≥ 100ms")
  long bufferPoolFlushEvalMillis;
}
