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
  private final Set<String> currentTurnStoreReservationCodes = new HashSet<>();

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
    currentTurnStoreReservationCodes.clear();
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

    // 2. Separate bots with containers and without
    List<AeroBot> botsWithContainer = new ArrayList<>();
    List<AeroBot> botsWithoutContainer = new ArrayList<>();
    for (AeroBot bot : idleBots) {
        if (bot.getCurrentCharge() < bot.getMaxCharge() * 0.3) {
            if (bot.getCurrentContainer() != null) {
                planStore(bot);
            } else {
                bot.planMoveToWaypoint(warehouse.getChargingArea());
                bot.planStartCharge();
            }
            continue;
        }

        if (bot.getCurrentContainer() != null) {
            botsWithContainer.add(bot);
        } else {
            botsWithoutContainer.add(bot);
        }
    }

    // 3. Match bots with containers FIRST (reuse)
    for (AeroBot bot : botsWithContainer) {
        Container current = bot.getCurrentContainer();
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
        } else if (!shouldWait(current.getProductCode())) {
            planStore(bot);
        }
    }

    // 4. Match bots without containers
    // First, globally greedy for pick area orders
    List<Order> unassignedPick = new ArrayList<>();
    for (Order o : warehouse.getPickArea().getCurrentOrders()) {
        if (!currentlyAssigned.contains(o.getSequence())) {
            unassignedPick.add(o);
        }
    }

    while (!botsWithoutContainer.isEmpty() && !unassignedPick.isEmpty()) {
        AeroBot bestBot = null;
        Order bestOrder = null;
        Assignment bestAssign = null;
        int minCost = Integer.MAX_VALUE;

        for (AeroBot bot : botsWithoutContainer) {
            for (Order order : unassignedPick) {
                Assignment bestForPair = findBestContainerForPair(bot, order);
                if (bestForPair != null && bestForPair.ticks < minCost) {
                    minCost = bestForPair.ticks;
                    bestBot = bot;
                    bestOrder = order;
                    bestAssign = bestForPair;
                }
            }
        }

        if (bestBot != null) {
            if (bestBot.getCurrentCharge() >= bestAssign.charge + 1000) {
                botAssignedOrder.put(bestBot, bestOrder.getSequence());
                botAssignedContainer.put(bestBot, bestAssign.container.getCode());
                botContainerHome.put(bestBot, bestAssign.rsl);
                currentlyAssigned.add(bestOrder.getSequence());

                bestBot.planMoveToWaypoint(bestAssign.rsl.getWaypoint());
                bestBot.planClimbToLevel(bestAssign.rsl.getLevel());
                bestBot.planLoadContainer(bestAssign.container);
                bestBot.planClimbToLevel(0);
                bestBot.planMoveToWaypoint(warehouse.getPickArea());
                bestBot.planPick(bestOrder);
            }
            botsWithoutContainer.remove(bestBot);
            unassignedPick.remove(bestOrder);
        } else {
            break; // No more possible assignments
        }
    }

    // Then, sequential greedy matching for future orders
    List<Order> unassignedFuture = new ArrayList<>();
    for (Order o : candidates) {
        if (!currentlyAssigned.contains(o.getSequence())) {
            // only add if not already handled
            boolean isPickArea = false;
            for (Order po : warehouse.getPickArea().getCurrentOrders()) {
                if (po.getSequence().equals(o.getSequence())) {
                    isPickArea = true; break;
                }
            }
            if (!isPickArea) {
                unassignedFuture.add(o);
            }
        }
    }

    while (!botsWithoutContainer.isEmpty() && !unassignedFuture.isEmpty()) {
        Order order = unassignedFuture.get(0);
        AeroBot bestBot = null;
        Assignment bestAssign = null;
        int minCost = Integer.MAX_VALUE;

        for (AeroBot bot : botsWithoutContainer) {
            Assignment bestForPair = findBestContainerForPair(bot, order);
            if (bestForPair != null && bestForPair.ticks < minCost) {
                minCost = bestForPair.ticks;
                bestBot = bot;
                bestAssign = bestForPair;
            }
        }

        if (bestBot != null) {
            if (bestBot.getCurrentCharge() >= bestAssign.charge + 1000) {
                botAssignedOrder.put(bestBot, order.getSequence());
                botAssignedContainer.put(bestBot, bestAssign.container.getCode());
                botContainerHome.put(bestBot, bestAssign.rsl);
                currentlyAssigned.add(order.getSequence());

                bestBot.planMoveToWaypoint(bestAssign.rsl.getWaypoint());
                bestBot.planClimbToLevel(bestAssign.rsl.getLevel());
                bestBot.planLoadContainer(bestAssign.container);
                bestBot.planClimbToLevel(0);
                bestBot.planMoveToWaypoint(warehouse.getPickArea());
                bestBot.planPick(order);
            }
            botsWithoutContainer.remove(bestBot);
        }
        unassignedFuture.remove(0);
    }
  }

  private static class Assignment {
      Rack.RackStorageLocation rsl;
      Container container;
      int charge;
      int ticks;
  }

  private Assignment findBestContainerForPair(AeroBot bot, Order order) {
      Collection<Container> containers = warehouse.findAvailableContainers(order.getProductCode());
      Assignment best = null;
      int minCost = Integer.MAX_VALUE;

      for (Container container : containers) {
          if (botAssignedContainer.containsValue(container.getCode())) continue;

          Location loc = container.getCurrentLocation();
          if (loc != null && loc.getType() == Location.Type.Rack) {
              Rack.RackStorageLocation rsl = (Rack.RackStorageLocation) loc;
              int ticks = estimateFetchPickTicks(bot, rsl, order, container);
              if (ticks < minCost) {
                  minCost = ticks;
                  if (best == null) best = new Assignment();
                  best.rsl = rsl;
                  best.container = container;
                  best.charge = estimateFetchPickCharge(bot, rsl, order, container);
                  best.ticks = ticks;
              }
          }
      }
      return best;
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
    Collection<Rack.RackStorageLocation> empty = warehouse.findEmptyRackStorageLocations();
    Rack.RackStorageLocation best = null;
    int bestScore = Integer.MAX_VALUE;

    for (Rack.RackStorageLocation rsl : empty) {
        if (currentTurnStoreReservationCodes.contains(rsl.getCode()) || warehouse.isReserved(rsl)) {
            continue;
        }
        int s = scoreLocation(rsl);
        if (s < bestScore) {
            bestScore = s;
            best = rsl;
        }
    }

    if (best != null) {
        currentTurnStoreReservationCodes.add(best.getCode());
        bot.planMoveToWaypoint(best.getWaypoint());
        bot.planClimbToLevel(best.getLevel());
        bot.planStoreContainer();
        bot.planClimbToLevel(0);
    }
    botContainerHome.remove(bot);
    botAssignedContainer.remove(bot);
  }

  private int scoreLocation(Rack.RackStorageLocation rsl) {
      if (rsl == null) return Integer.MAX_VALUE;
      int dist = rsl.getWaypoint().distance(warehouse.getPickArea());
      return dist * 5 + rsl.getLevel() * 16;
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

  private int estimateFetchPickTicks(AeroBot bot, Rack.RackStorageLocation rsl, Order order, Container container) {
    int ticks = 0;
    Waypoint botPos = bot.getCurrentWaypoint();
    ticks += warehouse.calculateMoveToWaypoint(botPos, rsl.getWaypoint()).ticks;
    ticks += warehouse.calculateClimbToLevel(0, rsl.getLevel()).ticks;
    ticks += warehouse.calculateLoadContainer(container).ticks;
    ticks += warehouse.calculateClimbToLevel(rsl.getLevel(), 0).ticks;
    ticks += warehouse.calculateMoveToWaypoint(rsl.getWaypoint(), warehouse.getPickArea()).ticks;
    ticks += warehouse.calculatePick(order).ticks;
    return ticks;
  }
}
