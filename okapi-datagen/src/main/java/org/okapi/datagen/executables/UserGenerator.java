/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.datagen.executables;

import com.google.gson.Gson;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.okapi.datagen.users.UserCredsGenerator;
import picocli.CommandLine;

@Slf4j
@CommandLine.Command(
    name = "users-gen",
    mixinStandardHelpOptions = true,
    version = "spans-gen 0.1",
    description = "Creates the default local Okapi test users.")
public class UserGenerator implements Callable<Integer> {

  @CommandLine.Option(
      names = {"-h", "--host"},
      description =
          "Okapi web host. Must start with http:// or https:// depending on the deployment.")
  private String host;

  @CommandLine.Option(
      names = {"-p", "--port"},
      description = "Okapi web port.")
  private int port;

  @Override
  public Integer call() throws Exception {
    var requests = UserCredsGenerator.createDefaultUsers();
    var okHttp = new OkHttpClient();
    var url = host + ":" + port + "/api/v1/users";
    var partialRequest = new Request.Builder().header("Content-Type", "application/json").url(url);
    var gson = new Gson();
    var dumpFile = createAndReturnDumpFile();
    try (var stream = new FileOutputStream(dumpFile.toFile())) {
      for (var req : requests) {
        var body = gson.toJson(req);
        var request = partialRequest.post(RequestBody.create(body.getBytes())).build();
        try (var resp = okHttp.newCall(request).execute()) {
          if (resp.isSuccessful()) {
            log.info("Created user: {}", req);
            stream.write(req.toString().getBytes());
            stream.write("\n".getBytes());
          } else {
            log.error("Could not create user: {} due to : {}", req, resp.body().string());
          }
        }
      }
      return 0;
    }
  }

  public static Path createAndReturnDumpFile() throws IOException {
    var home = System.getProperty("user.home");
    var fpath = Path.of(home, ".okapi", "user-data", "dump.txt");
    var parent = fpath.getParent();
    Files.createDirectories(parent);
    return fpath;
  }
}
