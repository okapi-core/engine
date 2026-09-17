/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.otel;

import io.opentelemetry.proto.resource.v1.Resource;
import java.util.Optional;

public class ResourceAttributesReader {
  public static final String SVC_NAME_ATTR = "service.name";

  public static Optional<String> getSvc(Resource resource) {
    var maybeSvc =
        resource.getAttributesList().stream()
            .filter(attr -> attr.getKey().equals(SVC_NAME_ATTR))
            .findFirst();

    if (maybeSvc.isPresent()) {
      var val = maybeSvc.get().getValue();
      if (val.hasStringValue() && !val.getStringValue().isBlank()) {
        return Optional.of(val.getStringValue());
      }
    }
    return Optional.empty();
  }
}
