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

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.knapp.codingcontest.core.OperationInternal.AeroBotMoveV;
import com.knapp.codingcontest.core.OperationInternal.FinStatus;
import com.knapp.codingcontest.data.Rack;

public class RackInternal extends AbstractWaypoint implements Rack {
  private final Map<String, AeroBotInternal> base = new LinkedHashMap<>();
  private final LinkedList<Map.Entry<String, LinkedList<OperationInternal.AeroBotMoveV>>> plannedOpGroups = new LinkedList<>();

  private final List<RackStorageLocationInternal> storage;
  private final String code;

  // ----------------------------------------------------------------------------

  public RackInternal(final String code, final int x, final int y, final int nrOfLevels) {
    super(Type.Rack, code , x, y);
    this.code = code;
    storage = new ArrayList<>(nrOfLevels);
    for (int i = 0; i < nrOfLevels; i++) {
      storage.add(new RackStorageLocationInternal(this, i + 1));
    }
  }

  // ----------------------------------------------------------------------------

  @Override
  public List<RackStorageLocation> getRackStorageLocations() {
    return Collections.unmodifiableList(storage.stream().collect(Collectors.toList()));
  }

  @Override
  public RackStorageLocationInternal getRackStorageLocation(final int level) {
    if (level == 0) {
      return null;
    }
    return storage.get(level - 1);
  }

  @Override
  public int getMaxLevel() {
    return storage.size();
  }

  // ----------------------------------------------------------------------------

  void plannedAdd(final OperationInternal.AeroBotMoveV op) {
    final Iterator<Entry<String, LinkedList<AeroBotMoveV>>> it = plannedOpGroups.descendingIterator();
    while (it.hasNext()) {
      final Entry<String, LinkedList<AeroBotMoveV>> e = it.next();
      if (e.getKey().equals(op.aeroBot.getCode())) {
        final LinkedList<AeroBotMoveV> aops = e.getValue();
        if ((aops.peekLast().to > 0) || (op.to == 0)) {
          aops.addLast(op);
          return;
        }
      }
    }
    final LinkedList<AeroBotMoveV> aops = new LinkedList<>();
    aops.add(op);
    plannedOpGroups.addLast(new AbstractMap.SimpleEntry<>(op.aeroBot.getCode(), aops));
  }

  void plannedRemove(final OperationInternal.AeroBotMoveV op) {
    final Map.Entry<String, LinkedList<AeroBotMoveV>> e = plannedOpGroups.peekFirst();
    if (!e.getKey().equals(op.aeroBot.getCode())) {
      throw new IllegalStateException("Oops - wrong sequence");
    }
    final LinkedList<AeroBotMoveV> aops = e.getValue();
    final Iterator<AeroBotMoveV> it = aops.descendingIterator();
    CHECK: {
      while (it.hasNext()) {
        final AeroBotMoveV op0 = it.next();
        if (op0.checkFinished() == FinStatus.Finished) {
          if (op0 != op) {
            throw new IllegalStateException("Oops - wrong sequence");
          }
          break CHECK;
        }
      }
    }
    if (areAllFinished(aops) && (aops.peekLast().to == 0)) {
      plannedOpGroups.removeFirst();
    }
  }

  private boolean areAllFinished(final LinkedList<? extends OperationInternal> ops) {
    return !ops.stream().anyMatch(o -> o.checkFinished() != FinStatus.Finished);
  }

  @Override
  public void enter(final AeroBotInternal aeroBot) {
    super.enter(aeroBot);
    base.put(aeroBot.getCode(), aeroBot);
  }

  @Override
  public void leave(final AeroBotInternal aeroBot) {
    base.remove(aeroBot.getCode());
    super.leave(aeroBot);
  }

  // ----------------------------------------------------------------------------

  boolean inSequenceToMoveV(final AeroBotInternal aeroBot) {
    final Map.Entry<String, LinkedList<AeroBotMoveV>> e = plannedOpGroups.peekFirst();
    return e.getKey().equals(aeroBot.getCode());
  }

  // ----------------------------------------------------------------------------

  public static class RackStorageLocationInternal extends RackStorageLocation {
    private ContainerInternal container;

    @SuppressWarnings("boxing")
    private RackStorageLocationInternal(final RackInternal rack, final int level) {
      super(rack, String.format("%s+%02d", rack.code, level), level);
    }

    @Override
    public ContainerInternal peekContainer() {
      return container;
    }

    void pushContainer(final ContainerInternal container) {
      this.container = container;
      container.setCurrentLocation(this);
    }

    ContainerInternal pullContainer() {
      final ContainerInternal container_ = container;
      container = null;
      container_.setCurrentLocation(null);
      return container_;
    }
  }

  // ----------------------------------------------------------------------------
  // ----------------------------------------------------------------------------
}
