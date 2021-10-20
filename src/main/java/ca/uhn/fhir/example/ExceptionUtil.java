package ca.uhn.fhir.example;

import com.google.common.base.Preconditions;
import java.lang.reflect.InvocationTargetException;
import javax.annotation.Nullable;
import org.slf4j.Logger;

public class ExceptionUtil {

   static <T extends RuntimeException> void throwRuntimeExceptionAndLog(Logger logger,
      String errorMessage, @Nullable Exception origException, Class<T> runTimeExceptionClass) {
      Preconditions.checkNotNull(runTimeExceptionClass);
      if (origException == null) {
         logger.error(errorMessage);
      } else {
         // logging the stack trace too
         logger.error(errorMessage, origException);
      }
      try {
         throw runTimeExceptionClass.getDeclaredConstructor(String.class).newInstance(errorMessage);
      } catch (InstantiationException | IllegalAccessException | NoSuchMethodException |
         InvocationTargetException e) {
         throw new RuntimeException(errorMessage);
      }
   }

   static <T extends RuntimeException> void throwRuntimeExceptionAndLog(Logger logger,
      String errorMessage, Class<T> runTimeExceptionClass) {
      throwRuntimeExceptionAndLog(logger, errorMessage, null, runTimeExceptionClass);
   }

   static void throwRuntimeExceptionAndLog(Logger logger, String errorMessage) {
      throwRuntimeExceptionAndLog(logger, errorMessage, null, RuntimeException.class);
   }

   static void throwRuntimeExceptionAndLog(Logger logger, String errorMessage, Exception e) {
      throwRuntimeExceptionAndLog(logger, errorMessage, e, RuntimeException.class);
   }

   static void throwRuntimeExceptionAndLog(Logger logger, Exception e) {
      throwRuntimeExceptionAndLog(logger, e.getMessage(), e, RuntimeException.class);
   }

}
