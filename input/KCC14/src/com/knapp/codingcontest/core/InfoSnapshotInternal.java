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

import java.io.Serializable;
import java.util.Map;
import java.util.TreeMap;

import com.knapp.codingcontest.operations.AeroBot;
import com.knapp.codingcontest.operations.AeroBot.OperationMode;
import com.knapp.codingcontest.operations.CostFactors;
import com.knapp.codingcontest.operations.InfoSnapshot;

public class InfoSnapshotInternal implements InfoSnapshot {
  private static final long serialVersionUID = 1L;

  // ----------------------------------------------------------------------------

  private final CostFactors c;
  public final int ticksRuntime;
  public final Map<String, AeroBotStatisticsInternal> stats = new TreeMap<>();
  public final Map<AeroBot.OperationMode, AeroBotStatisticsInternal.ModeStatisticsInternal> modeStats = new TreeMap<>();
  public final AeroBotStatisticsInternal.ModeStatisticsInternal totalStats = new AeroBotStatisticsInternal.ModeStatisticsInternal();

  private final int unfinishedOrderCount;

  // ----------------------------------------------------------------------------

  InfoSnapshotInternal(final WarehouseInternal iwarehouse, final int currentTick) {
    c = iwarehouse.getCostFactors();
    ticksRuntime = currentTick;

    for (final AeroBotInternal aeroBot : iwarehouse.iinput.aeroBots.values()) {
      final AeroBotStatisticsInternal as0 = aeroBot.stats;
      final AeroBotStatisticsInternal as = new AeroBotStatisticsInternal();
      for (final OperationMode mode : as0.mstat.keySet()) {
        copy(as.getModeStatistics(mode), as0.getModeStatistics(mode));
        modeStats.put(mode, new AeroBotStatisticsInternal.ModeStatisticsInternal());
      }
      stats.put(aeroBot.getCode(), as);
    }

    for (final AeroBot.OperationMode mode : AeroBot.OperationMode.values()) {
      final AeroBotStatisticsInternal.ModeStatisticsInternal ms = modeStats.get(mode);
      stats.values().stream().map(s -> s.mstat.get(mode)).forEach(s -> {
        ms.count += s.count;
        ms.tick_units += s.tick_units;
        ms.ticks += s.ticks;
      });
      totalStats.count += ms.count;
      totalStats.tick_units += ms.tick_units;
      totalStats.ticks += ms.ticks;
    }

    unfinishedOrderCount = iwarehouse.iinput.getUnfinishedOrders();
  }

  // ----------------------------------------------------------------------------

  @Override
  public String toString() {
    return "InfoSnapshot[" + stats + "]";
  }

  // ----------------------------------------------------------------------------

  private void copy(final AeroBotStatisticsInternal.ModeStatisticsInternal dst,
      final AeroBotStatisticsInternal.ModeStatisticsInternal src) {
    dst.count = src.count;
    dst.tick_units = src.tick_units;
    dst.ticks = src.ticks;
  }

  // ----------------------------------------------------------------------------

  @Override
  public int getUnfinishedOrderCount() {
    return unfinishedOrderCount;
  }

  @Override
  public int getTicksRuntime() {
    return ticksRuntime;
  }

  @Override
  public double getUnfinishedOrdersCost() {
    return getUnfinishedOrderCount() * c.getUnfinishedOrderCost();
  }

  @Override
  public double getTicksCost() {
    return getTicksRuntime() * c.getCostPerTick();
  }

  @Override
  public double getTotalCost() {
    return getUnfinishedOrdersCost() + getTicksCost();
  }

  // ----------------------------------------------------------------------------

  @Override
  public AeroBotStatistics getAeroBotStatistics(final AeroBot aeroBot) {
    return stats.get(aeroBot.getCode());
  }

  @Override
  public AeroBotStatistics.ModeStatistics getModeStatistics(final OperationMode mode) {
    return modeStats.get(mode);
  }

  @Override

  public AeroBotStatistics.ModeStatistics getTotalStatistics() {
    return totalStats;
  }

  // ----------------------------------------------------------------------------
  // ----------------------------------------------------------------------------

  public static class AeroBotStatisticsInternal implements AeroBotStatistics, Serializable {
    private static final long serialVersionUID = 1L;

    AeroBotStatisticsInternal() {
    }

    public static class ModeStatisticsInternal implements AeroBotStatistics.ModeStatistics, Serializable {
      private static final long serialVersionUID = 1L;

      public long count = 0;
      public long tick_units = 0;
      public long ticks = 0;

      public ModeStatisticsInternal() {
      }

      @Override
      public String toString() {
        return "m[#" + count + ", ()=" + tick_units + "]{*" + ticks + "}";
      }

      @Override
      public long get_count() {
        return count;
      }

      @Override
      public long get_tick_units() {
        return tick_units;
      }

      @Override
      public long get_ticks() {
        return ticks;
      }
    }

    private final Map<AeroBot.OperationMode, ModeStatisticsInternal> mstat;

    {
      mstat = new TreeMap<>();
      for (final AeroBot.OperationMode m : AeroBot.OperationMode.values()) {
        mstat.put(m, new ModeStatisticsInternal());
      }
    }

    @Override
    public ModeStatisticsInternal getModeStatistics(final OperationMode mode) {
      return mstat.get(mode);
    }

    @Override
    public String toString() {
      return "AeroBotStat[" + mstat + "]";
    }
  }
}
