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

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.knapp.codingcontest.data.Container;
import com.knapp.codingcontest.data.Order;
import com.knapp.codingcontest.data.Rack;
import com.knapp.codingcontest.data.Waypoint;
import com.knapp.codingcontest.operations.AeroBot;
import com.knapp.codingcontest.operations.CostFactors;

public class InputDataInternal {
  // ----------------------------------------------------------------------------

  private static final String PATH_INPUT_DATA;

  static {
    try {
      PATH_INPUT_DATA = new File(System.getProperty("dataPath", "./data")).getCanonicalPath();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  // ----------------------------------------------------------------------------

  private final String dataPath;

  private final CostFactors costFactors;

  protected final Map<String, AeroBotInternal> aeroBots = new TreeMap<>();
  protected ParkingAreaInternal parkingArea;
  protected ChargingAreaInternal chargingArea;

  int maxCharge;
  int chargeLoadPerTick;

  protected PickAreaInternal pickArea;

  protected RackInternal[][] racks_;
  protected final Map<String, List<ContainerInternal>> containers = new LinkedHashMap<>();

  protected final LinkedHashMap<Integer, MyOrder> orders = new LinkedHashMap<>();

  WarehouseInternal iwarehouse;
  static Map<AeroBot.OperationMode, Integer> TICKS_PER_OP = new EnumMap<>(AeroBot.OperationMode.class);
  static Map<AeroBot.OperationMode, Integer> CHARGE_PER_OP = new EnumMap<>(AeroBot.OperationMode.class);

  // ----------------------------------------------------------------------------

  public InputDataInternal(final CostFactors costFactors) {
    this(InputDataInternal.PATH_INPUT_DATA, costFactors);
  }

  protected InputDataInternal(final String dataPath, final CostFactors costFactors) {
    this.dataPath = dataPath != null ? dataPath : InputDataInternal.PATH_INPUT_DATA;
    this.costFactors = costFactors;
  }

  @Override
  public String toString() {
    return "InputData@" + dataPath;
  }

  // ----------------------------------------------------------------------------

  public CostFactors getCostFactors() {
    return costFactors;
  }

  public List<Order> getAllOrders() {
    return Collections.unmodifiableList(new ArrayList<>(orders.values()));
  }

  public Collection<Container> getAllContainers() {
    return Collections
        .unmodifiableCollection(containers.values().stream().flatMap(l -> l.stream()).collect(Collectors.toList()));
  }

  public List<Rack> getAllRacks() {
    return Arrays.stream(racks_).flatMap((final RackInternal[] r) -> Arrays.stream(r)).collect(Collectors.toList());
  }

  // ----------------------------------------------------------------------------

  public int getUnfinishedOrders() {
    return (int) orders.values().stream().filter(o -> !o.isFinished()).count();
  }

  // ----------------------------------------------------------------------------

  public void readData(final WarehouseInternal iwarehouse) throws IOException {
    readWarehouseLayout();
    readProductContainers();
    readOrders();
    iwarehouse.prepareAfterRead();
  }

  // ----------------------------------------------------------------------------
  // ----------------------------------------------------------------------------

  @SuppressWarnings("boxing")
  private void readWarehouseLayout() throws IOException {
    final Properties properties = new Properties();
    try (InputStreamReader isr = new InputStreamReader(new FileInputStream(fullFileName("warehouse.properties")),
        Charset.forName("ISO-8859-1"))) {
      properties.load(isr);

      {
        final int nrOfAeroBots = Integer.parseInt(properties.getProperty("number-of-aero-bots"));
        final int[] loc0 = loc(properties.getProperty("aero-bot-parking-location-origin").split("/"));
        parkingArea = new ParkingAreaInternal("Parking", loc0[0], loc0[1]);
        maxCharge = Integer.parseInt(properties.getProperty("charge.max"));
        for (int i = 0; i < nrOfAeroBots; i++) {
          final AeroBotInternal aeroBot = new AeroBotInternal(this, String.format("AB%02d", i + 1), maxCharge);
          aeroBots.put(aeroBot.getCode(), aeroBot);
        }
      }

      {
        final int nrOfChargingPoints = Integer.parseInt(properties.getProperty("number-of-charging-points"));
        final int[] loc0 = loc(properties.getProperty("charging-points-location-origin").split("/"));
        final int nrOfAeroBots = Integer.parseInt(properties.getProperty("charging-points-number-of-aero-bots"));
        chargingArea = new ChargingAreaInternal(this, "Charging", loc0[0], loc0[1], nrOfChargingPoints, nrOfAeroBots);
        chargeLoadPerTick = Integer.parseInt(properties.getProperty("charge.load-per-tick"));
      }

      {
        final int[] loc0 = loc(properties.getProperty("pick-stations-location-origin").split("/"));
        final int nrOfPickStations = Integer.parseInt(properties.getProperty("number-of-pick-stations"));
        pickArea = new PickAreaInternal(this, "Picking", loc0[0], loc0[1], nrOfPickStations);
      }

      {
        final int[] loc0 = loc(properties.getProperty("racks-location-origin").split("/"));
        final int[] geom = Arrays.stream(properties.getProperty("geometry-of-racks").split("/"))
            .mapToInt(v -> Integer.parseInt(v))
            .toArray();
        final int[] offs = offs(properties.getProperty("racks-location-offsets").split("/"));
        racks_ = new RackInternal[geom[1]][geom[0]];
        for (int y = 0; y < geom[1]; y++) {
          for (int x = 0; x < geom[0]; x++) {
            final String code = String.format("R%02d%02d", loc0[0] + (x * offs[0]), loc0[1] + (y * offs[1]));
            final RackInternal rack = new RackInternal(code, loc0[0] + (x * offs[0]), loc0[1] + (y * offs[1]), geom[2]);
            racks_[y][x] = rack;
          }
        }
      }

      {
        for (final AeroBot.OperationMode m : AeroBot.OperationMode.values()) {
          final Integer ticksPerOp = Integer.valueOf(properties.getProperty("ticks.per-op." + m.name()));
          InputDataInternal.TICKS_PER_OP.put(m, ticksPerOp);

          final Integer chargePerOp = Integer.valueOf(properties.getProperty("charge.per-op." + m.name()));
          InputDataInternal.CHARGE_PER_OP.put(m, chargePerOp);
        }
      }
    }
  }

  private int[] loc(final String[] loc_) {
    if (loc_.length < 3) {
      return new int[] { Integer.parseInt(loc_[0]), Integer.parseInt(loc_[1]), 0 };
    }
    return new int[] { Integer.parseInt(loc_[0]), Integer.parseInt(loc_[1]), Integer.parseInt(loc_[2]) };
  }

  private int[] offs(final String[] offs_) {
    final int[] offs = new int[offs_.length];
    for (int i = 0; i < offs.length; i++) {
      offs[i] = Integer.parseInt(offs_[i]);
    }
    return offs;
  }

  // ............................................................................

  private void readProductContainers() throws IOException {
    final Reader fr = new FileReader(fullFileName("product-containers.csv"));
    BufferedReader reader = null;
    try {
      reader = new BufferedReader(fr);
      for (String line = reader.readLine(); line != null; line = reader.readLine()) {
        line = line.trim();
        if ("".equals(line) || line.startsWith("#")) {
          continue;
        }
        // #code;product-code;x;y;level;
        final String[] columns = splitCsv(line);
        final String code = columns[0];
        final String product = columns[1].intern();
        final int x = Integer.parseInt(columns[2]) - 1;
        final int y = Integer.parseInt(columns[3]) - 1;
        final int lvl = Integer.parseInt(columns[4]);
        final ContainerInternal container = new ContainerInternal(code, product);
        racks_[y][x].getRackStorageLocation(lvl).pushContainer(container);
        containers.computeIfAbsent(container.getProductCode(), p -> new ArrayList<>()).add(container);
      }
    } finally {
      close(reader);
      close(fr);
    }
  }

  // ............................................................................

  private void readOrders() throws IOException {
    final Reader fr = new FileReader(fullFileName("order-lines.csv"));
    BufferedReader reader = null;
    try {
      final Map<Integer, List<String>> _orders = new LinkedHashMap<>();
      reader = new BufferedReader(fr);
      for (String line = reader.readLine(); line != null; line = reader.readLine()) {
        line = line.trim();
        if ("".equals(line) || line.startsWith("#")) {
          continue;
        }
        // #sequence;code;product-code;
        final String[] columns = splitCsv(line);
        final Integer sequence = Integer.valueOf(columns[0]);
        //final String ocode = columns[1];
        final String product = columns[2].intern();
        _orders.computeIfAbsent(sequence, c -> new ArrayList<>()).add(product);
      }
      for (final Map.Entry<Integer, List<String>> e : _orders.entrySet()) {
        orders.put(e.getKey(), new MyOrder(e.getKey(), e.getValue()));
      }
    } finally {
      close(reader);
      close(fr);
    }
  }

  // ----------------------------------------------------------------------------

  protected File fullFileName(final String fileName) {
    final String fullFileName = dataPath + File.separator + fileName;
    return new File(fullFileName);
  }

  protected void close(final Closeable closeable) {
    if (closeable != null) {
      try {
        closeable.close();
      } catch (final IOException exception) {
        iwarehouse.log(Level.SEVERE, "Oops", exception);
      }
    }
  }

  // ----------------------------------------------------------------------------

  protected String[] splitCsv(final String line) {
    return line.split(";");
  }

  // ----------------------------------------------------------------------------
  // ----------------------------------------------------------------------------

  MyOrder internal(final Order order) {
    return orders.get(order.getSequence());
  }

  AeroBotInternal internal(final AeroBot aeroBot) {
    return aeroBots.get(aeroBot.getCode());
  }

  ContainerInternal internal(final Container container) {
    return (ContainerInternal) container;
  }

  AbstractWaypoint internal(final Waypoint waypoint) {
    return (AbstractWaypoint) waypoint;
  }

  // ............................................................................

  public static class MyOrder extends Order {
    private final List<String> openProducts;

    MyOrder(final Order order) {
      super(order.getSequence(), order.getProductCode());
      openProducts = new ArrayList<>();
      openProducts.add(order.getProductCode());
    }

    MyOrder(final Integer sequence, final List<String> products) {
      super(sequence, products.get(0));
      openProducts = new ArrayList<>();
      openProducts.add(products.get(0));
    }

    boolean processedProduct(final String product) {
      return openProducts.remove(product);
    }

    boolean isFinished() {
      return openProducts.isEmpty();
    }
  }

  // ----------------------------------------------------------------------------

  public InputStat inputStat() {
    return new InputStat(this);
  }

  public static final class InputStat {
    public final int countOrders;
    public final int countProducts;

    public final int countRacks;
    public final int countRackLocations_;
    public final int countContainers;
    public final double containerFilling;

    private InputStat(final InputDataInternal iinput) {
      countOrders = iinput.orders.size();
      countProducts = (int) iinput.orders.values().stream().flatMap(o -> o.openProducts.stream()).sorted().distinct().count();

      countRacks = iinput.racks_.length * iinput.racks_[0].length;
      countRackLocations_ = countRacks * iinput.racks_[0][0].getMaxLevel();
      countContainers = iinput.containers.values().stream().mapToInt(cs -> cs.size()).sum();

      containerFilling = (double) countContainers / countRackLocations_;
    }
  }

  // ----------------------------------------------------------------------------
}
