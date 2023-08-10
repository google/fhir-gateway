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
package com.google.fhir.gateway.util;

public interface Constants {
  String SLASH_UNDERSCORE = "/_";
  String LOCATION = "Location";
  String FORWARD_SLASH = "/";
  String IDENTIFIER = "identifier";
  String LOCATION_RESOURCE_NOT_FOUND = "Location Resource : Not Found";
  String LOCATION_RESOURCE = "Location Resource : ";
  String PART_OF = "partof";
  String KEYCLOAK_UUID = "keycloak-uuid";
  String PRACTITIONER = "practitioner";
  String PARTICIPANT = "participant";
  String KEYCLOAK_USER_NOT_FOUND = "Keycloak User Not Found";
  String PRACTITIONER_NOT_FOUND = "Practitioner Not Found";
  String PRIMARY_ORGANIZATION = "primary-organization";
  String ID = "_id";
  String PREFFERED_USERNAME = "Preferred Username";
  String USERNAME = "Username";
  String FAMILY_NAME = "Family Name";
  String GIVEN_NAME = "Given Name";
  String EMAIL = "Email";
  String EMAIL_VERIFIED = "Email verified";
  String ROLE = "Role";
  String COLON = ":";
  String SPACE = " ";
  String EMPTY_STRING = "";
  String _PRACTITIONER = "Practitioner";
  String PRACTITIONER_ROLE = "PractitionerRole";
  String CARE_TEAM = "CareTeam";
  String ORGANIZATION = "Organization";
  String ORGANIZATION_AFFILIATION = "OrganizationAffiliation";
  String CODE = "code";
  String MEMBER = "member";
  String GROUP = "Group";
  String PROXY_TO_ENV = "PROXY_TO";
  String PRACTITIONER_GROUP_CODE = "405623001";
  String HTTP_SNOMED_INFO_SCT = "http://snomed.info/sct";

  String PRACTITIONER_DETAILS = "PractitionerDetails";

  String LOCATION_HIERARCHY = "LocationHierarchy";

  String PRACTITONER_RESOURCE_PATH = "Practitioner";
  String QUESTION_MARK = "?";

  String EQUALS_TO_SIGN = "=";
  String HTTP_GET_METHOD = "GET";
}
