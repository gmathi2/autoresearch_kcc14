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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.knapp.codingcontest.core.OperationInternal.FinStatus;
import com.knapp.codingcontest.data.Waypoint;
import com.knapp.codingcontest.operations.AeroBot;
import com.knapp.codingcontest.operations.AeroBot.OperationMode;
import com.knapp.codingcontest.operations.ChargingArea;

public class ChargingAreaInternal extends AbstractWaypoint implements ChargingArea {
  private final InputDataInternal iinput;
  private final int chargingSlots;
  private final int poolSize;
  private final Map<String, AeroBotInternal> chargingAeroBots = new TreeMap<>();
  private final Map<String, AeroBotInternal> waitingAeroBots = new LinkedHashMap<>();
  private final Map<String, OperationInternal> plannedOps = new LinkedHashMap<>();

  // ----------------------------------------------------------------------------

  public ChargingAreaInternal(final InputDataInternal iinput, final String code, final int x, final int y,
      final int chargingSlots, final int poolSize) {
    super(Waypoint.Type.Charging, code, x, y);
    this.iinput = iinput;
    this.chargingSlots = chargingSlots;
    this.poolSize = poolSize;
  }

  // ----------------------------------------------------------------------------

  @Override
  public int getChargingSlots() {
    return chargingSlots;
  }

  @Override
  public int getFreeChargingSlots() {
    return (chargingSlots - chargingAeroBots.size());
  }

  @Override
  public boolean hasFreeChargingSlot() {
    return (chargingAeroBots.size() < chargingSlots);
  }

  // ----------------------------------------------------------------------------

  boolean hasFreeWaitingSlot() {
    return ((chargingAeroBots.size() + waitingAeroBots.size()) < poolSize);
  }

  boolean isCharging(final AeroBotInternal aeroBot) {
    return chargingAeroBots.containsKey(aeroBot.getCode());
  }

  void startCharging(final AeroBotInternal aeroBot) {
    final AeroBotInternal wb = waitingAeroBots.remove(aeroBot.getCode());
    if (wb == null) {
      throw new IllegalStateException("not waiting: " + aeroBot);
    }
    chargingAeroBots.put(aeroBot.getCode(), wb);
  }

  void doneCharging(final AeroBotInternal aeroBot) {
    final AeroBotInternal cb = chargingAeroBots.remove(aeroBot.getCode());
    if (cb == null) {
      throw new IllegalStateException("not charging: " + aeroBot);
    }
    waitingAeroBots.put(aeroBot.getCode(), cb);
    aeroBot.setOperationMode(AeroBot.OperationMode._Idle_);
  }

  // ----------------------------------------------------------------------------

  void planned(final OperationInternal.AeroBotCharge op) {
    plannedOps.put(op.aeroBot.getCode(), op);
  }

  @Override
  public void enter(final AeroBotInternal aeroBot) {
    super.enter(aeroBot);
    waitingAeroBots.put(aeroBot.getCode(), aeroBot);
    aeroBot.setOperationMode(AeroBot.OperationMode._Idle_);
  }

  @Override
  public void leave(final AeroBotInternal aeroBot) {
    waitingAeroBots.remove(aeroBot.getCode());
    chargingAeroBots.remove(aeroBot.getCode());
    final OperationInternal op0 = plannedOps.remove(aeroBot.getCode());
    if (op0 == null) {
      iinput.iwarehouse.log(Level.WARNING, "WARNING: leave charging-area without charging?!?");
    }
    super.leave(aeroBot);
  }

  // ----------------------------------------------------------------------------

  boolean inSequenceToCharge(final AeroBotInternal aeroBot) {
    int availableSlots = chargingSlots - chargingAeroBots.size();
    if (availableSlots <= 0) {
      return false;
    }
    for (final AeroBotInternal ab : plannedAeroBots()) {
      if ((ab.currentOperation() == null) //
          || ((ab.currentOperation().getMode() == OperationMode.Charge)
              && (ab.currentOperation().checkFinished() == FinStatus.Finished))) {
        continue;
      }
      if (chargingAeroBots.containsKey(ab.getCode())) {
        continue;
      }
      if (!waitingAeroBots.containsKey(ab.getCode())) {
        return false;
      }
      if (aeroBot == ab) {
        return true;
      }
      if (--availableSlots <= 0) {
        break;
      }
    }
    return false;
  }

  private Collection<AeroBotInternal> plannedAeroBots() {
    return plannedOps.values().stream().map(o -> o.aeroBot).collect(Collectors.toList());
  }

  // ----------------------------------------------------------------------------
  // ----------------------------------------------------------------------------
}
