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

import com.knapp.codingcontest.data.Waypoint;

/**
 * The charging area in the warehouse where AeroBots can recharge their battery.
 *
 * <p>Extends {@link Waypoint} so it can be used as a navigation target
 * in {@link AeroBot#planMoveToWaypoint(Waypoint)}.
 *
 * <p>The charging area has a limited number of slots. Each slot can be occupied
 * by at most one bot at a time. When there are no free slots, the bot will wait
 * until one becomes available before it can start charging.
 *
 * @see AeroBot#planStartCharge()
 * @see Warehouse#calculateStartCharge()
 */
public interface ChargingArea extends Waypoint {
  // ----------------------------------------------------------------------------

  /**
   * @return the total number of charging slots
   */
  int getChargingSlots();

  /**
   * @return the number of currently free (unoccupied) charging slots
   */
  int getFreeChargingSlots();

  /**
   * @return {@code true} if at least one charging slot is available
   */
  boolean hasFreeChargingSlot();

  // ----------------------------------------------------------------------------
}
