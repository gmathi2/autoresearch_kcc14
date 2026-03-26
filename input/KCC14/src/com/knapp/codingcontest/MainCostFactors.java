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

import com.knapp.codingcontest.operations.CostFactors;

public class MainCostFactors implements CostFactors {
  @Override
  public double getUnfinishedOrderCost() {
    return 500.0;
  }

  @Override
  public double getCostPerTick() {
    return 1.0;
  }
}
