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
 * Enumeration of participating institutes (schools and universities) in the KNAPP Coding Contest.
 *
 * <p>Used by {@link com.knapp.codingcontest.solution.Solution#getParticipantName()} and
 * {@link com.knapp.codingcontest.solution.Solution#getInstitute()} to identify participants.
 */
public enum Institute {
  // HTLs & Schulen
  HAK_Feldbach, //
  HTBLA_Kaindorf_Sulm, //
  HTL_Bulme_Graz, //
  HTL_Leoben, //
  HTL_Pinkafeld, //
  HTL_Rennweg_Wien, //
  HTL_Villach, //
  HTL_Weiz, //
  HTL_Wien_West, //
  Sacre_Coeur_Graz, //

  SonstigeSchule, //

  // UNIs, FHs & Sonstige
  FH_Campus_02, //
  FH_Joanneum, //
  Johannes_Kepler_Universitaet_Linz, //
  TU_Graz, //
  TU_Wien, //

  Sonstige, //

  // while shown in report, group does not take part in the competition
  __REFERENCE__, // (reference solutions from project team)
  _Knapp_, // (KNAPP internals, test-runs)
  //
  __NonCompetitive_, // others not competing ...

  HTL_Kaindorf, //
  HTBLuVA_Villach, //
  ;

  // ----------------------------------------------------------------------------

  /**
   * Looks up an institute by its enum constant name.
   *
   * @param _institute the exact name of the institute constant
   * @return the matching {@link Institute}
   * @throws IllegalArgumentException if no constant with the given name exists
   */
  public static Institute find(final String _institute) {
    return Institute.valueOf(_institute);
  }

  // ----------------------------------------------------------------------------
}
