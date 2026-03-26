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

package com.knapp.codingcontest.operations.ex;

import com.knapp.codingcontest.operations.AeroBot;
import com.knapp.codingcontest.operations.Operation;

/**
 * Thrown when an AeroBot attempts to pick an order but is not located at the picking station.
 */
public class AeroBotNotAtPickingStationsException extends AbstractWarehouseException {
  private static final long serialVersionUID = 1L;

  // ----------------------------------------------------------------------------

  public AeroBotNotAtPickingStationsException(final AeroBot aeroBot, final Operation.AeroBotPick op) {
    super(aeroBot.getCode() + ":" + op.toResultString());
  }

  // ----------------------------------------------------------------------------
}
