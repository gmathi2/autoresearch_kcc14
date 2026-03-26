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
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.knapp.codingcontest.core.InputDataInternal.MyOrder;
import com.knapp.codingcontest.core.OperationInternal.AeroBotPick;
import com.knapp.codingcontest.data.Order;
import com.knapp.codingcontest.data.Waypoint;
import com.knapp.codingcontest.operations.AeroBot;
import com.knapp.codingcontest.operations.PickArea;

public class PickAreaInternal extends AbstractWaypoint implements PickArea {
  private final InputDataInternal iinput;

  final MyOrder[] currentOrders;
  private final Map<String, AeroBot> aeroBots = new TreeMap<>();
  private final Map<Integer, OperationInternal.AeroBotPick> plannedOrders = new LinkedHashMap<>();

  // ----------------------------------------------------------------------------

  public PickAreaInternal(final InputDataInternal iinput, final String code, final int x, final int y, final int pickSlots) {
    super(Waypoint.Type.Picking, code, x, y);
    this.iinput = iinput;
    currentOrders = new MyOrder[pickSlots];
  }

  // ----------------------------------------------------------------------------

  @Override
  public List<Order> getCurrentOrders() {
    return Collections.unmodifiableList(Arrays.stream(currentOrders).filter(o -> o != null).collect(Collectors.toList()));
  }

  // ----------------------------------------------------------------------------

  int findOrderSlot(final Order order) {
    for (int i = 0; i < currentOrders.length; i++) {
      if (currentOrders[i] == iinput.internal(order)) {
        return i;
      }
    }
    return -1;
  }

  int findFreeSlot() {
    for (int i = 0; i < currentOrders.length; i++) {
      if (currentOrders[i] == null) {
        return i;
      }
    }
    return -1;
  }

  // ----------------------------------------------------------------------------

  void checkReservation(final OperationInternal.AeroBotPick op, final String msg) {
    final OperationInternal.AeroBotPick op0 = plannedOrders.get(op.order.getSequence());
    if (op0 != null) {
      throw new IllegalStateException("[CHECK-RES] " + op + " - " + msg + " - " + op0);
    }
  }

  void planned(final OperationInternal.AeroBotPick op) {
    plannedOrders.put(op.order.getSequence(), op);
  }

  @Override
  public void enter(final AeroBotInternal aeroBot) {
    super.enter(aeroBot);
    aeroBots.put(aeroBot.getCode(), aeroBot);
  }

  @Override
  public void leave(final AeroBotInternal aeroBot) {
    aeroBots.remove(aeroBot.getCode());
    removePlannedBy(aeroBot);
    super.leave(aeroBot);
  }

  private void removePlannedBy(final AeroBotInternal aeroBot) {
    final Iterator<Entry<Integer, AeroBotPick>> it = plannedOrders.entrySet().iterator();
    while (it.hasNext()) {
      final Map.Entry<Integer, AeroBotPick> e = it.next();
      if (e.getValue().aeroBot == aeroBot) {
        it.remove();
        break;
      }
    }
  }

  // ----------------------------------------------------------------------------

  void doAssignOrder(final Order order) {
    for (int i = 0; i < currentOrders.length; i++) {
      if (currentOrders[i] == null) {
        currentOrders[i] = iinput.internal(order);
        return;
      }
    }
  }

  void doClearOrder(final Order order) {
    for (int i = 0; i < currentOrders.length; i++) {
      if (currentOrders[i] == iinput.internal(order)) {
        currentOrders[i] = null;
        return;
      }
    }
  }

  void doPick(final MyOrder order, final AeroBot aeroBot) {
    final MyOrder o = iinput.internal(order);
    if (!checkOrder(o)) {
      throw new IllegalStateException("order not in pick-area " + o);
    }
    final ContainerInternal c = iinput.internal(aeroBot).getCurrentContainer();
    if (!o.processedProduct(c.getProductCode())) {
      throw new IllegalStateException("current container/product " + c + " not for order " + o);
    }
  }

  // ----------------------------------------------------------------------------

  boolean checkOrder(final MyOrder order) {
    for (final MyOrder currentOrder : currentOrders) {
      if ((currentOrder != null) && currentOrder.getSequence().equals(order.getSequence())) {
        return true;
      }
    }
    return false;
  }

  int freeFinishedSlots() {
    int freeFinishedSlots = 0;
    for (int i = 0; i < currentOrders.length; i++) {
      if (isSlotFinished(currentOrders[i])) {
        currentOrders[i] = null;
        freeFinishedSlots++;
      }
    }
    return freeFinishedSlots;
  }

  private boolean isSlotFinished(final MyOrder order) {
    return (order != null) && order.isFinished();
  }

  int assignFreeSlots(final LinkedList<MyOrder> openOrders) {
    final List<MyOrder> assigned = new ArrayList<>(currentOrders.length);
    for (int i = 0; (!openOrders.isEmpty()) && (i < currentOrders.length); i++) {
      if (currentOrders[i] == null) {
        currentOrders[i] = openOrders.removeFirst();
        assigned.add(currentOrders[i]);
      }
    }
    if (!assigned.isEmpty()) {
      iinput.iwarehouse.log(Level.CONFIG, "* [PICK-AREA] assigned new orders: " + assigned);
    }
    return assigned.size();
  }

  // ----------------------------------------------------------------------------
  // ----------------------------------------------------------------------------
}
