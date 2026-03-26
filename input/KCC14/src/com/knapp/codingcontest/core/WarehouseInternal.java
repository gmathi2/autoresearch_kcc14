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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.knapp.codingcontest.core.InputDataInternal.MyOrder;
import com.knapp.codingcontest.data.Container;
import com.knapp.codingcontest.data.Location;
import com.knapp.codingcontest.data.Order;
import com.knapp.codingcontest.data.Rack;
import com.knapp.codingcontest.data.Waypoint;
import com.knapp.codingcontest.operations.AeroBot;
import com.knapp.codingcontest.operations.AeroBot.OperationMode;
import com.knapp.codingcontest.operations.CostFactors;
import com.knapp.codingcontest.operations.Operation;
import com.knapp.codingcontest.operations.Warehouse;

public class WarehouseInternal implements Warehouse {
  // some runtime properties
  public boolean useSanityChecks = true; // "Emergency-Break" if too long no orders are being processed
  public int thresholdTicks = 1000; // used for above checks
  // some settings that may be useful for debugging ...
  public int aeroBotHistorySize = 0; // record operations history per AeroBot up to size (or -1 for no limit)
  // some default, may be overridden by using System.setProperty
  //boolean useVisualizer = "true";
  //String initLogging  = "console";
  //String logTarget = "stdout";
  //Level logLevel = "INFO";
  // e.g. System.setProperty("useVisualizer", "false");
  // e.g. System.setProperty("logLevel", "ALL");

  //

  final InputDataInternal iinput;

  private final AtomicInteger ticks = new AtomicInteger(0);

  private LinkedList<MyOrder> openOrders;
  final List<OperationInternal> operations = new ArrayList<>(); // result: all in creation order

  final Map<String, OperationInternal> reservedLocations = new TreeMap<>();
  private final Map<String, OperationInternal> reservedContainers = new TreeMap<>();

  private AeroBotVisualizer visualizer;

  final AtomicInteger taskIds = new AtomicInteger(1);

  // ----------------------------------------------------------------------------
  // ----------------------------------------------------------------------------

  public WarehouseInternal(final InputDataInternal iinput) {
    this.iinput = iinput;
    iinput.iwarehouse = this;
    visualizer = new AeroBotVisualizer.NoOpVisualizer();
    log = Logger.getLogger("kcc");
    log.setUseParentHandlers(false);
    log.setLevel(Level.OFF);
    final Logger r = Logger.getLogger("");
    r.setLevel(Level.OFF);
  }

  // ----------------------------------------------------------------------------

  void prepareAfterRead() {
    for (final AeroBotInternal aeroBot : iinput.aeroBots.values()) {
      iinput.parkingArea.enter(aeroBot);
      aeroBot.plannedState.waypoint = iinput.parkingArea;
      aeroBot.plannedState.level = 0;
    }

    openOrders = new LinkedList<>(iinput.orders.values());
    iinput.pickArea.assignFreeSlots(openOrders);
  }

  public Iterable<Operation> result() {
    return operations.stream().collect(Collectors.toList());
  }

  @Override
  public com.knapp.codingcontest.operations.AeroBotVisualizer getVisualizer() {
    return visualizer;
  }

  // ----------------------------------------------------------------------------

  @Override
  public ParkingAreaInternal getParkingArea() {
    return iinput.parkingArea;
  }

  @Override
  public ChargingAreaInternal getChargingArea() {
    return iinput.chargingArea;
  }

  @Override
  public PickAreaInternal getPickArea() {
    return iinput.pickArea;
  }

  @Override
  public Collection<AeroBot> getAllAeroBots() {
    return Collections.unmodifiableCollection(iinput.aeroBots.values());
  }

  public RackInternal[][] getRacks() {
    return iinput.racks_;
  }

  // ----------------------------------------------------------------------------

  @Override
  public Collection<Container> getAllContainers() {
    return iinput.getAllContainers();
  }

  @Override
  public List<Order> getAllOrders() {
    return iinput.getAllOrders();
  }

  @Override
  public List<Order> getOpenOrders() {
    return openOrders.stream().collect(Collectors.toList());
  }

  @Override
  public boolean areAllOrdersFinished() {
    return openOrders.isEmpty() && iinput.pickArea.getCurrentOrders().isEmpty();
  }

  @Override
  public Collection<Rack> getAllRacks() {
    return iinput.getAllRacks();
  }

  // ----------------------------------------------------------------------------

  @Override
  public Collection<Container> findAvailableContainers(final String productCode) {
    final List<ContainerInternal> byProduct = iinput.containers.getOrDefault(productCode, Collections.emptyList());
    return Collections.unmodifiableCollection(byProduct.stream().filter(c -> !isReserved(c)).collect(Collectors.toList()));
  }

  @Override
  public List<Rack.RackStorageLocation> findEmptyRackStorageLocations() {
    return Arrays.stream(iinput.racks_)
        .flatMap((final RackInternal[] r) -> Arrays.stream(r))
        .flatMap(r -> r.getRackStorageLocations().stream())
        .filter(r -> r.peekContainer() == null)
        .filter(r -> !isReserved(r))
        .collect(Collectors.toList());
  }

  @Override
  public boolean isReserved(final Location location) {
    return reservedLocations.containsKey(location.getCode());
  }

  @Override
  public boolean isReserved(final Container container) {
    return reservedContainers.containsKey(container.getCode());
  }

  @Override
  public InfoSnapshotInternal getInfoSnapshot() {
    return new InfoSnapshotInternal(this, currentTick());
  }

  @Override
  public CostFactors getCostFactors() {
    return iinput.getCostFactors();
  }

  public int currentTick() {
    return ticks.get();
  }

  // ----------------------------------------------------------------------------
  // ----------------------------------------------------------------------------

  @Override
  public void executeTicksUntilFirstBotToFinish() {
    int count = 0;
    final OperationInternal.ExecuteTicks opTicks = new OperationInternal.ExecuteTicks(taskIds.getAndIncrement(), currentTick());
    for (;;) {
      final AeroBotInternal.OpResult resultAll = tick();
      count++;
      if (resultAll == AeroBotInternal.OpResult.FinishedLastOp) {
        break;
      }
      if (resultAll == AeroBotInternal.OpResult.IgnoreParked) {
        break;
      }
    }
    opTicks.count = count;
    operations.add(opTicks);
  }

  @Override
  public void executeOneTick() {
    operations.add(new OperationInternal.ExecuteTicks(taskIds.getAndIncrement(), iinput.iwarehouse.currentTick()));
    tick();
  }

  private AeroBotInternal.OpResult tick() {
    AeroBotInternal.OpResult resultAll = AeroBotInternal.OpResult.IgnoreParked;
    final int currentTick = ticks.incrementAndGet();

    log(Level.FINEST, "* -----------------------------------------------------------------------------");
    log(Level.FINEST, "* [[TICK]] @%d", currentTick);

    final Map<AeroBotInternal.OpResult, int[]> mop = Arrays.stream(AeroBotInternal.OpResult.values())
        .collect(Collectors.toMap(o -> o, o -> new int[] { 0 }));

    final Set<String> ibots = new TreeSet<>();
    iinput.aeroBots.values().stream().filter(b -> b.currentOperation() == null).forEach((b) -> {
      if (b.getOperationMode() != OperationMode.Park) {
        b.idleTick(currentTick);
        b.decCurrentCharge(InputDataInternal.CHARGE_PER_OP.get(OperationMode._Idle_));
        ibots.add(b.getCode());
      }
    });
    if (!ibots.isEmpty()) {
      log(Level.FINER, "* Idle aeroBots (no operations) not at 'Parking': " + ibots);
    }

    final Collection<AeroBotInternal> aeroBotsWithOperations = orderedByOpId(iinput.aeroBots.values());
    if (aeroBotsWithOperations.isEmpty()) {
      log(Level.FINER, "* No operations for any aeroBot planned (or active)?!?");
      return AeroBotInternal.OpResult.FinishedLastOp;
    }

    for (final AeroBotInternal aeroBot : aeroBotsWithOperations) {
      final AeroBotInternal.OpResult result = aeroBot.doTick(currentTick);
      if (resultAll.ordinal() < result.ordinal()) {
        resultAll = result;
      }
      mop.get(result)[0]++;
      switch (result) {
        case IgnoreParked:
        case WaitCurrentOp:
        case TickedCurrentOp:
          break;

        case SkipAdvanceNextOp: {
          aeroBot.idleTick(currentTick());
          break;
        }

        case AdvancedToNextOp:
        case FinishedLastOp:
          break;
      }
    }

    //
    final int orderCountF = iinput.pickArea.freeFinishedSlots();
    final int orderCountA = iinput.pickArea.assignFreeSlots(openOrders);

    // Record bot states for visualization
    visualizer.recordTick(currentTick, iinput.aeroBots);

    if (useSanityChecks) {
      if ((orderCountF + orderCountA) == 0) {
        if ((currentTick - lastOrderFinished) > thresholdTicks) {
          throw new IllegalStateException(
              "seems like nothing happens / no orders have been picked for " + thresholdTicks + " ticks");
        }
      } else {
        lastOrderFinished = currentTick;
      }
    }
    return resultAll;
  }

  private int lastOrderFinished = 0;

  // ----------------------------------------------------------------------------

  @Override
  @SuppressWarnings("boxing")
  public CalculateResult calculateMoveToWaypoint(final Waypoint from, final Waypoint to) {
    final int tick_units = from.distance(to);
    final int ticks_1 = (tick_units * InputDataInternal.TICKS_PER_OP.get(OperationMode.MoveH));
    final int charge = ticks_1 * InputDataInternal.CHARGE_PER_OP.get(OperationMode.MoveH);
    return new CalculateResult(ticks_1, charge);
  }

  @Override
  @SuppressWarnings("boxing")
  public CalculateResult calculateClimbToLevel(final int from, final int to) {
    final int tick_units = Math.abs(from - to);
    final int ticks_1 = (tick_units * InputDataInternal.TICKS_PER_OP.get(OperationMode.MoveV));
    final int charge = ticks_1 * InputDataInternal.CHARGE_PER_OP.get(OperationMode.MoveV);
    return new CalculateResult(ticks_1, charge);
  }

  @Override
  @SuppressWarnings("boxing")
  public CalculateResult calculateLoadContainer(final Container container) {
    final int tick_units = 1;
    final int ticks_1 = (tick_units * InputDataInternal.TICKS_PER_OP.get(OperationMode.Load));
    final int charge = ticks_1 * InputDataInternal.CHARGE_PER_OP.get(OperationMode.Load);
    return new CalculateResult(ticks_1, charge);
  }

  @Override
  @SuppressWarnings("boxing")
  public CalculateResult calculatePick(final Order order) {
    final int tick_units = 1;
    final int ticks_1 = (tick_units * InputDataInternal.TICKS_PER_OP.get(OperationMode.Pick));
    final int charge = ticks_1 * InputDataInternal.CHARGE_PER_OP.get(OperationMode.Pick);
    return new CalculateResult(ticks_1, charge);
  }

  @Override
  @SuppressWarnings("boxing")
  public CalculateResult calculateStoreContainer() {
    final int tick_units = 1;
    final int ticks_1 = (tick_units * InputDataInternal.TICKS_PER_OP.get(OperationMode.Store));
    final int charge = ticks_1 * InputDataInternal.CHARGE_PER_OP.get(OperationMode.Store);
    return new CalculateResult(ticks_1, charge);
  }

  @Override
  @SuppressWarnings("boxing")
  public CalculateResult calculateStartCharge(final AeroBot aeroBot) {
    final int tick_units = 1
        + ((iinput.maxCharge - ((AeroBotInternal) aeroBot).plannedState.charge) / iinput.chargeLoadPerTick);
    final int ticks_1 = (tick_units * InputDataInternal.TICKS_PER_OP.get(OperationMode.Charge));
    final int charge = ticks_1 * InputDataInternal.CHARGE_PER_OP.get(OperationMode.Charge);
    return new CalculateResult(ticks_1, charge);
  }

  @Override
  public Warehouse.CalculateResult calculateCosts(final List<Operation> operations) {
    final int[] r = operations.stream()
        .map(o -> calculateCosts(o))//
        .collect(() -> new int[] { 0, 0 }, (a, t) -> {
          a[0] += t.ticks;
          a[1] += t.charge;
        }, (a1, a2) -> {
          a1[0] += a2[0];
          a1[1] += a2[1];
        });
    return new Warehouse.CalculateResult(r[0], r[1]);
  }

  Warehouse.CalculateResult calculateCosts(final Operation operation) {
    return new Warehouse.CalculateResult(((OperationInternal) operation).ticks_[1], ((OperationInternal) operation).thisCharge);
  }

  // ----------------------------------------------------------------------------
  // ----------------------------------------------------------------------------

  private Collection<AeroBotInternal> orderedByOpId(final Collection<AeroBotInternal> aeroBots) {
    return aeroBots.stream()
        .map(b -> b.currentOperation())
        .filter(o -> o != null)
        .sorted((o1, o2) -> Integer.compare(o1.taskId, o2.taskId))
        .map(o -> o.aeroBot)
        .collect(Collectors.toList());
  }

  void checkReservation(final OperationInternal.AeroBotLoad op, final String msg) {
    final AeroBotInternal.PlannedState s = op.aeroBot.plannedState;
    final OperationInternal rl = reservedLocations.get(s.loc().getCode());
    if ((rl != null) && !((rl.getMode() == OperationMode.Store) && (rl.aeroBot == op.aeroBot))) {
      throw new IllegalStateException("[CHECK-RES] " + op + " - " + msg + " - " + rl);
    }
    final OperationInternal rc = reservedContainers.get(op.container.getCode());
    if (rc != null) {
      throw new IllegalStateException("[CHECK-RES] " + op + " - " + msg + " - " + rc);
    }
  }

  boolean checkReservation(final OperationInternal.AeroBotStore op, final String msg) {
    final AeroBotInternal.PlannedState s = op.aeroBot.plannedState;
    final OperationInternal rl = reservedLocations.get(s.loc().getCode());
    if ((rl == null) && (msg == null)) {
      return false;
    }
    if ((rl != null) && !((rl.getMode() == OperationMode.Load) && (rl.aeroBot == op.aeroBot))) {
      if (msg == null) {
        return false;
      }
      throw new IllegalStateException("[CHECK-RES] " + op + " - " + msg + " - " + rl);
    }
    return true;
  }

  void setReservation(final OperationInternal.AeroBotLoad op) {
    final AeroBotInternal.PlannedState s = op.aeroBot.plannedState;
    reservedContainers.put(s.container.getCode(), op);
    reservedLocations.put(s.loc().getCode(), op);
    log(Level.FINER, "[RESERVE] (LC) %s: %s, %s", op.aeroBot, s.loc().getCode(), s.container.getCode());
  }

  void setReservation(final OperationInternal.AeroBotStore op) {
    final AeroBotInternal.PlannedState s = op.aeroBot.plannedState;
    reservedLocations.put(s.loc().getCode(), op);
    log(Level.FINER, "[RESERVE] (L) %s: %s", op.aeroBot, s.loc().getCode());
  }

  void clearReservation(final OperationInternal.AeroBotLoad op) {
    final String l = op.aeroBot.getCurrentLocation().getCode();
    final Operation rl = reservedLocations.get(l);
    if (rl.getMode() == OperationMode.Load) {
      reservedLocations.remove(l);
      log(Level.FINER, "[CLEAR RESERVE] (L) %s: @%s - %s", op, l, rl);
    } else {
      log(Level.FINER, "[KEEP RESERVE] (L) => %s: @%s - %s", op, l, rl);
    }
  }

  void clearReservation(final OperationInternal.AeroBotStore op) {
    final String l = op.aeroBot.getCurrentLocation().getCode();
    final String c = op.aeroBot.getCurrentContainer().getCode();
    @SuppressWarnings("unused")
    final Operation rl = reservedLocations.remove(l);
    @SuppressWarnings("unused")
    final Operation rc = reservedContainers.remove(c);
    log(Level.FINER, "[CLEAR RESERVE] (LC) %s: @%s : %s", op.aeroBot, l, c);
  }

  // ----------------------------------------------------------------------------
  // ----------------------------------------------------------------------------

  public void initLogging(final boolean useVisualizer) {
    System.setProperty("file.encoding", "UTF-8");

    visualizer = useVisualizer ? new AeroBotVisualizer.DefaultVisualizer() : new AeroBotVisualizer.NoOpVisualizer();

    final String initLogging = System.getProperty("initLogging", "console");
    try {
      final Handler handler;
      if ("console".equals(initLogging)) {
        handler = new MyConsoleHandler();
        handler.setEncoding(StandardCharsets.UTF_8.name());
      } else {
        handler = new FileHandler(initLogging, true);
        handler.setEncoding(StandardCharsets.UTF_8.name());
        handler.setFormatter(new MyConsoleHandler.MyFormatter());
      }
      log = Logger.getLogger("kcc");
      log.setUseParentHandlers(false);
      Arrays.stream(log.getHandlers()).forEach(h -> log.removeHandler(h));
      final Level logLevel = Level.parse(System.getProperty("logLevel", "SEVERE"));
      handler.setLevel(logLevel);
      log.setLevel(logLevel);
      log.addHandler(handler);
      final Logger r = Logger.getLogger("");
      r.setLevel(Level.OFF);
    } catch (final Exception exception) {
      log = Logger.getLogger("kcc");
      log.setUseParentHandlers(false);
      log.setLevel(Level.OFF);
      final Logger r = Logger.getLogger("");
      r.setLevel(Level.OFF);
    }
  }

  private Logger log; // @dummy: need as permanent reference, otherwise logging would stop

  @Override
  public void log(final Level level, final String message, final Object... params) {
    log(level, message, null, params);
  }

  @Override
  public void log(final Level level, final String message, final Throwable throwable, final Object... params) {
    if (log.isLoggable(level)) {
      log.log(level, params.length == 0 ? message : String.format(message, params), throwable);
    }
  }

  // ----------------------------------------------------------------------------
  // ----------------------------------------------------------------------------
}