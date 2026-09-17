/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.spring.configs.properties;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties(prefix = "okapi.s3.upload")
@Component
public class S3UploadCfg {
  @Min(value = 100, message = "uploadDelayMs must be ≥ 100ms")
  long uploadDelayMs;
}
