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

/**
 * Provides the cost factors used for ranking in the contest.
 *
 * <p>The total cost is calculated as:
 * {@code unfinishedOrders * unfinishedOrderCost + totalTicks * costPerTick}
 *
 * @see Warehouse#getCostFactors()
 * @see InfoSnapshot#getTotalCost()
 */
public interface CostFactors {
  /**
   * @return costs per {@link com.knapp.codingcontest.data.Order} that has not been picked
   */
  double getUnfinishedOrderCost();

  /**
   * @return cost per tick of runtime
   */
  double getCostPerTick();

}
