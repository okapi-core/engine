/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import org.okapi.web.dtos.dashboards.yaml.LintDashboardYamlResponse;
import org.okapi.web.dtos.dashboards.yaml.YamlLintIssue;
import org.okapi.web.yaml.DashboardYamlLinter;
import org.okapi.web.yaml.DashboardYamlParser;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/** Lints dashboard YAML files from the command line. */
@SpringBootConfiguration
@Command(
    name = "dashboard-yaml-lint",
    mixinStandardHelpOptions = true,
    description = "Lint dashboard YAML files.")
public class DashboardYamlLintCli implements Callable<Integer> {
  @Parameters(
      index = "0",
      arity = "0..1",
      defaultValue = "dashboard-yamls",
      description = "Directory containing dashboard YAML files.")
  private Path root;

  public static void main(String[] args) {
    int exitCode;
    try (var context =
        new SpringApplicationBuilder(DashboardYamlLintCli.class)
            .web(WebApplicationType.NONE)
            .logStartupInfo(false)
            .run(args)) {
      exitCode = new CommandLine(new DashboardYamlLintCli()).execute(args);
    }
    System.exit(exitCode);
  }

  @Override
  public Integer call() {
    if (!Files.isDirectory(root)) {
      System.err.println("Dashboard YAML directory does not exist: " + root);
      return 2;
    }

    var parser = new DashboardYamlParser();
    var linter = new DashboardYamlLinter();
    List<Path> files;
    try (var paths = Files.walk(root)) {
      files =
          paths
              .filter(Files::isRegularFile)
              .filter(DashboardYamlLintCli::isYamlFile)
              .sorted()
              .toList();
    } catch (IOException e) {
      System.err.println("Could not read dashboard YAML directory " + root + ": " + e.getMessage());
      return 2;
    }

    if (files.isEmpty()) {
      System.err.println("No YAML files found under: " + root);
      return 2;
    }

    var invalidFiles = 0;
    for (var file : files) {
      var result = lint(file, parser, linter);
      if (!result.isOk()) {
        invalidFiles++;
        printIssues(file, result.getErrors());
      }
      printIssues(file, result.getWarnings(), "warning");
    }

    var validFiles = files.size() - invalidFiles;
    System.out.printf(
        "Linted %d dashboard YAML files: %d valid, %d invalid.%n",
        files.size(), validFiles, invalidFiles);
    return invalidFiles == 0 ? 0 : 1;
  }

  private static LintDashboardYamlResponse lint(
      Path file, DashboardYamlParser parser, DashboardYamlLinter linter) {
    try {
      return linter.lint(parser.parse(Files.readString(file)), null);
    } catch (IOException | RuntimeException e) {
      var issue =
          YamlLintIssue.builder()
              .code("YAML_READ_FAILED")
              .message(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
              .path("$")
              .build();
      return LintDashboardYamlResponse.builder().ok(false).errors(List.of(issue)).build();
    }
  }

  private static void printIssues(Path file, List<YamlLintIssue> issues) {
    printIssues(file, issues, "error");
  }

  private static void printIssues(Path file, List<YamlLintIssue> issues, String severity) {
    if (issues == null) return;
    for (var issue : issues) {
      System.err.printf("%s: %s: %s: %s%n", file, severity, issue.getPath(), issue.getMessage());
    }
  }

  private static boolean isYamlFile(Path path) {
    var name = path.getFileName().toString().toLowerCase();
    return name.endsWith(".yaml") || name.endsWith(".yml");
  }
}
