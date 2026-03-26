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

package com.knapp.codingcontest.operations;

import com.knapp.codingcontest.data.Order;
import com.knapp.codingcontest.data.Waypoint;

/**
 * Represents a single planned or executing operation of an {@link AeroBot}.
 *
 * <p>Operations are created via the {@code plan...} methods on {@link AeroBot}
 * and executed during warehouse tick advancement.
 * 
 * <p>The cost of an operation can be estimated using the corresponding {@code calculate...} methods
 * on {@link Warehouse}. This can be used for optimizations by planning and ranking operations.
 *
 * <p>Sub-interfaces identify the specific operation type and may provide
 * additional context (e.g. target waypoint, target level, order).
 *
 * @see AeroBot#getOpenOperations()
 */
public interface Operation {
  // ----------------------------------------------------------------------------

  /**
   * @return the {@link AeroBot.OperationMode} of this operation
   */
  AeroBot.OperationMode getMode();

  /**
   * @return the {@link AeroBot} that owns this operation
   */
  AeroBot getAeroBot();

  // ----------------------------------------------------------------------------

  /**
   * Returns a string representation suitable for result output.
   *
   * @return a formatted result string
   */
  String toResultString();

  // ----------------------------------------------------------------------------

  /** Operation: AeroBot parks at the parking area. */
  interface AeroBotPark extends Operation {
  }

  /** Operation: AeroBot moves horizontally to a target waypoint. */
  interface AeroBotMoveH extends Operation {
    /**
     * @return the target {@link Waypoint}
     */
    Waypoint getTo();
  }

  /** Operation: AeroBot climbs vertically to a target level at a rack. */
  interface AeroBotMoveV extends Operation {
    /**
     * @return the target level
     */
    int getTo();
  }

  /** Operation: AeroBot loads a container from the current rack location. */
  interface AeroBotLoad extends Operation {
  }

  /** Operation: AeroBot picks an order at the picking area. */
  interface AeroBotPick extends Operation {
    /**
     * @return the {@link Order} being picked
     */
    Order getOrder();
  }

  /** Operation: AeroBot stores its container into the current rack location. */
  interface AeroBotStore extends Operation {
  }

  /** Operation: AeroBot charges at the charging area. */
  interface AeroBotCharge extends Operation {
  }

  // ----------------------------------------------------------------------------
  // ----------------------------------------------------------------------------
}
