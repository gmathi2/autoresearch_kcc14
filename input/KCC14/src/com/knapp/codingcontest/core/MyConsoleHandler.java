/* -*- java -*-
# =========================================================================== #
#                                                                             #
#                         Copyright (C) KNAPP AG                              #
#                                                                             #
#       The copyright to the computer program(s) herein is the property       #
#       of Knapp.  The program(s) may be used   and/or copied only with       #
#       the  written permission of  Knapp  or in  accordance  with  the       #
#       terms and conditions stipulated in the agreement/contract under       #
#       which the program(s) have been supplied.                              #
#                                                                             #
# =========================================================================== #
*/

package com.knapp.codingcontest.core;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.logging.StreamHandler;

public class MyConsoleHandler extends StreamHandler {
  private static final OutputStream target;

  static {
    if ("stderr".equals(System.getProperty("logTarget", "stdout"))) {
      target = System.err;
    } else {
      target = System.out;
    }
  }

  public MyConsoleHandler() {
    this(MyConsoleHandler.target);
  }

  public MyConsoleHandler(final OutputStream target) {
    super(target, new MyFormatter());
  }

  @Override
  public void publish(final LogRecord record) {
    super.publish(record);
    flush();
  }

  @Override
  public void close() {
    flush();
  }

  public static class MyFormatter extends Formatter {
    private final String format = " %1$-8s : %2$s%3$s%n";

    @Override
    public String format(final LogRecord record) {
      final String message = formatMessage(record);
      String throwable = "";
      if (record.getThrown() != null) {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw);
        pw.println();
        record.getThrown().printStackTrace(pw);
        pw.close();
        throwable = sw.toString();
      }
      return String.format(format, record.getLevel().getName(), message, throwable);
    }
  }
}
