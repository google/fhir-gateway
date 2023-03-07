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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.google.fhir.gateway.interfaces.BundleProcessingWorker;
import com.google.fhir.gateway.interfaces.RequestDetailsReader;
import java.nio.charset.StandardCharsets;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BundleProcessorUtilsTest {

  protected static final FhirContext fhirContext = FhirContext.forR4();

  protected static final IParser parser = fhirContext.newJsonParser();
  protected static final BundleProcessorUtils bundleProcessorUtils =
      new BundleProcessorUtils(fhirContext);

  @Mock protected RequestDetailsReader requestMock;

  @Test
  public void allBundleEntriesProcessed() {
    int totalBundleEntries = 10;
    Bundle testBundle = new Bundle();
    testBundle.setType(Bundle.BundleType.TRANSACTION);
    for (int i = 0; i < totalBundleEntries; i++) {
      testBundle.addEntry(mock(Bundle.BundleEntryComponent.class));
    }
    TestBundleProcessingWorker worker = new TestBundleProcessingWorker(totalBundleEntries);
    mockRequestDetailsForBundle(testBundle);
    bundleProcessorUtils.processBundleFromRequest(requestMock, worker);
    assertThat(worker.getTotalEntriesProcessed(), equalTo(totalBundleEntries));
  }

  @Test
  public void processingEarlyExit() {
    int totalBundleEntries = 10;
    int bundlesToProcess = 5;
    Bundle testBundle = new Bundle();
    testBundle.setType(Bundle.BundleType.TRANSACTION);
    for (int i = 0; i < totalBundleEntries; i++) {
      testBundle.addEntry(mock(Bundle.BundleEntryComponent.class));
    }
    TestBundleProcessingWorker worker = new TestBundleProcessingWorker(bundlesToProcess);
    mockRequestDetailsForBundle(testBundle);
    bundleProcessorUtils.processBundleFromRequest(requestMock, worker);
    assertThat(worker.getTotalEntriesProcessed(), equalTo(bundlesToProcess));
  }

  private void mockRequestDetailsForBundle(Bundle bundle) {
    String bundleJson = parser.encodeResourceToString(bundle);
    when(requestMock.loadRequestContents()).thenReturn(bundleJson.getBytes(StandardCharsets.UTF_8));
  }

  static class TestBundleProcessingWorker implements BundleProcessingWorker {

    private int earlyExitAtBundleEntryNumber;
    private int entriesProcessed;

    TestBundleProcessingWorker(int earlyExitAtBundleEntryNumber) {
      this.entriesProcessed = 0;
      this.earlyExitAtBundleEntryNumber = earlyExitAtBundleEntryNumber;
    }

    @Override
    public void processBundleEntryComponent(Bundle.BundleEntryComponent bundleEntryComponent) {
      this.entriesProcessed = this.entriesProcessed + 1;
    }

    @Override
    public boolean processNextBundleEntry() {
      if (entriesProcessed == earlyExitAtBundleEntryNumber) {
        return false;
      }
      return true;
    }

    public int getTotalEntriesProcessed() {
      return entriesProcessed;
    }
  }
}
