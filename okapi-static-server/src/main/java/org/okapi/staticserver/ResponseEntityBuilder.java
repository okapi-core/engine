/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.staticserver;

import java.io.IOException;
import okhttp3.Response;
import org.okapi.staticserver.exceptions.BadRequestException;

public class ResponseEntityBuilder {

  public static void handleError(Response response) throws BadRequestException, IOException {
    if (response.code() >= 400 && response.code() <= 499) {
      throw new BadRequestException(response.code(), response.body().string());
    }
  }
}
