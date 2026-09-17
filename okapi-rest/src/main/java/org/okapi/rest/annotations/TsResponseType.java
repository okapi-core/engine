/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a type to be included in the generated TypeScript response-types.ts file, regardless of its
 * class name suffix.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TsResponseType {}
