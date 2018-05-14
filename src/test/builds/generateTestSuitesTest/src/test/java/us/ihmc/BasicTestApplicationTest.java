package us.ihmc;

import org.junit.Test;
import us.ihmc.continuousIntegration.ContinuousIntegrationAnnotations.ContinuousIntegrationTest;

public class BasicTestApplicationTest
{
   @ContinuousIntegrationTest(estimatedDuration = 10)
   @Test(timeout = 30000)
   public void testThings()
   {
      System.out.println("Hello there. I'm some test code.");
   }
}
