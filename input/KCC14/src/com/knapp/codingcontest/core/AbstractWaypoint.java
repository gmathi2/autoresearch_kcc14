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

import com.knapp.codingcontest.data.Waypoint;

public abstract class AbstractWaypoint implements Waypoint {
  // ----------------------------------------------------------------------------

  private final Type type;
  private final String code;
  private final int x;
  private final int y;

  // ----------------------------------------------------------------------------

  protected AbstractWaypoint(final Type type, final String code, final int x, final int y) {
    this.type = type;
    this.code = code;
    this.x = x;
    this.y = y;
  }

  // ----------------------------------------------------------------------------

  @Override
  public Type getType() {
    return type;
  }

  @Override
  public String getCode() {
    return code;
  }

  @Override
  public int getX() {
    return x;
  }

  @Override
  public int getY() {
    return y;
  }

  @Override
  public final String toString() {
    return "WP#" + code + "[" + type + ", x/y/lvl=" + x + "/" + y + "]";
  }

  // ----------------------------------------------------------------------------

  @Override
  public int distance(final Waypoint to) {
    return Math.abs(x - to.getX()) + Math.abs(y - to.getY());
  }

  // ----------------------------------------------------------------------------
  // ----------------------------------------------------------------------------

  void enter(final AeroBotInternal aeroBot) {
    aeroBot.setCurrentWaypoint(this);
  }

  @SuppressWarnings("unused")
  void leave(final AeroBotInternal aeroBot) {
  }

  // ----------------------------------------------------------------------------
  // ----------------------------------------------------------------------------
}
