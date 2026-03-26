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

import java.util.List;

/**
 * A storage rack in the warehouse, acting as both a {@link Waypoint} and a vertical stack
 * of {@link RackStorageLocation}s.
 *
 * <p>Each rack has one or more levels. Each level is a {@link RackStorageLocation}
 * that can hold at most one {@link Container}.
 */
public interface Rack extends Waypoint {
  // ----------------------------------------------------------------------------

  /**
   * Returns all storage locations of this rack, ordered by level (ascending).
   * Note that the List is 0-based, but the levels are 1-based (i.e. level 1 = index 0).
   *
   * @return an unmodifiable list of all rack storage locations
   */
  List<RackStorageLocation> getRackStorageLocations();

  /**
   * Returns the storage location at the specified level.
   *
   * @param level the level (1 to top) of the desired storage location
   * @return the {@link RackStorageLocation} at that level or {@code null}
   *         if the level is zero.
   * @throws IllegalArgumentException if the level is out of range
   *         (less than 0 or greater than max level)
   */
  RackStorageLocation getRackStorageLocation(final int level);

  /**
   * @return the highest valid level for this rack (this is also the
   *         number of levels in the rack)
   */
  int getMaxLevel();

  // ----------------------------------------------------------------------------
  // ----------------------------------------------------------------------------

  /**
   * A single storage location within a {@link Rack} at a specific level.
   *
   * <p>Extends {@link Location} with rack-specific functionality.
   * Use {@link #peekContainer()} to inspect the currently stored container
   * without modifying state.
   */
  public abstract static class RackStorageLocation extends Location {
    /** The rack this storage location belongs to. */
    private final Rack rack;

    protected RackStorageLocation(final Rack rack, final String code, final int level) {
      super(Type.Rack, code, level);
      this.rack = rack;
    }

    /**
     * {@inheritDoc}
     *
     * @return the owning {@link Rack}
     */
    @Override
    public Rack getWaypoint() {
      return rack;
    }

    /**
     * Returns the container currently stored at this location, without removing it.
     *
     * @return the {@link Container} at this location, or {@code null} if empty
     */
    public abstract Container peekContainer();
  }

  // ----------------------------------------------------------------------------
}
