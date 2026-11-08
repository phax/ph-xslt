/*
 * Copyright (C) 2026 Philip Helger (www.helger.com)
 * philip[at]helger[dot]com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.helger.xpath.value;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

/**
 * Test class for class {@link EAtomicType}.
 *
 * @author Philip Helger
 */
public final class EAtomicTypeTest
{
  /** The walk up the parent chain that isDerivedFrom used before its bits. */
  private static boolean _isDerivedFromByWalk (@NonNull final EAtomicType eType, @NonNull final EAtomicType eOther)
  {
    if (eOther == EAtomicType.NUMERIC)
      return _isNumericByWalk (eType);
    for (EAtomicType e = eType; e != null; e = e.getParent ())
      if (e == eOther)
        return true;
    return false;
  }

  private static boolean _isNumericByWalk (@NonNull final EAtomicType eType)
  {
    return eType == EAtomicType.DOUBLE ||
           eType == EAtomicType.FLOAT ||
           (_isDerivedFromByWalk (eType, EAtomicType.DECIMAL) && eType != EAtomicType.NUMERIC);
  }

  @Test
  public void testIsDerivedFromAllPairs ()
  {
    for (final EAtomicType eType : EAtomicType.values ())
    {
      assertEquals (eType.name (), Boolean.valueOf (_isNumericByWalk (eType)), Boolean.valueOf (eType.isNumeric ()));
      for (final EAtomicType eOther : EAtomicType.values ())
        assertEquals (eType + " / " + eOther,
                      Boolean.valueOf (_isDerivedFromByWalk (eType, eOther)),
                      Boolean.valueOf (eType.isDerivedFrom (eOther)));
    }
  }

  @Test
  public void testSamples ()
  {
    assertTrue (EAtomicType.BYTE.isDerivedFrom (EAtomicType.DECIMAL));
    assertTrue (EAtomicType.BYTE.isDerivedFrom (EAtomicType.NUMERIC));
    assertTrue (EAtomicType.FLOAT.isDerivedFrom (EAtomicType.NUMERIC));
    assertTrue (EAtomicType.ID.isDerivedFrom (EAtomicType.ANY_ATOMIC_TYPE));
    assertFalse (EAtomicType.NUMERIC.isDerivedFrom (EAtomicType.NUMERIC));
    assertFalse (EAtomicType.NUMERIC.isNumeric ());
    assertFalse (EAtomicType.FLOAT.isDerivedFrom (EAtomicType.DOUBLE));
    assertFalse (EAtomicType.UNTYPED_ATOMIC.isDerivedFrom (EAtomicType.STRING));
    assertTrue (EAtomicType.DATE_TIME_STAMP.isDateTimeFamily ());
  }
}
