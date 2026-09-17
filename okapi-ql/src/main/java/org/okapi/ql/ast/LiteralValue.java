/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ql.ast;

public sealed interface LiteralValue
    permits StringValue,
        IdentifierValue,
        IntegerValue,
        DecimalValue,
        DurationValue,
        TimestampValue,
        NowValue {}
