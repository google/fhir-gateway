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

import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.fhir.gateway.interfaces.RequestDetailsReader;
import org.apache.http.HttpHeaders;

public class JwtUtil {
  public static final String CLAIM_NAME = "name";
  public static final String CLAIM_IHE_IUA_SUBJECT_NAME = "subject_name";
  public static final String CLAIM_IHE_IUA_CLIENT_ID = "client_id";
  public static final String CLAIM_SUBJECT = "sub";
  public static final String CLAIM_JWT_ID = "jti";
  public static final String CLAIM_ISSUER = "iss";
  public static final String CLAIM_AZP = "azp";

  public static String getClaimOrDie(DecodedJWT jwt, String claimName) {
    Claim claim = jwt.getClaim(claimName);
    if (claim.asString() == null) {
      throw new AuthenticationException(
          String.format("The provided token has no %s claim!", claimName));
    }
    return claim.asString();
  }

  public static String getClaimOrDefault(DecodedJWT jwt, String claimName, String defaultValue) {
    String claim;
    try {
      claim = JwtUtil.getClaimOrDie(jwt, claimName);
    } catch (JWTDecodeException | AuthenticationException e) {
      claim = defaultValue;
    }
    return claim;
  }

  public static DecodedJWT getDecodedJwtFromRequestDetails(RequestDetailsReader requestDetails) {
    if (requestDetails == null) return null;
    String authHeader = requestDetails.getHeader(HttpHeaders.AUTHORIZATION);
    String bearerToken = authHeader.substring(TokenVerifier.BEARER_PREFIX.length());
    return JWT.decode(bearerToken);
  }
}
