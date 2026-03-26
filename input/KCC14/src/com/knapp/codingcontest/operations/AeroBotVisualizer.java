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

package com.knapp.codingcontest.operations;

import java.io.IOException;

/**
 * Visualizes AeroBot movements and detects conflicts where multiple bots
 * are at the same rack at level > 0 simultaneously.
 *
 * This tool helps students debug their Solution.java implementations by
 * showing exactly when and where coordination issues occur.
 */
public interface AeroBotVisualizer {
  /**
   * Generate HTML file with embedded data to avoid CORS issues.
   * Samples data at intervals for performance.
   */
  void generateHTML(String filename) throws IOException;

  /**
   * Check if there are any conflicts detected.
   */
  boolean hasConflicts();

  /**
   * Get the first conflict tick for debugging.
   */
  int getConflictCount();

  /**
   * Get total number of conflicts.
   */
  long getFirstConflictTick();
}
