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

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import com.knapp.codingcontest.data.Waypoint;
import com.knapp.codingcontest.operations.AeroBot;
import com.knapp.codingcontest.operations.ParkingArea;

public class ParkingAreaInternal extends AbstractWaypoint implements ParkingArea {
  private final Map<String, AeroBot> aeroBots = new TreeMap<>();

  // ----------------------------------------------------------------------------

  public ParkingAreaInternal(final String code, final int x, final int y) {
    super(Waypoint.Type.Parking, code, x, y);
  }

  // ----------------------------------------------------------------------------

  @Override
  public Collection<AeroBot> getParkingAeroBots() {
    return Collections.unmodifiableCollection(aeroBots.values());
  }

  // ----------------------------------------------------------------------------

  @Override
  void enter(final AeroBotInternal aeroBot) {
    super.enter(aeroBot);
    aeroBots.put(aeroBot.getCode(), aeroBot);
    aeroBot.setOperationMode(AeroBot.OperationMode.Park);
  }

  @Override
  void leave(final AeroBotInternal aeroBot) {
    aeroBots.remove(aeroBot.getCode());
    super.leave(aeroBot);
  }

  // ----------------------------------------------------------------------------
  // ----------------------------------------------------------------------------
}
