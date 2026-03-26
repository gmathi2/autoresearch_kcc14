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

import java.util.List;

import com.knapp.codingcontest.data.Order;
import com.knapp.codingcontest.data.Waypoint;

/**
 * The picking area where orders are fulfilled.
 *
 * <p>Extends {@link Waypoint} so it can be used as a navigation target.
 * AeroBots must be at this waypoint to execute {@link AeroBot#planPick(Order)}.
 *
 * @see AeroBot#planPick(Order)
 */
public interface PickArea extends Waypoint {
  // ----------------------------------------------------------------------------

  /**
   * Returns the orders currently allowed to be picked at the picking area.
   *
   * <p>This represents the multiple pick stations at the pick area, each
   * of which can have one order available for picking. Selecting a specific pick
   * station is not necessary, as an AeroBot can pick any current order at the pick area.
   * 
   * <p>This list will automatically change over time as orders are fulfilled and new orders
   * become available in FIFO sequence of the input order list.
   *
   * @return a read-only list of current {@link Order}s at the pick area
   */
  List<Order> getCurrentOrders();

  // ----------------------------------------------------------------------------
}
