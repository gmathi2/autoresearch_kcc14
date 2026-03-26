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

import com.knapp.codingcontest.data.Container;
import com.knapp.codingcontest.data.Location;

public class ContainerInternal extends Container {
  private Location location;

  // ----------------------------------------------------------------------------

  public ContainerInternal(final String code, final String productCode) {
    super(code, productCode);
  }

  // ----------------------------------------------------------------------------

  @Override
  public String toString() {
    return "Cont#" + getCode() + "[product=" + getProductCode() + "]{location=" + location.getCode() + "}";
  }

  // ----------------------------------------------------------------------------

  public void setCurrentLocation(final Location location) {
    this.location = location;
  }

  @Override
  public Location getCurrentLocation() {
    return location;
  }

  // ----------------------------------------------------------------------------
  // ----------------------------------------------------------------------------
}
