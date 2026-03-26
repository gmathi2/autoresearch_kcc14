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

import java.io.Serializable;

import com.knapp.codingcontest.operations.AeroBot.OperationMode;

/**
 * A serializable snapshot of the current simulation state, including cost metrics
 * and per-bot statistics.
 *
 * <p>Obtain via {@link Warehouse#getInfoSnapshot()}.
 *
 * @see Warehouse#getInfoSnapshot()
 * @see CostFactors
 */
public interface InfoSnapshot extends Serializable {
  // ----------------------------------------------------------------------------

  /**
   * @return the number of orders that have not yet been picked
   */
  int getUnfinishedOrderCount();

  /**
   * @return the total number of ticks elapsed since the start of the simulation
   */
  int getTicksRuntime();

  // ----------------------------------------------------------------------------

  /**
   * @return costs of current unfinished orders
   */
  double getUnfinishedOrdersCost();

  /**
   * @return costs of current ticks run
   */
  double getTicksCost();

  /**
   * Returns the total cost used for ranking.
   *
   * <p>Locally excludes the time-based ranking factor.
   *
   * @return the total cost
   */
  double getTotalCost();

  /**
   * Returns per-bot statistics.
   *
   * @param aeroBot the bot to query
   * @return the statistics for the given bot
   */
  AeroBotStatistics getAeroBotStatistics(AeroBot aeroBot);

  /**
   * Returns aggregate statistics for a specific operation mode across all bots.
   *
   * @param mode the operation mode
   * @return the aggregated mode statistics
   */
  AeroBotStatistics.ModeStatistics getModeStatistics(OperationMode mode);

  /**
   * @return aggregate statistics across all bots and all modes
   */
  AeroBotStatistics.ModeStatistics getTotalStatistics();

  // ----------------------------------------------------------------------------

  /**
   * Per-bot statistics, broken down by {@link OperationMode}.
   */
  interface AeroBotStatistics {
    /**
     * Statistics for a specific {@link OperationMode}.
     */
    interface ModeStatistics {
      /** @return the number of operations executed in this mode */
      long get_count();

      /** @return the total tick-units consumed in this mode */
      long get_tick_units();

      /** @return the total ticks spent in this mode */
      long get_ticks();
    }

    /**
     * @param mode the operation mode
     * @return mode-specific statistics for this bot
     */
    ModeStatistics getModeStatistics(OperationMode mode);
  }

  // ----------------------------------------------------------------------------
}
