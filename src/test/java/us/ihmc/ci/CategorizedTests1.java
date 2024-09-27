package us.ihmc.ci;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

public class CategorizedTests1
{
   @Tag("fast")
   @Test
   public void fastTest() throws InterruptedException
   {
   }

   @Test
   public void untaggedTest()
   {

   }

   @Disabled
   @Tag("failing")
   @Test
   public void failingTest() throws InterruptedException
   {
      Assertions.fail();
   }

   @ResourceLock(value = "File.txt", mode = ResourceAccessMode.READ_WRITE)
   @Execution(ExecutionMode.SAME_THREAD)
   @Tag("slow")
   @Test
   public void slowTest() throws InterruptedException
   {
   }

   @Tag("fast")
   @Tag("needs-gpu")
   @Test
   public void gpuTest() throws InterruptedException
   {
   }
}
