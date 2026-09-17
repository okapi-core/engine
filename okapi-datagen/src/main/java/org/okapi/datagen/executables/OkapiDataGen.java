/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.datagen.executables;

import java.util.concurrent.Callable;
import picocli.CommandLine;

@CommandLine.Command(
    name = "okapi-datagen",
    mixinStandardHelpOptions = true,
    description = "Okapi generator for testing.",
    synopsisSubcommandLabel = "COMMAND",
    usageHelpAutoWidth = true,
    subcommands = {AstronomySpansGen.class, AstronomyMetricsGen.class, UserGenerator.class})
public class OkapiDataGen implements Callable<Integer> {

  @CommandLine.Spec private CommandLine.Model.CommandSpec spec;

  @Override
  public Integer call() {
    spec.commandLine().usage(System.out);
    return 0;
  }

  public static void main(String[] args) {
    int exitCode = new CommandLine(new OkapiDataGen()).execute(args);
    System.exit(exitCode);
  }
}
