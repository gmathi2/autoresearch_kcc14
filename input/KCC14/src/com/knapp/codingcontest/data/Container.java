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

package com.knapp.codingcontest.data;

/**
 * Represents a physical container that holds a single product with unlimited quantity.
 *
 * <p>Containers are stored in {@link Rack.RackStorageLocation}s and can be loaded
 * onto an {@code AeroBot} for transport to the picking station.
 *
 * <p>This class is read-only from the solution's perspective &mdash;
 * state changes happen implicitly through {@code plan...} methods on the {@code AeroBot}.
 */
public abstract class Container {
  /** Unique identifier for this container. */
  private final String code;
  /** Product code of the product stored in this container. */
  private final String productCode;

  // ----------------------------------------------------------------------------

  protected Container(final String code, final String productCode) {
    this.code = code;
    this.productCode = productCode;
  }

  // ----------------------------------------------------------------------------

  /**
   * @return the unique identifier of this container
   */
  public String getCode() {
    return code;
  }

  /**
   * @return the product code of the product stored in this container
   */
  public String getProductCode() {
    return productCode;
  }

  /**
   * Returns the current location of this container.
   *
   * <p>The location is either a {@link Rack.RackStorageLocation} (when stored in a rack)
   * or an {@code AeroBot}-location (when loaded onto a bot).
   *
   * @return the current {@link Location} of this container
   */
  public abstract Location getCurrentLocation();

  // ----------------------------------------------------------------------------
  // ----------------------------------------------------------------------------
}
