/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.model;

import java.util.List;

public record EdgeSequence(List<OutgoingEdge> accepted) {}
