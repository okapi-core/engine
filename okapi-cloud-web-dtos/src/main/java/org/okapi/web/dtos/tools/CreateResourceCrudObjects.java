/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.dtos.tools;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

/**
 * This tool helps create CRUD objects for a new resource. Usage: java CreateResourceCrudObjects
 * <ResourceName> Example: java CreateResourceCrudObjects User
 *
 * <p>It generates the following classes inside okapi-cloud-web-dtos under package
 * org.okapi.web.dtos.<resourcename> (lowercase resource name):
 *
 * <ul>
 *   <li>CreateUserRequest.java
 *   <li>UpdateUserRequest.java
 *   <li>GetUserResponse.java
 *   <li>ListUsersResponse.java
 *   <li>DeleteUserRequest.java
 * </ul>
 *
 * ListUsersResponse.java has a default implementation that includes a list of GetUserResponse
 * objects and Pagination info. - Pagination info is represented by a PaginationInfo class that
 * includes nextPageToken and other relevant fields.
 */
public class CreateResourceCrudObjects {
  public static void main(String[] args) {
    if (args == null || args.length < 1) {
      System.err.println(
          "Usage: java CreateResourceCrudObjects <ResourceName>\n"
              + "Example: java CreateResourceCrudObjects User");
      System.exit(1);
      return;
    }

    String rawResourceName = args[0].trim();
    if (rawResourceName.isBlank()) {
      System.err.println("Resource name must be non-empty.");
      System.exit(1);
      return;
    }
    if (!isAlphabetic(rawResourceName)) {
      System.err.println("Resource name must contain only alphabetic characters (A-Z or a-z).");
      System.exit(1);
      return;
    }

    String resourceName = sanitizeAndCapitalize(rawResourceName);
    String resourceLower = rawResourceName.toLowerCase(Locale.ROOT);
    String packageName = "org.okapi.web.dtos." + resourceLower;

    try {
      Path moduleRoot = Paths.get("okapi-cloud-web-dtos");
      Path baseJava =
          Files.exists(moduleRoot)
              ? moduleRoot.resolve(Paths.get("src", "main", "java"))
              : Paths.get("src", "main", "java");
      var packageDir = baseJava.resolve(packageName.replace('.', File.separatorChar));
      Files.createDirectories(packageDir);

      writeFile(
          packageDir.resolve("Create" + resourceName + "Request.java"),
          renderSimpleRequest("Create", resourceName, packageName, false));
      writeFile(
          packageDir.resolve("Update" + resourceName + "Request.java"),
          renderSimpleRequest("Update", resourceName, packageName, true));
      writeFile(
          packageDir.resolve("Get" + resourceName + "Response.java"),
          renderGetResponse(resourceName, packageName));
      writeFile(
          packageDir.resolve("Delete" + resourceName + "Request.java"),
          renderSimpleRequest("Delete", resourceName, packageName, false));
      writeFile(
          packageDir.resolve("List" + pluralize(resourceName) + "Response.java"),
          renderListResponse(resourceName, packageName));
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(2);
    }
  }

  private static void writeFile(Path path, String content) throws IOException {
    if (Files.exists(path)) {
      System.out.println("Skipping existing file: " + path);
      return;
    }

    Files.writeString(path, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    System.out.println("Created file: " + path.toAbsolutePath());
  }

  private static String renderSimpleRequest(
      String prefix, String resourceName, String packageName, boolean toBuilder) {
    String className = prefix + resourceName + "Request";
    String builderAnnotation = toBuilder ? "@Builder(toBuilder = true)" : "@Builder";
    return ""
        + "package "
        + packageName
        + ";\n"
        + "\n"
        + "import lombok.AllArgsConstructor;\n"
        + "import lombok.Builder;\n"
        + "import lombok.Getter;\n"
        + "import lombok.NoArgsConstructor;\n"
        + "\n"
        + "@Getter\n"
        + "@AllArgsConstructor\n"
        + "@NoArgsConstructor\n"
        + builderAnnotation
        + "\n"
        + "public class "
        + className
        + " {\n"
        + "  // TODO: add fields for "
        + prefix.toLowerCase()
        + " "
        + decapitalize(resourceName)
        + "\n"
        + "}\n";
  }

  private static String renderGetResponse(String resourceName, String packageName) {
    String className = "Get" + resourceName + "Response";
    return ""
        + "package "
        + packageName
        + ";\n"
        + "\n"
        + "import lombok.AllArgsConstructor;\n"
        + "import lombok.Builder;\n"
        + "import lombok.Getter;\n"
        + "import lombok.NoArgsConstructor;\n"
        + "\n"
        + "@Getter\n"
        + "@AllArgsConstructor\n"
        + "@NoArgsConstructor\n"
        + "@Builder(toBuilder = true)\n"
        + "public class "
        + className
        + " {\n"
        + "  // TODO: add fields representing "
        + decapitalize(resourceName)
        + "\n"
        + "}\n";
  }

  private static String renderListResponse(String resourceName, String packageName) {
    String plural = pluralize(resourceName);
    String className = "List" + plural + "Response";
    String listFieldName = decapitalize(plural);
    return ""
        + "package "
        + packageName
        + ";\n"
        + "\n"
        + "import jakarta.validation.constraints.NotNull;\n"
        + "import java.util.List;\n"
        + "import lombok.AllArgsConstructor;\n"
        + "import lombok.Builder;\n"
        + "import lombok.Getter;\n"
        + "import lombok.NoArgsConstructor;\n"
        + "import org.okapi.web.dtos.pagination.PaginationInfo;\n"
        + "\n"
        + "@Getter\n"
        + "@AllArgsConstructor\n"
        + "@NoArgsConstructor\n"
        + "@Builder(toBuilder = true)\n"
        + "public class "
        + className
        + " {\n"
        + "  @NotNull\n"
        + "  private List<Get"
        + resourceName
        + "Response> "
        + listFieldName
        + ";\n"
        + "  private PaginationInfo pagination;\n"
        + "}\n";
  }

  private static String pluralize(String resourceName) {
    if (resourceName == null || resourceName.isEmpty()) {
      return "";
    }
    String lower = resourceName.toLowerCase();
    if (lower.endsWith("y")
        && resourceName.length() > 1
        && !isVowel(lower.charAt(lower.length() - 2))) {
      return resourceName.substring(0, resourceName.length() - 1) + "ies";
    }
    if (lower.endsWith("s")
        || lower.endsWith("x")
        || lower.endsWith("z")
        || lower.endsWith("ch")
        || lower.endsWith("sh")) {
      return resourceName + "es";
    }
    return resourceName + "s";
  }

  private static boolean isVowel(char c) {
    return "aeiou".indexOf(Character.toLowerCase(c)) >= 0;
  }

  private static String sanitizeAndCapitalize(String input) {
    if (input == null) return "";
    String trimmed = input.trim();
    if (trimmed.isEmpty()) return "";
    return Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1);
  }

  private static String decapitalize(String value) {
    if (value == null || value.isEmpty()) return "";
    return Character.toLowerCase(value.charAt(0)) + value.substring(1);
  }

  private static boolean isAlphabetic(String value) {
    return value != null && !value.isBlank() && value.matches("^[A-Za-z]+$");
  }
}
