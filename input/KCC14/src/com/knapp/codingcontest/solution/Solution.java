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

package com.knapp.codingcontest.solution;

import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

import com.knapp.codingcontest.data.Container;
import com.knapp.codingcontest.data.Institute;
import com.knapp.codingcontest.data.Order;
import com.knapp.codingcontest.data.Rack;
import com.knapp.codingcontest.data.Waypoint;
import com.knapp.codingcontest.operations.AeroBot;
import com.knapp.codingcontest.operations.AeroBotVisualizer;
import com.knapp.codingcontest.operations.ChargingArea;
import com.knapp.codingcontest.operations.InfoSnapshot;
import com.knapp.codingcontest.operations.ParkingArea;
import com.knapp.codingcontest.operations.PickArea;
import com.knapp.codingcontest.operations.Warehouse;

/**
 * This is the code YOU have to provide
 */
public class Solution {
  public String getParticipantName() {
    return "AutoResearch";
  }

  public Institute getParticipantInstitution() {
    return Institute.Sonstige;
  }

  // ----------------------------------------------------------------------------

  protected final Warehouse warehouse;

  // ----------------------------------------------------------------------------

  public Solution(final Warehouse warehouse) {
    // TODO: prepare data structures (may also be done in run() method below)
    this.warehouse = warehouse;
    if (getParticipantName() == null) {
      throw new IllegalArgumentException("let getParticipantName() return your name");
    }
    if (getParticipantInstitution() == null) {
      throw new IllegalArgumentException("let getParticipantInstitution() return yout institution");
    }
  }

  // ----------------------------------------------------------------------------

  /**
   * The main entry-point.
   *
   */
  public void run() throws Exception {
    // TODO: make calls to API (see below)
  }

  // ----------------------------------------------------------------------------
  // ----------------------------------------------------------------------------

  /**
   * Just for documentation purposes.
   *
   * this method may be removed without any side-effects
   *   divided into these sections
   *
   *     <li><em>input methods</em>
   *
   *     <li><em>main interaction methods</em>
   *         - these methods are the ones that make (explicit) changes to the warehouse
   *
   *     <li><em>information</em>
   *         - information you might need for your solution
   *
   *     <li><em>additional information</em>
   *         - various other infos: statistics, information about (current) costs, ...
   *
   */
  @SuppressWarnings({ "unused", "null" })
  private void apis() throws Exception {
    final AeroBot aeroBot = null;
    final Waypoint waypoint = null;
    final int level = 0;
    final Container container = null;
    final Order order = null;

    // ----- input -----

    final Collection<Container> ac = warehouse.getAllContainers();
    final List<Order> ao = warehouse.getAllOrders();
    final Collection<Rack> ar = warehouse.getAllRacks();

    // ----- main interaction methods -----

    aeroBot.planMoveToWaypoint(waypoint);
    aeroBot.planClimbToLevel(level);
    aeroBot.planLoadContainer(container);
    aeroBot.planPick(order);
    aeroBot.planStoreContainer();
    aeroBot.planStartCharge();

    // ----- information -----

    final ParkingArea parking = warehouse.getParkingArea();
    final ChargingArea charging = warehouse.getChargingArea();
    final PickArea picking = warehouse.getPickArea();
    final Collection<AeroBot> aeroBots = warehouse.getAllAeroBots();

    final Collection<Container> acs = warehouse.findAvailableContainers("productCode");
    final Collection<Rack.RackStorageLocation> ersls = warehouse.findEmptyRackStorageLocations();

    final List<Order> openOrders = warehouse.getOpenOrders();
    final boolean allFinished = warehouse.areAllOrdersFinished();

    // ----- additional information -----

    final Collection<AeroBot> pabs = parking.getParkingAeroBots();

    final int cs = charging.getChargingSlots();
    final int fcs = charging.getFreeChargingSlots();
    charging.hasFreeChargingSlot();

    final List<Order> porders = picking.getCurrentOrders();

    final InfoSnapshot info = warehouse.getInfoSnapshot();

    //
    final int uo = info.getUnfinishedOrderCount();
    final int t = info.getTicksRuntime();

    // Park, MoveH, MoveV, Load, Pick, Store, Charge, _Idle_
    final InfoSnapshot.AeroBotStatistics abstat = info.getAeroBotStatistics(aeroBot);
    final InfoSnapshot.AeroBotStatistics.ModeStatistics abms = abstat.getModeStatistics(AeroBot.OperationMode._Idle_);
    final InfoSnapshot.AeroBotStatistics.ModeStatistics ms = info.getModeStatistics(AeroBot.OperationMode._Idle_);
    ms.get_count();
    ms.get_tick_units();
    ms.get_ticks();

    //
    final double c_uo = info.getUnfinishedOrdersCost();
    final double c_t = info.getTicksCost();
    final double c_T = info.getTotalCost();

    // optional logging (SEVERE, WARNING, INFO, CONFIG, FINE, FINER, FINEST)
    // if used you may adjust some attributes below ...
    warehouse.log(Level.FINEST, "finest");

    final AeroBotVisualizer v = warehouse.getVisualizer();
    final String filename = null;
    v.generateHTML(filename);
    v.getConflictCount();
    v.getFirstConflictTick();
  }

  static {
    // TODO: you may change logging (target: file or console/stderr, level) - you may turn it 'OFF'
    //System.setProperty("initLogging", "solution.log"); // default: 'console' (stdout); use file instead of console
    //System.setProperty("logTarget", "stderr"); // default: stdout (only considered with initLogging=console)
    //System.setProperty("logLevel", "FINE"); // default: INFO (OFF, SEVERE, WARNING, INFO, CONFIG, FINE, FINER, FINEST, ALL)

    // System.setProperty("useVisualizer", "false"); // uncomment to disable data-collection for visualization
  }

  // ----------------------------------------------------------------------------
}
