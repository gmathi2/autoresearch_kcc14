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
 * Represents a pick order &mdash; a request to pick a specific product.
 *
 * <p>Orders are fulfilled by planning a {@code planPick(Order)} operation
 * on an {@code AeroBot} that is at the picking area and has a container
 * with the requested product loaded.
 *
 * <p>This class is read-only from the solution's perspective.
 */
public class Order {
  /** A number defining the ascending sequence in which this order was placed. */
  private final Integer sequence;
  /** The product code that needs to be picked (Note: quantity does not matter). */
  private final String productCode;

  protected Order(final Integer sequence, final String productCode) {
    this.sequence = sequence;
    this.productCode = productCode;
  }

  /**
   * @return the sequence number of this order
   */
  public Integer getSequence() {
    return sequence;
  }

  /**
   * @return the product code that needs to be picked to fulfil this order
   */
  public String getProductCode() {
    return productCode;
  }

  @Override
  public String toString() {
    return "Order[" + sequence + "," + productCode + "]";
  }
}
