/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.nodes;

import java.util.List;
import java.util.Locale;
import org.okapi.promql.eval.EvalContext;
import org.okapi.promql.eval.exceptions.EvaluationException;

/**
 * A duration expression evaluated against the outer query context. Values are represented in
 * seconds.
 */
public sealed interface DurationExpr
    permits DurationExpr.Literal, DurationExpr.Unary, DurationExpr.Binary, DurationExpr.Function {

  double evalSeconds(EvalContext ctx);

  default long evalMs(EvalContext ctx) {
    double millis = evalSeconds(ctx) * 1000d;
    if (!Double.isFinite(millis) || millis > Long.MAX_VALUE || millis < Long.MIN_VALUE) {
      throw new EvaluationException("duration expression is out of range");
    }
    return Math.round(millis);
  }

  static DurationExpr fixedMs(long millis) {
    return new Literal(millis / 1000d);
  }

  record Literal(double seconds) implements DurationExpr {
    @Override
    public double evalSeconds(EvalContext ctx) {
      return seconds;
    }
  }

  record Unary(String op, DurationExpr inner) implements DurationExpr {
    @Override
    public double evalSeconds(EvalContext ctx) {
      double value = inner.evalSeconds(ctx);
      return switch (op) {
        case "+" -> value;
        case "-" -> -value;
        default -> throw new EvaluationException("unknown duration unary operator: " + op);
      };
    }
  }

  record Binary(String op, DurationExpr left, DurationExpr right) implements DurationExpr {
    @Override
    public double evalSeconds(EvalContext ctx) {
      double a = left.evalSeconds(ctx);
      double b = right.evalSeconds(ctx);
      return switch (op) {
        case "+" -> a + b;
        case "-" -> a - b;
        case "*" -> a * b;
        case "/" -> a / b;
        case "%" -> a % b;
        case "^" -> Math.pow(a, b);
        default -> throw new EvaluationException("unknown duration operator: " + op);
      };
    }
  }

  record Function(String name, List<DurationExpr> args) implements DurationExpr {
    @Override
    public double evalSeconds(EvalContext ctx) {
      return switch (name.toLowerCase(Locale.ROOT)) {
        case "step" -> {
          requireArgCount(0);
          yield ctx.stepMs / 1000d;
        }
        case "range" -> {
          requireArgCount(0);
          yield (ctx.endMs - ctx.startMs) / 1000d;
        }
        case "min" -> {
          requireArgCount(2);
          yield Math.min(args.get(0).evalSeconds(ctx), args.get(1).evalSeconds(ctx));
        }
        case "max" -> {
          requireArgCount(2);
          yield Math.max(args.get(0).evalSeconds(ctx), args.get(1).evalSeconds(ctx));
        }
        default -> throw new EvaluationException("unknown duration function: " + name);
      };
    }

    private void requireArgCount(int expected) {
      if (args.size() != expected) {
        throw new EvaluationException(
            name + ": expected " + expected + " duration arguments, got " + args.size());
      }
    }
  }
}
