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

import java.util.Collection;

import com.knapp.codingcontest.data.Waypoint;

/**
 * The parking area where AeroBots start and can idle between tasks.
 *
 * <p>Extends {@link Waypoint} so it can be used as a navigation target.
 *
 * @see AeroBot
 */
public interface ParkingArea extends Waypoint {
  // ----------------------------------------------------------------------------

  /**
   * Returns the AeroBots currently parked at this area.
   *
   * @return a read-only collection of parked {@link AeroBot}s
   */
  Collection<AeroBot> getParkingAeroBots();
}
