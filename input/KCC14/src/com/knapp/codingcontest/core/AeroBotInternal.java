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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;

import com.knapp.codingcontest.core.OperationInternal.AeroBotStore;
import com.knapp.codingcontest.data.Container;
import com.knapp.codingcontest.data.Location;
import com.knapp.codingcontest.data.Order;
import com.knapp.codingcontest.data.Rack;
import com.knapp.codingcontest.data.Rack.RackStorageLocation;
import com.knapp.codingcontest.data.Waypoint;
import com.knapp.codingcontest.operations.AeroBot;
import com.knapp.codingcontest.operations.Operation;

@SuppressWarnings("boxing")
public class AeroBotInternal extends Location implements AeroBot {
  final InputDataInternal iinput;

  private final List<OperationInternal> plannedOperations = new ArrayList<>();
  private OperationInternal runningOperation;
  private OperationInternal lastFinishedOperation;
  private int lastFinishedOperationTick = -1;
  private final LinkedList<OperationInternal> history = new LinkedList<>();

  PlannedState plannedState = new PlannedState();
  // exec
  private OperationMode mode = OperationMode._Idle_;
  final int charge[] = { 0, 0 };

  private Waypoint currentWaypoint;
  private int currentLevel;
  private ContainerInternal currentContainer;

  final InfoSnapshotInternal.AeroBotStatisticsInternal stats = new InfoSnapshotInternal.AeroBotStatisticsInternal();

  // ----------------------------------------------------------------------------

  AeroBotInternal(final InputDataInternal iinput, final String code, final int maxCharge) {
    super(Location.Type.AeroBot, code, 0);
    this.iinput = iinput;
    charge[0] = maxCharge;
    charge[1] = maxCharge;
    plannedState.charge = charge[1];
  }

  // ----------------------------------------------------------------------------

  @Override
  public OperationMode getOperationMode() {
    return mode;
  }

  @Override
  public int getMaxCharge() {
    return charge[1];
  }

  @Override
  public int getCurrentCharge() {
    return charge[0];
  }

  @Override
  public ContainerInternal getCurrentContainer() {
    return currentContainer;
  }

  @Override
  public Waypoint getCurrentWaypoint() {
    return currentWaypoint;
  }

  @Override
  public int getCurrentLevel() {
    return currentLevel;
  }

  @Override
  public RackInternal.RackStorageLocationInternal getCurrentLocation() {
    return (currentLevel > 0 ? ((RackInternal) currentWaypoint).getRackStorageLocation(currentLevel) : null);
  }

  // ----------------------------------------------------------------------------

  @Override
  public List<Operation> getOpenOperations() {
    final List<Operation> openOperations = new ArrayList<>();
    if ((runningOperation != null) && (runningOperation.checkFinished() != OperationInternal.FinStatus.Finished)) {
      openOperations.add(runningOperation);
    }
    openOperations.addAll(plannedOperations);
    return Collections.unmodifiableList(openOperations);
  }

  int sumOpenTicks(final List<OperationInternal> operations) {
    return operations.stream().mapToInt(o -> openTicks(o)).sum();
  }

  int openTicks(final OperationInternal operation) {
    return operation.ticks_[1] - operation.ticks_[0];
  }

  // ----------------------------------------------------------------------------

  @Override
  public void planMoveToWaypoint(final Waypoint waypoint) {
    final OperationInternal.AeroBotMoveH op = new OperationInternal.AeroBotMoveH(this, iinput.iwarehouse.currentTick(),
        iinput.internal(waypoint));
    checkOp(op);
    addOp(op);
  }

  @Override
  public void planClimbToLevel(final int level) {
    final OperationInternal.AeroBotMoveV op = new OperationInternal.AeroBotMoveV(this, iinput.iwarehouse.currentTick(), level);
    checkOp(op);
    addOp(op);
  }

  @Override
  public void planLoadContainer(final Container container) {
    final OperationInternal.AeroBotLoad op = new OperationInternal.AeroBotLoad(this, iinput.iwarehouse.currentTick(),
        iinput.internal(container));
    checkOp(op);
    addOp(op);
  }

  @Override
  public void planPick(final Order order) {
    final OperationInternal.AeroBotPick op = new OperationInternal.AeroBotPick(this, iinput.iwarehouse.currentTick(),
        iinput.internal(order));
    checkOp(op);
    addOp(op);
  }

  @Override
  public void planStoreContainer() {
    final OperationInternal.AeroBotStore op = new OperationInternal.AeroBotStore(this, iinput.iwarehouse.currentTick());
    checkOp(op);
    addOp(op);
  }

  @Override
  public void planStartCharge() {
    final OperationInternal.AeroBotCharge op = new OperationInternal.AeroBotCharge(this, iinput.iwarehouse.currentTick());
    checkOp(op);
    addOp(op);
  }

  // ----------------------------------------------------------------------------
  // ----------------------------------------------------------------------------

  private void checkOp(final OperationInternal.AeroBotMoveH op) {
    check(op, plannedState.lvl() == 0, "must be at ground to move horizontally");
    check(op, plannedState.sufficientcharge(op), "charge would be depleted");
  }

  private void checkOp(final OperationInternal.AeroBotMoveV op) {
    check(op, plannedState.wp(Rack.class), "can only move vertcally at rack-locations");
    check(op, plannedState.sufficientcharge(op), "charge would be depleted");
  }

  private void checkOp(final OperationInternal.AeroBotLoad op) {
    check(op, plannedState.loc(Rack.RackStorageLocation.class), "only valid at storage-location");
    check(op, !plannedState.hascontbot(), "can only load if aeroBot empty");
    check(op, plannedState.hascontloc(op.container), "location must have container: " + op.container);
    iinput.iwarehouse.checkReservation(op, "location/container must not be reserved (for other bot): ");
    check(op, plannedState.sufficientcharge(op), "charge would be depleted");
  }

  private void checkOp(final OperationInternal.AeroBotPick op) {
    check(op, op.plannedProductCode.equals(op.order.getProductCode()),
        "product '" + op.plannedProductCode + "' must be for order: " + op.order);
    check(op, plannedState.wp(iinput.pickArea), "only valid at pick-area");
    check(op, plannedState.hascontbot(), "can only pick if aeroBot !empty");
    check(op, plannedState.sufficientcharge(op), "charge would be depleted");
    iinput.pickArea.checkReservation(op, "order must only be picked once");
  }

  private void checkOp(final OperationInternal.AeroBotStore op) {
    check(op, plannedState.loc(Rack.RackStorageLocation.class), "only valid at storage-location");
    check(op, plannedState.hascontbot(), "can only store if aeroBot !empty");
    check(op, plannedState.hasnocontloc() || checkReservation(op), "location must not have any container (l)");
    iinput.iwarehouse.checkReservation(op, "location must not be reserved (for other bot)");
    check(op, plannedState.sufficientcharge(op), "charge would be depleted");
  }

  private boolean checkReservation(final AeroBotStore op) {
    final AeroBotInternal.PlannedState s = op.aeroBot.plannedState;
    final OperationInternal rl = iinput.iwarehouse.reservedLocations.get(s.loc().getCode());
    if (rl == null) {
      return false;
    }
    if (!((rl.getMode() == OperationMode.Load) && (rl.aeroBot == op.aeroBot))) {
      return false;
    }
    return true;
  }

  private void checkOp(final OperationInternal.AeroBotCharge op) {
    check(op, plannedState.wp(iinput.chargingArea), "only valid at charging-area");
  }

  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

  private void check(final OperationInternal op, final boolean check, final String message) {
    if (!check) {
      throw new IllegalStateException("[CHECK-OP] " + op + " :: " + message);
    }
  }

  // ............................................................................

  private void addOp(final OperationInternal op) {
    iinput.iwarehouse.operations.add(op);
    plannedOperations.add(op);
    op.planned();
  }

  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

  private void addOp(final OperationInternal.AeroBotMoveH op) {
    plannedState.waypoint = op.to;
    addOp((OperationInternal) op);
  }

  private void addOp(final OperationInternal.AeroBotMoveV op) {
    plannedState.level = op.to;
    addOp((OperationInternal) op);
  }

  private void addOp(final OperationInternal.AeroBotLoad op) {
    plannedState.container = op.container;
    iinput.iwarehouse.setReservation(op);
    addOp((OperationInternal) op);
  }

  private void addOp(final OperationInternal.AeroBotPick op) {
    addOp((OperationInternal) op);
  }

  private void addOp(final OperationInternal.AeroBotStore op) {
    plannedState.container = null;
    iinput.iwarehouse.setReservation(op);
    addOp((OperationInternal) op);
  }

  private void addOp(final OperationInternal.AeroBotCharge op) {
    addOp((OperationInternal) op);
    plannedState.charge = op.aeroBot.getMaxCharge();
  }

  // ----------------------------------------------------------------------------
  // ----------------------------------------------------------------------------

  void setOperationMode(final OperationMode mode) {
    this.mode = mode;
  }

  void decCurrentCharge(final int delta) {
    charge[0] -= delta;
    if (charge[0] < 0) {
      throw new IllegalStateException("battery empty");
    }
  }

  void incCurrentCharge(final int delta) {
    charge[0] += delta;
    if (charge[0] > charge[1]) {
      charge[0] = charge[1];
    }
    plannedState.charge = charge[0];
  }

  void setContainer(final ContainerInternal container) {
    if (container == null) {
      iinput.iwarehouse.log(Level.FINE, "* [[%s]] clear container", getCode());
    } else {
      container.setCurrentLocation(this);
      iinput.iwarehouse.log(Level.FINE, "* [[%s]] container = %s", getCode(), container);
    }
    currentContainer = container;
  }

  public void setCurrentWaypoint(final Waypoint waypoint) {
    if (waypoint == null) {
      iinput.iwarehouse.log(Level.FINE, "* [[%s]] detach WP", getCode());
    } else {
      iinput.iwarehouse.log(Level.FINE, "* [[%s]] current WP: %s", getCode(), waypoint);
    }
    currentWaypoint = waypoint;
  }

  private void setCurrentLevel(final int level) {
    if (level == 0) {
      iinput.iwarehouse.log(Level.FINE, "* [[%s]] at base of rack: %s", getCode(), getCurrentWaypoint());
    } else {
      iinput.iwarehouse.log(Level.FINE, "* [[%s]] rack location@[%d]: %s", getCode(), level,
          ((Rack) currentWaypoint).getRackStorageLocation(level));
    }
    currentLevel = level;
  }

  // ----------------------------------------------------------------------------

  private void doPark() {
    iinput.parkingArea.enter(this);
    final InfoSnapshotInternal.AeroBotStatisticsInternal.ModeStatisticsInternal ms = stats
        .getModeStatistics(AeroBot.OperationMode.Park);
    ms.count++;
    ms.tick_units += 0;
    ms.ticks += 0;
  }

  // ----------------------------------------------------------------------------

  void doStart(final OperationInternal.AeroBotMoveH op) {
    op.from.leave(this);
  }

  void doFinish(final OperationInternal.AeroBotMoveH op) {
    if (op.to.getType() != Waypoint.Type.Parking) {
      op.to.enter(this);
    } else {
      doPark();
    }
  }

  @SuppressWarnings("unused")
  void doStart(final OperationInternal.AeroBotMoveV op) {
  }

  void doFinish(final OperationInternal.AeroBotMoveV op) {
    setCurrentLevel(op.to);
    ((RackInternal) op.aeroBot.currentWaypoint).plannedRemove(op);
  }

  void doStart(final OperationInternal.AeroBotLoad op) {
    if (!op.container.getCode().equals((getCurrentLocation().peekContainer()).getCode())) {
      throw new IllegalArgumentException("container mismatch @load");
    }
  }

  void doFinish(final OperationInternal.AeroBotLoad op) {
    setContainer(getCurrentLocation().pullContainer());
    final String l = getCurrentLocation().getCode();
    final String c = getCurrentContainer().getCode();
    iinput.iwarehouse.clearReservation(op);
    log(Level.FINE, "[LOAD] %s: %s <== %s", c, op.aeroBot, l);
  }

  @SuppressWarnings("unused")
  void doStart(final OperationInternal.AeroBotStore op) {
  }

  void doFinish(final OperationInternal.AeroBotStore op) {
    getCurrentLocation().pushContainer(getCurrentContainer());
    final String l = getCurrentLocation().getCode();
    final String c = getCurrentContainer().getCode();
    iinput.iwarehouse.clearReservation(op);
    setContainer(null);
    log(Level.FINE, "[STORE] %s: %s ==> %s", c, op.aeroBot, l);
  }

  @SuppressWarnings("unused")
  void doStart(final OperationInternal.AeroBotCharge op) {
  }

  void doFinish(final OperationInternal.AeroBotCharge op) {
    iinput.chargingArea.doneCharging(op.aeroBot);
  }

  @SuppressWarnings("unused")
  void doStart(final OperationInternal.AeroBotPick op) {
  }

  void doFinish(final OperationInternal.AeroBotPick op) {
    iinput.pickArea.doPick(op.order, op.aeroBot);

  }

  // ----------------------------------------------------------------------------
  // ----------------------------------------------------------------------------

  static class PlannedState {
    AbstractWaypoint waypoint;
    int level;
    ContainerInternal container;
    int charge;

    int lvl() {
      return level;
    }

    boolean hascontbot() {
      return container != null;
    }

    boolean hascontloc(final ContainerInternal container) {
      final RackStorageLocation loc = loc();
      if (loc.peekContainer() == null) {
        return false;
      }
      if (loc.peekContainer() != container) {
        return false;
      }
      return true;
    }

    boolean hasnocontloc() {
      final RackStorageLocation loc = loc();
      return (loc.peekContainer() == null);
    }

    RackStorageLocation loc() {
      return (level > 0) ? ((Rack) waypoint).getRackStorageLocation(level) : null;
    }

    public boolean wp(final Class<? extends Waypoint> type) {
      return type.isInstance(waypoint);
    }

    public boolean loc(final Class<? extends Location> type) {
      return type.isInstance(loc());
    }

    boolean wp(final Waypoint waypoint) {
      return this.waypoint == waypoint;
    }

    boolean sufficientcharge(final OperationInternal op) {
      return op.aeroBot.plannedState.charge > op.aeroBot.plan_charge_(op);
    }
  }

  private int plan_charge_(final OperationInternal op) {
    return op.ticks_[1] * InputDataInternal.CHARGE_PER_OP.get(op.getMode());
  }

  void idleTick(final int currentTick) {
    final InfoSnapshotInternal.AeroBotStatisticsInternal.ModeStatisticsInternal ms = stats
        .getModeStatistics(AeroBot.OperationMode._Idle_);
    ms.count++;
    ms.tick_units += 1;
    ms.ticks += 1;

    if (iinput.iwarehouse.aeroBotHistorySize != 0) {
      OperationInternal op = history.peekLast();
      if (!(op instanceof OperationInternal._Idle_)) {
        op = new OperationInternal._Idle_(iinput.iwarehouse.taskIds.getAndIncrement(), currentTick);
        history.addLast(op);
      }
      ((OperationInternal._Idle_) op).count++;
      log(Level.FINER, String.format(". @%6d :: %-10s: (IDLE #%d) %-10s", //
          currentTick, getCode(), ((OperationInternal._Idle_) op).count, currentOperation()));
    }
  }

  // ----------------------------------------------------------------------------

  static enum OpResult {
    IgnoreParked, //
    WaitCurrentOp, //
    TickedCurrentOp, //
    SkipAdvanceNextOp, //
    AdvancedToNextOp, //
    FinishedLastOp, //
    ;
  }

  OpResult doTick(final int currentTick) {
    if (mode == OperationMode.Park) {
      if ((runningOperation == null) && plannedOperations.isEmpty()) {
        return OpResult.IgnoreParked;
      }
    }
    OperationInternal.FinStatus finStatus = null;
    if (runningOperation != null) {
      finStatus = runningOperation.incTick();
      switch (finStatus) {
        case Waiting:
          decCurrentCharge(InputDataInternal.CHARGE_PER_OP.get(OperationMode._Idle_));
          runningOperation.tickOp(currentTick);
          log(Level.FINE, "- @%6d :: %s", currentTick, runningOperation);
          return OpResult.WaitCurrentOp;

        case Working:
          decCurrentCharge(InputDataInternal.CHARGE_PER_OP.get(runningOperation.getMode()));
          runningOperation.tickOp(currentTick);
          log(Level.FINER, "+ @%6d :: %s", currentTick, runningOperation);
          return OpResult.TickedCurrentOp;

        case Finished:
          runningOperation.tickFinished = currentTick;
          decCurrentCharge(InputDataInternal.CHARGE_PER_OP.get(runningOperation.getMode()));
          runningOperation.tickOp(currentTick); // ugly workaround to call once more :-/
          setOperationMode(OperationMode._Idle_);
          runningOperation.finishOp(currentTick);
          runningOperation.endCharge = charge[0];
          if (iinput.iwarehouse.aeroBotHistorySize != 0) {
            history.addLast(runningOperation);
            if (iinput.iwarehouse.aeroBotHistorySize > 0) {
              int d = history.size() - iinput.iwarehouse.aeroBotHistorySize;
              while (--d >= 0) {
                history.removeFirst();
              }
            }
          }
          log(Level.CONFIG, "> @%6d :: %s", currentTick, runningOperation);
          lastFinishedOperation = runningOperation;
          lastFinishedOperationTick = currentTick;
          runningOperation = null;
          break;
      }
    }
    if (plannedOperations.isEmpty()) {
      return OpResult.FinishedLastOp;
    }

    final OperationInternal op0 = plannedOperations.get(0);
    if (readyToRun(op0)) {
      runningOperation = op0;
      runningOperation.ticks_[0] = 0;
      plannedOperations.remove(0);

      runningOperation.tickStart = currentTick;
      runningOperation.startCharge = charge[0];
      setOperationMode(runningOperation.getMode());
      final InfoSnapshotInternal.AeroBotStatisticsInternal.ModeStatisticsInternal ms = stats
          .getModeStatistics(runningOperation.getMode());
      ms.count++;
      ms.tick_units += runningOperation.tick_units;
      ms.ticks += runningOperation.ticks_[1];

      log(Level.INFO, "< @%6d :: %s", currentTick, runningOperation);
      runningOperation.startOp(currentTick);

      if (finStatus != OperationInternal.FinStatus.Finished) {
        final OpResult r = doTick(currentTick);
        if (r != OpResult.TickedCurrentOp) {
          return r;
        }
      }
      return OpResult.AdvancedToNextOp;
    }

    return OpResult.SkipAdvanceNextOp;
  }

  private boolean readyToRun(final OperationInternal op0) {
    switch (op0.getMode()) {
      case Park:
        break;

      case MoveH:
        break;

      case MoveV: {
        final RackInternal r0 = (RackInternal) op0.aeroBot.getCurrentWaypoint();
        for (final AeroBotInternal ab : iinput.aeroBots.values()) {
          if ((ab != op0.aeroBot) //
              && (ab.getCurrentWaypoint() == op0.aeroBot.getCurrentWaypoint()) //
              && ((ab.getCurrentLevel() > 0)
                  || ((ab.runningOperation != null) && (ab.runningOperation.getMode() == OperationMode.MoveV)))) //
          {
            return false;
          }
          if (op0.aeroBot.currentLevel == 0) {
            if (!r0.inSequenceToMoveV(op0.aeroBot)) {
              return false;
            }
          }
        }
        break;
      }

      case Load:
        break;

      case Store:
        break;

      case Charge: {
        if (!iinput.chargingArea.hasFreeChargingSlot()) {
          return false;
        }
        if (!iinput.chargingArea.inSequenceToCharge(op0.aeroBot)) {
          return false;
        }
        break;
      }

      case Pick:
        if (!iinput.pickArea.checkOrder(((OperationInternal.AeroBotPick) op0).order)) {
          return false;
        }
        break;

      case _Idle_:
        break;
    }

    return true;
  }

  // ----------------------------------------------------------------------------

  OperationInternal currentOperation() {
    if (runningOperation != null) {
      return runningOperation;
    }
    if (!plannedOperations.isEmpty()) {
      return plannedOperations.get(0);
    }
    return null;
  }

  OperationInternal currentOrJustFinishedOperation(final int tick) {
    final OperationInternal operation = currentOperation();
    if (operation != null) {
      return operation;
    }
    if (lastFinishedOperationTick == tick) {
      return lastFinishedOperation;
    }
    return null;
  }

  // ----------------------------------------------------------------------------

  private void log(final Level level, final String message, final Object... params) {
    iinput.iwarehouse.log(level, message, params);
  }
}
