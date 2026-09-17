/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.tools.results;

import java.util.List;
import org.okapi.rest.logs.ChLogRow;

public record SearchLogsResultDetail(
    String toolCallId, int pageNumber, int totalPages, List<ChLogRow> lines) {}
