/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.exceptions;

import lombok.experimental.StandardException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
@StandardException
public class UnAuthorizedException extends UserfacingException {}
