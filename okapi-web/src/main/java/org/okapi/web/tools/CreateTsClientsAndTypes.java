/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.tools;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

public class CreateTsClientsAndTypes {
  @AllArgsConstructor
  @NoArgsConstructor
  @Getter
  @ToString
  public static class FeSetup {
    @SerializedName("fe_project_root")
    String feProjectRoot;
  }

  public static void main(String[] args) throws Exception {
    var pkg = new String[] {"org.okapi"};
    CreateTsClient.main(pkg);
    CreateTsTypeFiles.main(pkg);

    var gson = new Gson();
    var fp = Path.of("./fe-setup.json");
    var setup = gson.fromJson(Files.readString(fp), FeSetup.class);
    var fePath = Path.of(setup.getFeProjectRoot());
    var copyLocation = fePath.resolve("src/lib");
    var toCopy = new String[] {"request-types.ts", "response-types.ts", "api.ts"};
    for (var artifact : toCopy) {
      var path = Path.of("./" + artifact);
      var cpPath = copyLocation.resolve(artifact);
      Files.copy(path, cpPath, StandardCopyOption.REPLACE_EXISTING);
      Files.delete(path);
    }
  }
}
