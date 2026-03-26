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

import java.util.List;

import com.knapp.codingcontest.data.Container;
import com.knapp.codingcontest.data.Order;
import com.knapp.codingcontest.data.Rack;
import com.knapp.codingcontest.data.Waypoint;

/**
 * Represents a single AeroBot vehicle operating in the warehouse.
 *
 * <p>AeroBots move horizontally between {@link Waypoint}s, climb vertically at {@link Rack}s,
 * load/store {@link Container}s, and pick {@link Order}s at the picking station.
 *
 * <h3>Read-only query methods</h3>
 * <p>Methods like {@link #getCode()}, {@link #getCurrentCharge()}, etc. allow inspecting
 * the current state of the bot without side effects.
 *
 * <h3>Planning methods ({@code plan...})</h3>
 * <p>Methods prefixed with {@code plan} enqueue operations to be executed during
 * subsequent {@link Warehouse#executeOneTick()} or
 * {@link Warehouse#executeTicksUntilFirstBotToFinish()} calls.
 * Operations are executed in FIFO order.
 *
 * @see Warehouse#getAllAeroBots()
 * @see Operation
 */
public interface AeroBot {
  /**
   * The current operational mode of an AeroBot.
   */
  enum OperationMode {
    /** Parked at the parking area. */
    Park,
    /** Moving horizontally between waypoints. */
    MoveH,
    /** Climbing vertically at a rack. */
    MoveV,
    /** Loading a container from a rack storage location. */
    Load,
    /** Picking an order at the picking station. */
    Pick,
    /** Storing a container into a rack storage location. */
    Store,
    /** Charging at the charging station. */
    Charge,
    /** Not performing any operation or waiting for the next operation to be allowed to start. */
    _Idle_;
  }

  // ---- query methods -------------------------------------------------------

  /**
   * @return the unique identifier of this AeroBot
   */
  String getCode();

  /**
   * @return the current operational mode of this AeroBot
   */
  OperationMode getOperationMode();

  /**
   * @return the current battery charge level (decreases with each tick of work)
   */
  int getCurrentCharge();

  /**
   * @return the maximum battery charge capacity of this AeroBot
   */
  int getMaxCharge();

  /**
   * Returns the container currently loaded on this AeroBot.
   *
   * @return the loaded {@link Container}, or {@code null} if none is loaded
   */
  Container getCurrentContainer();

  /**
   * Returns the waypoint this AeroBot is currently located at.
   *
   * @return the current {@link Waypoint}
   */
  Waypoint getCurrentWaypoint();

  /**
   * @return the current vertical level of this AeroBot (relevant at rack waypoints)
   */
  int getCurrentLevel();

  /**
   * Returns the rack storage location this AeroBot is currently at.
   *
   * @return the current {@link Rack.RackStorageLocation}, or {@code null} if not at a rack
   */
  Rack.RackStorageLocation getCurrentLocation();

  // ---- planning methods ----------------------------------------------------

  /**
   * Plans a horizontal move to the specified waypoint.
   *
   * <p>The bot will move along the grid from its current waypoint to the target.
   * Each grid step consumes time (in ticks) and units of charge.
   *
   * @param waypoint the target waypoint to move to
   * @see Warehouse#calculateMoveToWaypoint(Waypoint, Waypoint)
   */
  void planMoveToWaypoint(Waypoint waypoint);

  /**
   * Plans a vertical climb (up OR down) to the specified rack level or floor.
   *
   * <p>The bot must be at a {@link Rack} waypoint. Each level climbed
   * consumes one tick and one unit of charge. Only one bot can climb a rack at a time,
   * so the bot may have to wait for other bots to finish climbing before it can start.
   *
   * @param level the target level (0 for floor, 1 to max for rack levels)
   * @see Warehouse#calculateClimbToLevel(int, int)
   */
  void planClimbToLevel(int level);

  /**
   * Plans loading a container from the current rack storage location.
   *
   * <p>The bot must be at a rack, at the correct level, and not already carrying
   * a container. The target storage location must contain the specified container.
   *
   * @param container the container to load (must match the container at the current location)
   * @see Warehouse#calculateLoadContainer(Container)
   */
  void planLoadContainer(Container container);

  /**
   * Plans picking an order at the picking station.
   *
   * <p>The bot must be at the {@link com.knapp.codingcontest.operations.PickArea}
   * and carry a container whose product matches the order's product code.
   * <p>Note that only orders that are currently at the picking station can be picked
   * (see {@link PickArea#getCurrentOrders()}).
   * <p>So the bot may have to wait for other orders to finish picking before it can start.
   *
   * @param order the order to fulfil
   * @see Warehouse#planPick(Order)
   */
  void planPick(Order order);

  /**
   * Plans storing the currently loaded container into the current rack storage location.
   *
   * <p>The bot must be at a rack, at a level with an empty storage location,
   * and must be carrying a container.
   *
   * @see Warehouse#calculateStoreContainer()
   */
  void planStoreContainer();

  /**
   * Plans a charging operation at the charging station.
   *
   * <p>The bot must be at the {@link ChargingArea}. Charging restores the bot's
   * charge to {@link #getMaxCharge()} and cannot be interrupted. There are limited
   * charging slots, so the bot may have to wait for other bots to finish charging
   * before it can start.
   *
   * @see Warehouse#calculateStartCharge()
   */
  void planStartCharge();

  // --------------------------------------------------------------------------

  /**
   * Returns the list of operations that have been planned but not yet fully executed.
   * <p>Operations are returned in the order they will be executed (FIFO).
   * <p>The first operation in the list is the one currently being executed (if any),
   * and the rest are planned operations.
   *
   * @return an unmodifiable list of pending {@link Operation}s
   */
  List<Operation> getOpenOperations();
}
