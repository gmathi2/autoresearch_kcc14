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
  private final Map<AeroBot, Rack.RackStorageLocation> botContainerHome = new HashMap<>();

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
    // 1. Identify idle bots and candidates
    List<AeroBot> idleBots = new ArrayList<>();
    for (AeroBot bot : warehouse.getAllAeroBots()) {
        if (bot.getOpenOperations().isEmpty()) {
            botAssignedOrder.remove(bot);
            idleBots.add(bot);
        }
    }

    if (idleBots.isEmpty()) return;

    Set<Integer> currentlyAssigned = new HashSet<>(botAssignedOrder.values());
    List<Order> candidates = getCandidates(currentlyAssigned);

    // 2. Handle idle bots
    for (AeroBot bot : idleBots) {
        // Charging
        if (bot.getCurrentCharge() < bot.getMaxCharge() * 0.3) {
            if (bot.getCurrentContainer() != null) {
                planStore(bot);
            } else {
                bot.planMoveToWaypoint(warehouse.getChargingArea());
                bot.planStartCharge();
            }
            continue;
        }

        // Reuse?
        Container current = bot.getCurrentContainer();
        if (current != null) {
            Order matched = null;
            for (Order o : candidates) {
                if (o.getProductCode().equals(current.getProductCode()) && !currentlyAssigned.contains(o.getSequence())) {
                    matched = o;
                    break;
                }
            }
            if (matched != null) {
                botAssignedOrder.put(bot, matched.getSequence());
                currentlyAssigned.add(matched.getSequence());
                bot.planPick(matched);
                continue;
            } else if (!shouldWait(current.getProductCode())) {
                planStore(bot);
                // After storing, bot will be idle next call
                continue;
            } else {
                // Wait at pick area (or wherever we are)
                continue;
            }
        }

        // Assign new
        for (Order order : candidates) {
            if (currentlyAssigned.contains(order.getSequence())) continue;
            
            if (tryAssignSpecificOrder(bot, order, currentlyAssigned)) {
                break;
            }
        }
    }
  }

  private List<Order> getCandidates(Set<Integer> currentlyAssigned) {
      List<Order> candidates = new ArrayList<>(warehouse.getPickArea().getCurrentOrders());
      List<Order> open = warehouse.getOpenOrders();
      for (Order o : open) {
          if (candidates.size() >= 50) break;
          boolean already = false;
          for (Order c : candidates) {
              if (c.getSequence().equals(o.getSequence())) {
                  already = true;
                  break;
              }
          }
          if (!already) candidates.add(o);
      }
      return candidates;
  }

  private void planStore(AeroBot bot) {
    Rack.RackStorageLocation rsl = botContainerHome.get(bot);
    if (rsl == null) {
        Collection<Rack.RackStorageLocation> empty = warehouse.findEmptyRackStorageLocations();
        if (!empty.isEmpty()) rsl = empty.iterator().next();
    }
    if (rsl != null) {
        bot.planMoveToWaypoint(rsl.getWaypoint());
        bot.planClimbToLevel(rsl.getLevel());
        bot.planStoreContainer();
        bot.planClimbToLevel(0);
    }
    botContainerHome.remove(bot);
    botAssignedContainer.remove(bot);
  }

  private boolean tryAssignSpecificOrder(AeroBot bot, Order order, Set<Integer> currentlyAssigned) {
    Collection<Container> containers = warehouse.findAvailableContainers(order.getProductCode());
    Rack.RackStorageLocation bestRsl = null;
    Container bestContainer = null;
    int minCharge = Integer.MAX_VALUE;

    for (Container container : containers) {
      if (botAssignedContainer.containsValue(container.getCode())) continue;

      Location loc = container.getCurrentLocation();
      if (loc != null && loc.getType() == Location.Type.Rack) {
        Rack.RackStorageLocation rsl = (Rack.RackStorageLocation) loc;

        int estimatedCharge = estimateFetchPickCharge(bot, rsl, order, container);
        if (estimatedCharge < minCharge) {
            minCharge = estimatedCharge;
            bestRsl = rsl;
            bestContainer = container;
        }
      }
    }

    if (bestContainer != null) {
        if (bot.getCurrentCharge() < minCharge + 1000) return false;

        botAssignedOrder.put(bot, order.getSequence());
        botAssignedContainer.put(bot, bestContainer.getCode());
        botContainerHome.put(bot, bestRsl);
        currentlyAssigned.add(order.getSequence());

        bot.planMoveToWaypoint(bestRsl.getWaypoint());
        bot.planClimbToLevel(bestRsl.getLevel());
        bot.planLoadContainer(bestContainer);
        bot.planClimbToLevel(0);
        bot.planMoveToWaypoint(warehouse.getPickArea());
        bot.planPick(order);
        return true;
    }
    return false;
  }

  private boolean shouldWait(String productCode) {
    List<Order> open = warehouse.getOpenOrders();
    int limit = Math.min(open.size(), 30); // only wait if needed very soon
    for (int i = 0; i < limit; i++) {
        if (open.get(i).getProductCode().equals(productCode)) return true;
    }
    return false;
  }

  private int estimateFetchPickCharge(AeroBot bot, Rack.RackStorageLocation rsl, Order order, Container container) {
    int charge = 0;
    Waypoint botPos = bot.getCurrentWaypoint();
    charge += warehouse.calculateMoveToWaypoint(botPos, rsl.getWaypoint()).charge;
    charge += warehouse.calculateClimbToLevel(0, rsl.getLevel()).charge;
    charge += warehouse.calculateLoadContainer(container).charge;
    charge += warehouse.calculateClimbToLevel(rsl.getLevel(), 0).charge;
    charge += warehouse.calculateMoveToWaypoint(rsl.getWaypoint(), warehouse.getPickArea()).charge;
    charge += warehouse.calculatePick(order).charge;
    return charge;
  }
}
