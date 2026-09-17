/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service;

import java.util.Map;
import org.okapi.exceptions.BadRequestException;

public interface QueryHydrator {
  String hydrate(String query, Map<String, Object> params) throws BadRequestException;
}
