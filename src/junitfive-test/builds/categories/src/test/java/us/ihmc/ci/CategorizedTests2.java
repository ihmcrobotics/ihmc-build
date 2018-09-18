package us.ihmc.ci;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class CategorizedTests2
{
   @Tag("fast")
   @Test
   public void fastTest() throws InterruptedException
   {
   }

   @Test
   public void untaggedTest() throws InterruptedException
   {
   }

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
