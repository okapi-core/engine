/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ops;

import java.util.concurrent.Callable;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@SpringBootApplication
@Command(
    name = "okapi-ops",
    mixinStandardHelpOptions = true,
    description = "Okapi operational tooling.",
    synopsisSubcommandLabel = "COMMAND",
    usageHelpAutoWidth = true,
    subcommands = {ChMigrateCommand.class, PgMigrateCommand.class, PgValidateCommand.class})
public class OkapiOpsCli implements Callable<Integer> {
  @Spec private CommandSpec spec;

  @Override
  public Integer call() {
    spec.commandLine().usage(System.out);
    return 0;
  }

  public static void main(String[] args) {
    var application = new SpringApplication(OkapiOpsCli.class);
    application.setWebApplicationType(WebApplicationType.NONE);
    var context = application.run(args);
    int exitCode;
    try {
      exitCode = new CommandLine(new OkapiOpsCli()).execute(args);
    } finally {
      context.close();
    }
    System.exit(exitCode);
  }
}
