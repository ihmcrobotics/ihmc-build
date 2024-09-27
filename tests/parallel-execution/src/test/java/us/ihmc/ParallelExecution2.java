package us.ihmc;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class ParallelExecution2
{
   @Tag("fast")
   @Test
   public void fastTest() throws InterruptedException
   {
      Thread.sleep(5000);
   }

   @Test
   public void untaggedTest() throws InterruptedException
   {
      Thread.sleep(5000);
   }

   @Tag("slow")
   @Test
   public void slowTest() throws InterruptedException
   {
      Thread.sleep(5000);
   }

   @Tag("fast")
   @Tag("needs-gpu")
   @Test
   public void gpuTest() throws InterruptedException
   {
      Thread.sleep(5000);
   }
}
