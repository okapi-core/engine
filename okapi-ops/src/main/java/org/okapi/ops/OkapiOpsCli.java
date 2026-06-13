/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ops;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.util.concurrent.Callable;

@Command(
    name = "okapi-ops",
    mixinStandardHelpOptions = true,
    description = "Okapi operational tooling.",
    synopsisSubcommandLabel = "COMMAND",
    usageHelpAutoWidth = true,
    subcommands = {
      ChMigrateCommand.class,
      PgMigrateCommand.class,
      PgValidateCommand.class,
      CreateSecretsCommand.class
    })
public class OkapiOpsCli implements Callable<Integer> {
  @Spec private CommandSpec spec;

  @Override
  public Integer call() {
    spec.commandLine().usage(System.out);
    return 0;
  }

  public static void main(String[] args) {
    int exitCode = new CommandLine(new OkapiOpsCli()).execute(args);
    System.exit(exitCode);
  }
}
