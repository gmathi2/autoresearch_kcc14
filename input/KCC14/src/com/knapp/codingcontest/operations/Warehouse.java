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
import java.util.List;
import java.util.logging.Level;

import com.knapp.codingcontest.data.Container;
import com.knapp.codingcontest.data.Location;
import com.knapp.codingcontest.data.Order;
import com.knapp.codingcontest.data.Rack;
import com.knapp.codingcontest.data.Waypoint;

/**
 * Central class for accessing warehouse data, executing simulation ticks,
 * and computing cost estimates.
 *
 * <p>The warehouse provides:
 * <ul>
 *   <li><b>Data access</b> &mdash; query containers, orders, racks, and special areas</li>
 *   <li><b>Reservation queries</b> &mdash; check whether locations or containers are reserved by a planned operation</li>
 *   <li><b>Cost calculation</b> &mdash; estimate tick/charge costs of hypothetical operations</li>
 *   <li><b>Tick execution</b> &mdash; advance the simulation by one or more ticks - <strong>IMPORTANT</strong></li>
 *   <li><b>Logging</b> &mdash; write messages to the contest log</li>
 * </ul>
 *
 * <p>All collections returned by this interface are <strong>read-only snapshots</strong> or
 * unmodifiable views &mdash; modifying them has no effect on warehouse state and any attempt
 * to do so will invalidate the Solution. To modify the warehouse state, plan operations for
 * the AeroBots and execute ticks.
 *
 * @see AeroBot
 */
public interface Warehouse {
  /**
   * @return all containers in the warehouse (both stored in rack locations and loaded onto AeroBots)
   */
  Collection<Container> getAllContainers();

  /**
   * @return all orders, in ascending sequence of their appearance in the pick area
   * (i.e. the first order in the list is the first one that becomes available for picking).
   *
   * @see PickArea#getCurrentOrders()
   */
  List<Order> getAllOrders();

  /**
   * @return all open (not yet picked) orders in the warehouse
   */
  List<Order> getOpenOrders();

  /**
   * @return {@code true} if all orders have been picked and the Solution run is finished
   */
  boolean areAllOrdersFinished();

  /**
   * @return all racks in the warehouse with their locations and containers
   */
  Collection<Rack> getAllRacks();

  // ----------------------------------------------------------------------------

  /**
   * @return the single parking area waypoint
   */
  ParkingArea getParkingArea();

  /**
   * @return the single charging area waypoint
   */
  ChargingArea getChargingArea();

  /**
   * @return the single picking area waypoint
   */
  PickArea getPickArea();

  /**
   * @return all AeroBots operating in this warehouse
   */
  Collection<AeroBot> getAllAeroBots();

  /**
   * Finds containers that are currently stored in a rack (not loaded on any bot)
   * and are not reserved by any planned operation, filtered by product code.
   *
   * @param productCode the product code to search for
   * @return available containers matching the given product code
   */
  Collection<Container> findAvailableContainers(String productCode);

  /**
   * Finds rack storage locations that are currently empty and not part of
   * any existing planned operation.
   *
   * @return empty, unplanned rack storage locations
   */
  Collection<Rack.RackStorageLocation> findEmptyRackStorageLocations();

  /**
   * Checks whether the given location is reserved by a planned operation.
   *
   * @param location the location to check
   * @return {@code true} if the location is reserved
   */
  boolean isReserved(Location location);

  /**
   * Checks whether the given container is reserved by a planned operation.
   *
   * @param container the container to check
   * @return {@code true} if the container is reserved
   */
  boolean isReserved(Container container);

  /**
   * @return a snapshot of various internal detail information: costs so far, unfinished count, costs
   * @apiNote This is typically not needed for use in the solution, but can be
   *          useful for debugging and optimization purposes.
   */
  InfoSnapshot getInfoSnapshot();

  /**
   * @return the cost factors used for ranking the Solution
   */
  CostFactors getCostFactors();

  // ----------------------------------------------------------------------------

  /**
   * Advances the simulation by exactly one tick.
   *
   * <p>All AeroBots execute one step of their current operation (if any).
   *
   * <p><strong>IMPORTANT:</strong> To advance the simulation, you MUST call one of the tick execution methods
   * (either this one or {@link #executeTicksUntilFirstBotToFinish()}). Modifying warehouse state by planning operations
   * does not automatically advance time, so the planned operations will not take effect until you execute ticks.
   * This is a common mistake that can lead to unexpected behavior and incorrect results.
   * Always remember to execute ticks after planning operations.
   */
  void executeOneTick();

  /**
   * Advances the simulation until the first AeroBot completes all its planned operations.
   *
   * <p>Useful for batch execution: plan operations for all bots, then call this
   * to let them execute concurrently until the fastest one finishes.
   *
   * <p>Note that the other bots may still have remaining planned operations after this method returns.
   *
   * <p>Calling this method without any planned operations will simply execute one tick and do nothing.
  *
  * <p><strong>IMPORTANT:</strong> To advance the simulation, you MUST call one of the tick execution methods
  * (either this one or {@link #executeOneTick()}). Modifying warehouse state by planning operations
  * does not automatically advance time, so the planned operations will not take effect until you execute ticks.
  * This is a common mistake that can lead to unexpected behavior and incorrect results.
  * Always remember to execute ticks after planning operations.
   */
  void executeTicksUntilFirstBotToFinish();

  // ----------------------------------------------------------------------------

  /**
   * Holds the estimated cost of a hypothetical operation in ticks and charge units.
   */
  public static class CalculateResult {
    /** The number of ticks the operation would take. */
    public final int ticks;
    /** The charge units the operation would consume. */
    public final int charge;

    public CalculateResult(final int ticks, final int charge) {
      this.ticks = ticks;
      this.charge = charge;
    }
  }

  /**
   * Calculates the cost of moving horizontally from one waypoint to another.
   *
   * @param from the starting waypoint
   * @param to the target waypoint
   * @return the estimated ticks and charge
   * @see AeroBot#planMoveToWaypoint(Waypoint)
   */
  CalculateResult calculateMoveToWaypoint(Waypoint from, Waypoint to);

  /**
   * Calculates the cost of climbing from one level to another at a rack.
   *
   * @param from the starting level
   * @param to the target level
   * @return the estimated ticks and charge
   * @see AeroBot#planClimbToLevel(int)
   */
  CalculateResult calculateClimbToLevel(int from, int to);

  /**
   * Calculates the cost of loading a container.
   *
   * @param container the container to load
   * @return the estimated ticks and charge
   * @see AeroBot#planLoadContainer(Container)
   */
  CalculateResult calculateLoadContainer(Container container);

  /**
   * Calculates the cost of picking an order.
   *
   * @param order the order to pick
   * @return the estimated ticks and charge
   * @see AeroBot#planPick(Order)
   */
  CalculateResult calculatePick(Order order);

  /**
   * Calculates the cost of storing a container.
   *
   * @return the estimated ticks and charge
   * @see AeroBot#planStoreContainer()
   */
  CalculateResult calculateStoreContainer();

  /**
   * Calculates the cost of starting a charging operation.
   *
   * @param aeroBot the AeroBot to charge
   * @return the estimated ticks and charge
   * @see AeroBot#planStartCharge()
   */
  CalculateResult calculateStartCharge(AeroBot aeroBot);

  /**
   * Calculates the total cost (ticks and charge) of a sequence of operations.
   *
   * @param operations the operations to evaluate
   * @return the aggregated ticks and charge
   */
  CalculateResult calculateCosts(List<Operation> operations);

  // ----------------------------------------------------------------------------

  /**
   * Visualizes AeroBot movements and detects conflicts where multiple bots
   * are at the same rack at level > 0 simultaneously.
   *
   * This tool helps students debug their Solution.java implementations by
   * showing exactly when and where coordination issues occur.
   *
   * @return the visualizer
   */
  AeroBotVisualizer getVisualizer();

  /**
   * Logs a formatted message at the given level.
   *
   * <p>If {@code params} are provided, the message is treated as a
   * {@link String#format(String, Object...)} format string.
   *
   * @param level the logging level
   * @param message the message or format string to log
   * @param params optional format arguments
   */
  void log(Level level, String message, Object... params);

  /**
   * Logs a formatted message with an associated throwable.
   *
   * <p>If {@code params} are provided, the message is treated as a
   * {@link String#format(String, Object...)} format string.
   *
   * @param level the logging level
   * @param message the message or format string to log
   * @param throwable the associated exception
   * @param params optional format arguments
   */
  void log(Level level, String message, Throwable throwable, Object... params);
}
