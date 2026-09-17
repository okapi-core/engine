/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ingester.client;

import com.google.gson.Gson;
import java.io.IOException;
import okhttp3.Response;
import org.okapi.exceptions.BadRequestException;
import org.okapi.exceptions.DownstreamFailedException;

public class ProxyResponseTranslator {
  Gson gson = new Gson();

  public <T> T translateResponse(Response response, Class<T> clazz) throws IOException {
    if (response.isSuccessful()) {
      var resBody = response.body().string();
      return gson.fromJson(resBody, clazz);
    } else if (response.code() == 400) {
      throw new BadRequestException(response.body().string());
    } else {
      throw new DownstreamFailedException();
    }
  }

  public String translateRawResponse(Response response) throws IOException {
    if (response.isSuccessful()) {
      return response.body().string();
    } else if (response.code() == 400) {
      throw new BadRequestException(response.body().string());
    } else {
      throw new DownstreamFailedException();
    }
  }
}
