package us.ihmc;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.time.Duration;

public class ParallelExecution1
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

   @ResourceLock(value = "File.txt", mode = ResourceAccessMode.READ_WRITE)
   @Execution(ExecutionMode.SAME_THREAD)
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
