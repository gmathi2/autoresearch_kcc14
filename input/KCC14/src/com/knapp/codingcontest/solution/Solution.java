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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.knapp.codingcontest.data.Container;
import com.knapp.codingcontest.data.Institute;
import com.knapp.codingcontest.data.Location;
import com.knapp.codingcontest.data.Order;
import com.knapp.codingcontest.data.Rack;
import com.knapp.codingcontest.data.Waypoint;
import com.knapp.codingcontest.operations.AeroBot;
import com.knapp.codingcontest.operations.Warehouse;

public class Solution {
  public String getParticipantName() {
    return "AutoResearch";
  }

  public Institute getParticipantInstitution() {
    return Institute.Sonstige;
  }

  protected final Warehouse warehouse;
  
  private final Map<AeroBot, Integer> botAssignedOrder = new HashMap<>();
  private final Map<AeroBot, String> botAssignedContainer = new HashMap<>();

  public Solution(final Warehouse warehouse) {
    this.warehouse = warehouse;
  }

  public void run() throws Exception {
    while (!warehouse.areAllOrdersFinished()) {
      assignTasks();
      warehouse.executeTicksUntilFirstBotToFinish();
      
      if (allBotsIdle() && !warehouse.areAllOrdersFinished()) {
          warehouse.executeOneTick();
      }
    }
  }

  private boolean allBotsIdle() {
    for (AeroBot bot : warehouse.getAllAeroBots()) {
      if (!bot.getOpenOperations().isEmpty()) {
        return false;
      }
    }
    return true;
  }

  private void assignTasks() {
    // 1. Cleanup finished tasks
    for (AeroBot bot : warehouse.getAllAeroBots()) {
        if (bot.getOpenOperations().isEmpty()) {
            botAssignedOrder.remove(bot);
            botAssignedContainer.remove(bot);
        }
    }

    Set<Integer> currentlyAssigned = new HashSet<>(botAssignedOrder.values());
    List<AeroBot> idleBots = new ArrayList<>();
    for (AeroBot bot : warehouse.getAllAeroBots()) {
        if (bot.getOpenOperations().isEmpty()) {
            if (bot.getCurrentCharge() < bot.getMaxCharge() * 0.3) {
                bot.planMoveToWaypoint(warehouse.getChargingArea());
                bot.planStartCharge();
            } else {
                idleBots.add(bot);
            }
        }
    }

    if (idleBots.isEmpty()) return;

    // 2. Identify candidate orders (Priority: Current, then Next Open)
    List<Order> candidates = new ArrayList<>(warehouse.getPickArea().getCurrentOrders());
    List<Order> open = warehouse.getOpenOrders();
    for (Order o : open) {
        if (candidates.size() >= 50) break;
        boolean alreadyInCandidates = false;
        for (Order c : candidates) {
            if (c.getSequence().equals(o.getSequence())) {
                alreadyInCandidates = true;
                break;
            }
        }
        if (!alreadyInCandidates) {
            candidates.add(o);
        }
    }

    // 3. Assign
    int botIdx = 0;
    for (Order order : candidates) {
        if (botIdx >= idleBots.size()) break;
        if (currentlyAssigned.contains(order.getSequence())) continue;

        AeroBot bot = idleBots.get(botIdx);
        if (tryAssignSpecificOrder(bot, order)) {
            botIdx++;
            currentlyAssigned.add(order.getSequence());
        }
    }
  }

  private boolean tryAssignSpecificOrder(AeroBot bot, Order order) {
    Collection<Container> containers = warehouse.findAvailableContainers(order.getProductCode());
    for (Container container : containers) {
      if (botAssignedContainer.containsValue(container.getCode())) {
        continue;
      }

      Location loc = container.getCurrentLocation();
      if (loc != null && loc.getType() == Location.Type.Rack) {
        Rack.RackStorageLocation rsl = (Rack.RackStorageLocation) loc;

        int estimatedCharge = estimateCycleCharge(bot, rsl, order, container);
        if (bot.getCurrentCharge() < estimatedCharge + 1000) {
          return false;
        }

        botAssignedOrder.put(bot, order.getSequence());
        botAssignedContainer.put(bot, container.getCode());

        bot.planMoveToWaypoint(rsl.getWaypoint());
        bot.planClimbToLevel(rsl.getLevel());
        bot.planLoadContainer(container);
        bot.planClimbToLevel(0);
        bot.planMoveToWaypoint(warehouse.getPickArea());
        bot.planPick(order);
        bot.planMoveToWaypoint(rsl.getWaypoint());
        bot.planClimbToLevel(rsl.getLevel());
        bot.planStoreContainer();
        bot.planClimbToLevel(0);
        return true;
      }
    }
    return false;
  }

  private int estimateCycleCharge(AeroBot bot, Rack.RackStorageLocation rsl, Order order, Container container) {
    int charge = 0;
    Waypoint botPos = bot.getCurrentWaypoint();
    charge += warehouse.calculateMoveToWaypoint(botPos, rsl.getWaypoint()).charge;
    charge += warehouse.calculateClimbToLevel(0, rsl.getLevel()).charge;
    charge += warehouse.calculateLoadContainer(container).charge;
    charge += warehouse.calculateClimbToLevel(rsl.getLevel(), 0).charge;
    charge += warehouse.calculateMoveToWaypoint(rsl.getWaypoint(), warehouse.getPickArea()).charge;
    charge += warehouse.calculatePick(order).charge;
    charge += warehouse.calculateMoveToWaypoint(warehouse.getPickArea(), rsl.getWaypoint()).charge;
    charge += warehouse.calculateClimbToLevel(0, rsl.getLevel()).charge;
    charge += warehouse.calculateStoreContainer().charge;
    charge += warehouse.calculateClimbToLevel(rsl.getLevel(), 0).charge;
    return charge;
  }
}
