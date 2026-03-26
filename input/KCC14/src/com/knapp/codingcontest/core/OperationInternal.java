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

import java.util.logging.Level;

import com.knapp.codingcontest.data.Container;
import com.knapp.codingcontest.operations.AeroBot;
import com.knapp.codingcontest.operations.Operation;

@SuppressWarnings("boxing")
public abstract class OperationInternal implements Operation, Comparable<OperationInternal> {
  public final int taskId;
  public final int tickCreate;
  public int tick_units;
  final int[] ticks_ = new int[] { -1, -1 };
  public int tickStart;
  public int tickFinished;

  final AeroBotInternal aeroBot;

  final int plannedCharge;
  int startCharge;
  int thisCharge;
  int endCharge;

  protected OperationInternal(final AeroBotInternal aeroBot, final int taskId, final int currentTick) {
    this.taskId = taskId;
    tickCreate = currentTick;
    this.aeroBot = aeroBot;
    plannedCharge = aeroBot.plannedState.charge;
  }

  protected void updateTicksCharge() {
    tick_units = plan_tick_units();
    ticks_[1] = (tick_units * InputDataInternal.TICKS_PER_OP.get(getMode()));
    thisCharge = (ticks_[1] * InputDataInternal.CHARGE_PER_OP.get(getMode()));
    aeroBot.plannedState.charge -= thisCharge;
  }

  // only use for TICK!!!
  protected OperationInternal(final int taskId, final int currentTick) {
    this.taskId = taskId;
    tickCreate = currentTick;
    aeroBot = null;
    plannedCharge = 0;
  }

  // ----------------------------------------------------------------------------

  @Override
  public AeroBotInternal getAeroBot() {
    return aeroBot;
  }

  @Override
  public String toString() {
    return String.format("%-5s: %-7s ticks=%3d/%3d (ch-=%d)", //
        (aeroBot != null ? aeroBot.getCode() : "ALL"), getMode(), ticks_[0], ticks_[1], thisCharge);
  }

  @Override
  public abstract String toResultString();

  // ----------------------------------------------------------------------------

  protected int plan_tick_units() {
    return 1;
  }

  abstract void planned();

  abstract void startOp(int currentTick);

  @SuppressWarnings("unused")
  void tickOp(final int currentTick) {
    // progressing changes - e.g. charge
  }

  abstract void finishOp(int currentTick);

  static enum FinStatus {
    Waiting, Working, Finished,;
  }

  FinStatus checkFinished() {
    if (ticks_[1] < 0) {
      return FinStatus.Waiting;
    }
    return (ticks_[0] >= ticks_[1]) ? FinStatus.Finished : FinStatus.Working;
  }

  FinStatus incTick() {
    if (ticks_[1] < 0) {
      return FinStatus.Waiting;
    }
    if (ticks_[0] < ticks_[1]) {
      ticks_[0]++;
    }
    return (ticks_[0] >= ticks_[1]) ? FinStatus.Finished : FinStatus.Working;
  }

  @Override
  public final int compareTo(final OperationInternal o) {
    return taskId - o.taskId;
  }

  // ----------------------------------------------------------------------------

  public static class AeroBotMoveH extends OperationInternal implements Operation.AeroBotMoveH {
    public final AbstractWaypoint from;
    public final AbstractWaypoint to;

    AeroBotMoveH(final AeroBotInternal aeroBot, final int currentTick, final AbstractWaypoint to) {
      super(aeroBot, aeroBot.iinput.iwarehouse.taskIds.getAndIncrement(), currentTick);
      from = aeroBot.plannedState.waypoint;
      this.to = to;
      updateTicksCharge();
    }

    @Override
    public AbstractWaypoint getTo() {
      return to;
    }

    @Override
    public AeroBot.OperationMode getMode() {
      return AeroBot.OperationMode.MoveH;
    }

    @Override
    public final String toString() {
      return String.format("%s - %s => %s", super.toString(), //
          from, to);
    }

    @Override
    public String toResultString() {
      return String.format("%d;%d;%s;%s;%s;%d;%d;", taskId, tickCreate, //
          getMode(), aeroBot.getCode(), to.getType(), to.getX(), to.getY());
    }

    @Override
    protected int plan_tick_units() {
      return aeroBot.plannedState.waypoint.distance(to);
    }

    @Override
    void planned() {
    }

    @Override
    void startOp(final int currentTick) {
      aeroBot.doStart(this);
    }

    @Override
    void finishOp(final int currentTick) {
      aeroBot.doFinish(this);
    }
  }

  // ............................................................................

  public static class AeroBotMoveV extends OperationInternal implements Operation.AeroBotMoveV {
    public final int from;
    public final int to;

    AeroBotMoveV(final AeroBotInternal aeroBot, final int currentTick, final int to) {
      super(aeroBot, aeroBot.iinput.iwarehouse.taskIds.getAndIncrement(), currentTick);
      from = aeroBot.plannedState.level;
      this.to = to;
      updateTicksCharge();
    }

    @Override
    public int getTo() {
      return to;
    }

    @Override
    public AeroBot.OperationMode getMode() {
      return AeroBot.OperationMode.MoveV;
    }

    @Override
    public final String toString() {
      return String.format("%s - climb  %d => %d", super.toString(), //
          from, to);
    }

    @Override
    public String toResultString() {
      return String.format("%d;%d;%s;%s;%d;", taskId, tickCreate, //
          getMode(), aeroBot.getCode(), to);
    }

    @Override
    protected int plan_tick_units() {
      return Math.abs(to - aeroBot.plannedState.level);
    }

    @Override
    void planned() {
      ((RackInternal) aeroBot.plannedState.waypoint).plannedAdd(this);
    }

    @Override
    void startOp(final int currentTick) {
      aeroBot.doStart(this);
    }

    @Override
    void finishOp(final int currentTick) {
      aeroBot.doFinish(this);
    }
  }

  // ............................................................................

  public static class AeroBotLoad extends OperationInternal implements Operation.AeroBotLoad {
    public final ContainerInternal container;

    AeroBotLoad(final AeroBotInternal aeroBot, final int currentTick, final ContainerInternal container) {
      super(aeroBot, aeroBot.iinput.iwarehouse.taskIds.getAndIncrement(), currentTick);
      this.container = container;
      updateTicksCharge();
    }

    @Override
    public AeroBot.OperationMode getMode() {
      return AeroBot.OperationMode.Load;
    }

    @Override
    public final String toString() {
      return String.format("%s - <<< %s", super.toString(), //
          container);
    }

    @Override
    public String toResultString() {
      return String.format("%d;%d;%s;%s;%s;", taskId, tickCreate, //
          getMode(), aeroBot.getCode(), container.getCode());
    }

    @Override
    void planned() {
    }

    @Override
    void startOp(final int currentTick) {
      aeroBot.doStart(this);
    }

    @Override
    void finishOp(final int currentTick) {
      aeroBot.doFinish(this);
    }
  }

  // ............................................................................

  public static class AeroBotStore extends OperationInternal implements Operation.AeroBotStore {
    private final Container container;

    AeroBotStore(final AeroBotInternal aeroBot, final int currentTick) {
      super(aeroBot, aeroBot.iinput.iwarehouse.taskIds.getAndIncrement(), currentTick);
      container = aeroBot.plannedState.container;
      updateTicksCharge();
    }

    @Override
    public AeroBot.OperationMode getMode() {
      return AeroBot.OperationMode.Store;
    }

    @Override
    public final String toString() {
      return String.format("%s - >>> %s", super.toString(), //
          container);
    }

    @Override
    public String toResultString() {
      return String.format("%d;%d;%s;%s;", taskId, tickCreate, //
          getMode(), aeroBot.getCode());
    }

    @Override
    void planned() {
    }

    @Override
    void startOp(final int currentTick) {
      aeroBot.doStart(this);
    }

    @Override
    void finishOp(final int currentTick) {
      aeroBot.doFinish(this);
    }
  }

  public static class AeroBotCharge extends OperationInternal implements Operation.AeroBotCharge {
    AeroBotCharge(final AeroBotInternal aeroBot, final int currentTick) {
      super(aeroBot, aeroBot.iinput.iwarehouse.taskIds.getAndIncrement(), currentTick);
      updateTicksCharge();
    }

    @Override
    public AeroBot.OperationMode getMode() {
      return AeroBot.OperationMode.Charge;
    }

    @Override
    public final String toString() {
      return String.format("%s - %d (%d)", super.toString(), //
          startCharge, ((int) ((aeroBot.charge[0] / aeroBot.charge[1]) * 100.0)));
    }

    @Override
    public String toResultString() {
      return String.format("%d;%d;%s;%s;", taskId, tickCreate, //
          getMode(), aeroBot.getCode());
    }

    @Override
    protected int plan_tick_units() {
      return 1 + ((aeroBot.getMaxCharge() - aeroBot.plannedState.charge) / aeroBot.iinput.chargeLoadPerTick);
    }

    @Override
    void planned() {
      ((ChargingAreaInternal) aeroBot.plannedState.waypoint).planned(this);
    }

    @Override
    void startOp(final int currentTick) {
      final int tu = 1 + ((aeroBot.getMaxCharge() - aeroBot.getCurrentCharge()) / aeroBot.iinput.chargeLoadPerTick);
      final int t_1 = (tu * InputDataInternal.TICKS_PER_OP.get(getMode()));
      if (ticks_[1] < t_1) {
        ticks_[1] = t_1; // ugly workaround :-/
      }
      aeroBot.doStart(this);
    }

    @Override
    FinStatus incTick() {
      final ChargingAreaInternal charging = aeroBot.iinput.iwarehouse.getChargingArea();
      if (!charging.isCharging(aeroBot)) {
        return FinStatus.Waiting;
      }
      return super.incTick();
    }

    @Override
    void tickOp(final int currentTick) {
      super.tickOp(currentTick);

      final ChargingAreaInternal charging = aeroBot.iinput.iwarehouse.getChargingArea();

      if (charging.isCharging(aeroBot)) {
        if (aeroBot.getCurrentCharge() < aeroBot.getMaxCharge()) {
          aeroBot.incCurrentCharge(aeroBot.iinput.chargeLoadPerTick);
          log(Level.FINE, "+1 @%6d :: %s", currentTick, this);
        }
      } else {
        if (aeroBot.getCurrentCharge() <= aeroBot.getMaxCharge()) {
          if (charging.hasFreeChargingSlot()) {
            charging.startCharging(aeroBot);
            if (checkFinished() == OperationInternal.FinStatus.Finished) {
              aeroBot.incCurrentCharge(aeroBot.iinput.chargeLoadPerTick);
              log(Level.FINE, "+2 @%6d :: %s", currentTick, this);
            }
          }
        }
      }
    }

    @Override
    void finishOp(final int currentTick) {
      aeroBot.doFinish(this);
    }
  }

  // ............................................................................

  public static class AeroBotPick extends OperationInternal implements Operation.AeroBotPick {
    final InputDataInternal.MyOrder order;
    final String plannedProductCode;

    AeroBotPick(final AeroBotInternal aeroBot, final int currentTick, final InputDataInternal.MyOrder order) {
      super(aeroBot, aeroBot.iinput.iwarehouse.taskIds.getAndIncrement(), currentTick);
      this.order = order;
      plannedProductCode = aeroBot.plannedState.container.getProductCode();
      updateTicksCharge();
    }

    @Override
    public InputDataInternal.MyOrder getOrder() {
      return order;
    }

    @Override
    public AeroBot.OperationMode getMode() {
      return AeroBot.OperationMode.Pick;
    }

    @Override
    public final String toString() {
      return String.format("%s - %s", super.toString(), //
          order);
    }

    @Override
    public String toResultString() {
      return String.format("%d;%d;%s;%s;%d;", taskId, tickCreate, //
          getMode(), aeroBot.getCode(), order.getSequence());
    }

    @Override
    void planned() {
      ((PickAreaInternal) aeroBot.plannedState.waypoint).planned(this);
    }

    @Override
    void startOp(final int currentTick) {
      aeroBot.doStart(this);
    }

    @Override
    void finishOp(final int currentTick) {
      aeroBot.doFinish(this);
    }
  }

  // ............................................................................

  static class ExecuteTicks extends OperationInternal implements Operation {
    int count = 0;

    ExecuteTicks(final int taskId, final int currentTick) {
      super(taskId, currentTick);
    }

    @Override
    public AeroBot.OperationMode getMode() {
      return null;
    }

    @Override
    public final String toString() {
      return String.format("%s - (%d)", super.toString(), //
          count);
    }

    @Override
    public String toResultString() {
      return String.format("%d;%d;TICK;%d;", taskId, tickCreate, count);
    }

    @Override
    protected int plan_tick_units() {
      return 0;
    }

    @Override
    void planned() {
      throw new UnsupportedOperationException();
    }

    @Override
    void startOp(final int currentTick) {
      throw new UnsupportedOperationException();
    }

    @Override
    void finishOp(final int currentTick) {
      throw new UnsupportedOperationException();
    }
  }

  static class _Idle_ extends OperationInternal implements Operation {
    int count = 0;

    _Idle_(final int taskId, final int currentTick) {
      super(taskId, currentTick);
    }

    @Override
    public AeroBot.OperationMode getMode() {
      return null;
    }

    @Override
    public final String toString() {
      return String.format("%s ...", super.toString());
    }

    @Override
    public String toResultString() {
      return String.format("%d;%d;_IDLE_;%d;", taskId, tickCreate, count);
    }

    @Override
    protected int plan_tick_units() {
      return 0;
    }

    @Override
    void planned() {
      throw new UnsupportedOperationException();
    }

    @Override
    void startOp(final int currentTick) {
      throw new UnsupportedOperationException();
    }

    @Override
    void finishOp(final int currentTick) {
      throw new UnsupportedOperationException();
    }
  }

  // ----------------------------------------------------------------------------

  void log(final Level level, final String message, final Object... params) {
    aeroBot.iinput.iwarehouse.log(level, message, params);
  }
}
