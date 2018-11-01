package us.ihmc.ci;

import org.junit.jupiter.api.Test;

public class CategorizedExtendingTest extends CategorizedAbstractTest
{
   @Test
   public void imAndExtendingTest()
   {
      super.imAnAbstractTest();
   }
}
