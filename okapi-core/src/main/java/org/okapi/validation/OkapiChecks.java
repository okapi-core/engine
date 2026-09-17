/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.validation;

import java.util.function.Supplier;
import org.okapi.exceptions.BadRequestException;

public class OkapiChecks {

  public static void checkArgument(boolean condition, String message) throws BadRequestException {
    if (!condition) {
      throw new BadRequestException(message);
    }
  }

  public static <T extends Exception> void checkArgument(boolean condition, Supplier<T> elseThrow)
      throws T {
    if (!condition) {
      throw elseThrow.get();
    }
  }
}
