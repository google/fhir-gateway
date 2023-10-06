/*
 * Copyright 2021-2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.fhir.gateway;

import com.google.common.base.Preconditions;
import java.lang.reflect.InvocationTargetException;
import javax.annotation.Nullable;
import org.slf4j.Logger;

public class ExceptionUtil {

  static <T extends RuntimeException> void throwRuntimeExceptionAndLog(
      Logger logger,
      String errorMessage,
      @Nullable Exception origException,
      Class<T> runTimeExceptionClass) {
    Preconditions.checkNotNull(runTimeExceptionClass);
    // logging the error message followed by the stack trace.
    if (origException == null) {
      logger.error(errorMessage, new Exception("stack-trace"));
    } else {
      logger.error(errorMessage, origException);
    }
    try {
      throw runTimeExceptionClass.getDeclaredConstructor(String.class).newInstance(errorMessage);
    } catch (InstantiationException
        | IllegalAccessException
        | NoSuchMethodException
        | InvocationTargetException e) {
      throw new RuntimeException(errorMessage);
    }
  }

  static <T extends RuntimeException> void throwRuntimeExceptionAndLog(
      Logger logger, String errorMessage, Class<T> runTimeExceptionClass) {
    throwRuntimeExceptionAndLog(logger, errorMessage, null, runTimeExceptionClass);
  }

  static void throwRuntimeExceptionAndLog(Logger logger, String errorMessage) {
    throwRuntimeExceptionAndLog(logger, errorMessage, null, RuntimeException.class);
  }

  public static void throwRuntimeExceptionAndLog(Logger logger, String errorMessage, Exception e) {
    throwRuntimeExceptionAndLog(logger, errorMessage, e, RuntimeException.class);
  }
}
