/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.staticserver;

import com.google.gson.Gson;
import java.io.IOException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.okapi.staticserver.exceptions.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ApiProxy {

  @Autowired OkHttpClient client;

  public <T> ApiResponse<T> handleRequest(Request request, Class<T> clazz)
      throws BadRequestException, IOException {
    var gson = new Gson();
    try (var res = client.newCall(request).execute()) {
      // on error, throw a bad request exception
      ResponseEntityBuilder.handleError(res);
      var body = res.body();
      var data = gson.fromJson(body.string(), clazz);
      return ApiResponse.<T>builder().code(res.code()).data(data).build();
    }
  }
}
