/*
 * Copyright 2021-2024 Google LLC
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

// TODO change this test to fail if the expected plugins cannot be found.

// TODO uncomment this test possibly with adding the option of passing
// TOKEN_ISSUER name through system properties (in addition to env vars).
// Currently in our e2e tests, we verify that the sample app can start with
// proper TOKEN_ISSUER env var. The behaviour of this test has changed in
// recent versions of Spring and that's why it is commented out temporarily.
//
// @RunWith(SpringRunner.class)
// @SpringBootTest
// public class MainAppTest {
//
//
//   @Test
//   public void contextLoads() {
//   }
// }
