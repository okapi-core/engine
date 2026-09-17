/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.tools.results;

import java.util.List;

public record PagedResultWrite(
    String toolCallId, int totalPages, int totalItems, List<String> pageKeys) {}
