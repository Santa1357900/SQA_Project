package com.google.javascript.jscomp;

import org.junit.Test;
import static org.junit.Assert.*;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.BooleanLiteralSet;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.JSTypeNative;

import java.util.HashMap;

public class TypeInferenceTest {

  @Test
  public void testGetBooleanOutcomesNullAndBooleanLogic() throws Throwable {
    BooleanLiteralSet left = BooleanLiteralSet.TRUE;
    BooleanLiteralSet right = BooleanLiteralSet.FALSE;

    BooleanLiteralSet resultAnd = TypeInference.getBooleanOutcomes(left, right, true);
    assertNotNull(resultAnd);

    BooleanLiteralSet resultOr = TypeInference.getBooleanOutcomes(left, right, false);
    assertNotNull(resultOr);
  }

  @Test
  public void testConstantsAndStaticFields() throws Throwable {
    assertNotNull(TypeInference.TEMPLATE_TYPE_NOT_OBJECT_TYPE);
    assertNotNull(TypeInference.TEMPLATE_TYPE_OF_THIS_EXPECTED);
    assertNotNull(TypeInference.FUNCTION_LITERAL_UNDEFINED_THIS);
  }

  @Test
  public void testEdgeCasesBooleanOutcomes() throws Throwable {
    BooleanLiteralSet empty = BooleanLiteralSet.EMPTY;
    BooleanLiteralSet both = BooleanLiteralSet.BOTH;

    BooleanLiteralSet res1 = TypeInference.getBooleanOutcomes(empty, both, true);
    assertNotNull(res1);

    BooleanLiteralSet res2 = TypeInference.getBooleanOutcomes(both, empty, false);
    assertNotNull(res2);
  }
}