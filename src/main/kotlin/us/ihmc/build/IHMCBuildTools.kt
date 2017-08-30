package us.ihmc.build

import org.apache.commons.lang3.StringUtils
import java.util.ArrayList

fun toPascalCased(hyphenated: String): String
{
   val split = hyphenated.split("-")
   var pascalCased = ""
   for (section in split)
   {
      pascalCased += StringUtils.capitalize(section)
   }
   return pascalCased
}

fun toHyphenated(pascalCased: String): String
{
   var hyphenated = pascalCasedToPrehyphenated(pascalCased);
   
   hyphenated = hyphenated.substring(1, hyphenated.length - 1);
   
   return hyphenated;
}

fun pascalCasedToPrehyphenated(pascalCased: String): String
{
   val parts = ArrayList<String>();
   var part = "";
   
   for (i in 0..pascalCased.length)
   {
      var character = pascalCased[i].toString();
      if (StringUtils.isAllUpperCase(character) || StringUtils.isNumeric(character))
      {
         if (!part.isEmpty())
         {
            parts.add(part.toLowerCase());
         }
         part = character;
      }
      else
      {
         part += character;
      }
   }
   if (!part.isEmpty())
   {
      parts.add(part.toLowerCase());
   }
   
   var hyphenated = "";
   for (i in 0..parts.size)
   {
      hyphenated += '-';
      hyphenated += parts.get(i);
   }
   hyphenated += '-';
   
   return hyphenated;
}