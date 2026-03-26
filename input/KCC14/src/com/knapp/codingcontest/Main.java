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

package com.knapp.codingcontest;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.knapp.codingcontest.core.InfoSnapshotInternal;
import com.knapp.codingcontest.core.InfoSnapshotInternal.AeroBotStatisticsInternal;
import com.knapp.codingcontest.core.InputDataInternal;
import com.knapp.codingcontest.core.PrepareUpload;
import com.knapp.codingcontest.core.WarehouseInternal;
import com.knapp.codingcontest.operations.AeroBot;
import com.knapp.codingcontest.operations.CostFactors;
import com.knapp.codingcontest.solution.Solution;

/**
 * ----------------------------------------------------------------------------
 * you may change any code you like
 *   => but changing the output may lead to invalid results on upload!
 * ----------------------------------------------------------------------------
 */
public class Main {
  // ----------------------------------------------------------------------------

  public static void main(final String... args) throws Exception {
    System.out.println("vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv");
    System.out.println("vvv   KNAPP Coding Contest: STARTING...        vvv");
    System.out.println(String.format("vvv                %s                    vvv", Main.DATE_FORMAT.format(new Date())));
    System.out.println("vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv");

    System.out.println("# ------------------------------------------------");
    System.out.println("# ... LOADING INPUT ...");
    final CostFactors costFactors = new MainCostFactors();
    final InputDataInternal iinput = new InputDataInternal(costFactors);
    final WarehouseInternal iwarehouse = new WarehouseInternal(iinput);
    iinput.readData(iwarehouse);
    final InputDataInternal.InputStat istat = iinput.inputStat();

    System.out.println("# ------------------------------------------------");
    System.out.println("# ... RUN YOUR SOLUTION ...");
    final long start = System.currentTimeMillis();
    final Solution solution = new Solution(iwarehouse);
    Throwable throwable = null;
    final boolean useVisualizer = Boolean.parseBoolean(System.getProperty("useVisualizer", "true"));
    try {
      iwarehouse.initLogging(useVisualizer);
      solution.run();
    } catch (final Throwable _throwable) {
      throwable = _throwable;
    }
    final long end = System.currentTimeMillis();
    System.out.println("# ... DONE ... (" + Main.formatInterval(end - start) + ")");

    if (useVisualizer) {
      System.out.println("# ------------------------------------------------");
      System.out.println("# ... GENERATING VISUALIZATION ...");
      try {
        iwarehouse.getVisualizer().generateHTML("aerobot-visualization.html");
        System.out.println("✓ Visualization saved to aerobot-visualization.html");
        if (iwarehouse.getVisualizer().hasConflicts()) {
          System.out.println("⚠ Detected " + iwarehouse.getVisualizer().getConflictCount() + " conflicts (first at tick "
              + iwarehouse.getVisualizer().getFirstConflictTick() + ")");
        }
      } catch (final java.io.IOException e) {
        System.out.println("Failed to generate visualization: " + e.getMessage());
      }
    }

    System.out.println("# ------------------------------------------------");
    System.out.println("# ... WRITING OUTPUT/RESULT ...");
    PrepareUpload.createZipFile(solution, iwarehouse);
    System.out.println(">>> Created " + PrepareUpload.FILENAME_RESULT + " & " + PrepareUpload.uploadFileName(solution));

    System.out.println("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^");
    System.out.println("^^^   KNAPP Coding Contest: FINISHED           ^^^");
    System.out.println(String.format("^^^                %s                    ^^^", Main.DATE_FORMAT.format(new Date())));
    System.out.println("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^");

    System.out.println("# ------------------------------------------------");
    System.out.println("# ... RESULT/COSTS FOR YOUR SOLUTION ...");
    System.out.println("#     " + solution.getParticipantName() + " / " + solution.getParticipantInstitution());

    Main.printResults(solution, istat, iwarehouse);

    if (throwable != null) {
      System.out.println("");
      System.out.println("# ... Ooops ...");
      System.out.println("");
      throwable.printStackTrace(System.out);
    }
  }

  @SuppressWarnings("boxing")
  public static String formatInterval(final long interval) {
    final int h = (int) ((interval / (1000 * 60 * 60)) % 60);
    final int m = (int) ((interval / (1000 * 60)) % 60);
    final int s = (int) ((interval / 1000) % 60);
    final int ms = (int) (interval % 1000);
    return String.format("%02d:%02d:%02d.%03d", h, m, s, ms);
  }

  // ----------------------------------------------------------------------------
  // ----------------------------------------------------------------------------

  @SuppressWarnings("boxing")
  public static void printResults(final Solution solution, final InputDataInternal.InputStat istat,
      final WarehouseInternal iwarehouse) throws Exception {
    final InfoSnapshotInternal info = iwarehouse.getInfoSnapshot();

    final int uo = info.getUnfinishedOrderCount();
    final double c_uo_ = info.getUnfinishedOrdersCost();
    final long ti = info.getTicksRuntime();

    final double c_ti = info.getTicksCost();
    final double c_t = info.getTotalCost();

    System.out.println("# ------------------------------------------------");
    System.out.println("# ... RESULT/COSTS FOR YOUR SOLUTION ...");
    System.out.println("#     " + solution.getParticipantName() + " / " + solution.getParticipantInstitution());

    //
    System.out.println(String.format("  --------------------------------------------------------------"));
    System.out.println(String.format("    INPUT STATISTICS                                            "));
    System.out.println(String.format("  ------------------------------------- : ----------------------"));
    System.out.println(String.format("      #products                         :  %8d", istat.countProducts));
    System.out.println(
        String.format("      #containers                       :  %8d (%1.2f)", istat.countContainers, istat.containerFilling));
    System.out.println(String.format("      #orders                           :  %8d", istat.countOrders));
    System.out.println(
        String.format("      #racks/#locations                 :  %8d / %8d", istat.countRacks, istat.countRackLocations_));

    //
    System.out.println(String.format("  --------------------------------------------------------------"));
    System.out.println(String.format("    RESULT STATISTICS                                           "));
    System.out.println(String.format("  ------------------------------------- : ----------------------"));
    System.out.println(String.format("    #operations                         :        #c       #u       #t"));
    final AeroBotStatisticsInternal.ModeStatisticsInternal mss = new AeroBotStatisticsInternal.ModeStatisticsInternal();
    for (final AeroBot.OperationMode mode : AeroBot.OperationMode.values()) {
      final AeroBotStatisticsInternal.ModeStatisticsInternal ms = new AeroBotStatisticsInternal.ModeStatisticsInternal();
      info.stats.values().stream().map(s -> s.getModeStatistics(mode)).forEach(s -> {
        ms.count += s.count;
        ms.tick_units += s.tick_units;
        ms.ticks += s.ticks;
      });
      System.out.println(String.format("      #%-20s             :  %8d %8d %8d", mode, ms.count, ms.tick_units, ms.ticks));
      mss.count += ms.count;
      mss.tick_units += ms.tick_units;
      mss.ticks += ms.ticks;
    }
    System.out.println(String.format("       %-20s             :  %8d %8d %8d", "[SUM]", mss.count, mss.tick_units, mss.ticks));

    //
    System.out.println(String.format("  ============================================================================="));
    System.out.println(String.format("    RESULTS                                                                    "));
    System.out.println(String.format("  ===================================== : ============ | ======================"));
    System.out.println(String.format("      what                              :       costs  |  (details: count,...)"));
    System.out.println(String.format("  ------------------------------------- : ------------ | ----------------------"));
    System.out.println(String.format("   -> costs/unfinished orders           :  %10.1f  |   %8d", c_uo_, uo));
    System.out.println(String.format("   -> costs ticks runtime               :  %10.1f  |   %8d", c_ti, ti));
    System.out.println(String.format("  ------------------------------------- : ------------ | ----------------------"));
    System.out.println(String.format("   => TOTAL COST                           %10.1f", c_t));
    System.out.println(String.format("                                          ============"));
  }

  // ----------------------------------------------------------------------------

  private static final DateFormat DATE_FORMAT = new SimpleDateFormat("HH:mm:ss");

  // ----------------------------------------------------------------------------
}
