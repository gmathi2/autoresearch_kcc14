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
 * Represents a location where a {@link Container} can reside.
 *
 * <p>A location is either a {@link Rack.RackStorageLocation} within a rack
 * or an implicit location on an {@code AeroBot} (when the container is loaded).
 *
 * <p>This class is read-only from the solution's perspective.
 */
public abstract class Location {
  /**
   * Possible kinds of location.
   */
  public static enum Type {
    /** The container is stored in a {@link Rack}. */
    Rack,
    /** The container is loaded onto an {@code AeroBot}. */
    AeroBot,
  }

  /** The kind of this location. */
  private final Type type;
  /** Identifier code (rack code or bot code). */
  private final String code;
  /** The vertical level within the rack (1 to top), or 0 for an AeroBot location. */
  private final int level;

  protected Location(final Type type, final String code, final int level) {
    this.type = type;
    this.code = code;
    this.level = level;
  }

  @Override
  public String toString() {
    return getCode();
  }

  /**
   * @return the kind of this location ({@link Type#Rack} or {@link Type#AeroBot})
   */
  public Type getType() {
    return type;
  }

  /**
   * @return the identifier code of this location
   */
  public String getCode() {
    return code;
  }

  /**
   * @return the vertical level (1 to top) &mdash; meaningful for rack locations, 0 otherwise
   */
  public int getLevel() {
    return level;
  }

  /**
   * Returns the {@link Waypoint} associated with this location.
   *
   * <p>The waypoint of the rack on the floor for {@link Rack.RackStorageLocation}s.
   * Returns {@code null} for AeroBot locations.
   *
   * @return the associated waypoint, or {@code null}
   */
  public Waypoint getWaypoint() {
    return null;
  }
}
