/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.parse;

public record LabelMatcher(String name, LabelOp op, String value) {}
