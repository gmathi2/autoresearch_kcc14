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

package com.knapp.codingcontest.data;

/**
 * Represents a position on the warehouse floor that an {@code AeroBot} can move to.
 *
 * <p>Waypoints form the navigable grid of the warehouse. Each waypoint has (x, y) coordinates
 * and a type indicating what area it represents.
 *
 * @see Rack
 * @see com.knapp.codingcontest.operations.ParkingArea
 * @see com.knapp.codingcontest.operations.PickArea
 * @see com.knapp.codingcontest.operations.ChargingArea
 */
public interface Waypoint {
  /**
   * The kind of area at this waypoint.
   */
  public static enum Type {
    /** The parking area where AeroBots start and can idle. */
    Parking,
    /** A storage rack with multiple vertical levels. */
    Rack,
    /** The picking area where orders are fulfilled. */
    Picking,
    /** The charging area where AeroBots recharge. */
    Charging,
  }

  // ----------------------------------------------------------------------------

  /**
   * @return the type of area at this waypoint
   */
  Type getType();

  /**
   * @return the unique identifier of this waypoint
   */
  String getCode();

  /**
   * @return the horizontal x-coordinate on the warehouse grid
   */
  int getX();

  /**
   * @return the horizontal y-coordinate on the warehouse grid
   */
  int getY();

  // ----------------------------------------------------------------------------

  /**
   * Calculates the Manhattan distance from this waypoint to another
   * (i.e. distance in x + distance in y).
   *
   * @param to the target waypoint
   * @return the distance in grid units
   */
  int distance(final Waypoint to);

  // ----------------------------------------------------------------------------
}
