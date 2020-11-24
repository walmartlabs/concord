package com.walmartlabs.concord.server.plugins.iamsso;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;

public final class SsoCookies {

    private static final String TOKEN_COOKIE = "ssoToken";
    private static final String POST_LOGIN_URL_COOKIE = "postLoginUrl";

    /**
     * Return the SSO token
     *
     * @param request from which SSO token cookie will get
     * @return SSO token or <code>null</code> if no SSO token cookie present
     */
    public static String getTokenCookie(HttpServletRequest request) {
        return getCookie(TOKEN_COOKIE, request);
    }

    /**
     * Store SSO token in response cookies
     *
     * @param token SSO token
     * @param expiresIn SSO token expiration time in seconds
     * @param response
     *
     */
    public static void addTokenCookie(String token, Integer expiresIn, HttpServletResponse response) {
        Cookie cookie = new Cookie(TOKEN_COOKIE, token);
        cookie.setMaxAge(expiresIn);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        
        response.addCookie(cookie);
    }

    /**
     * Remove SSO token cookie from response
     *
     * @param response response from which SSO token cookie will be removed
     */
    public static void removeTokenCookie(HttpServletResponse response) {
        remove(TOKEN_COOKIE, response);
    }

    /**
     * Store post successful redirect url in response cookies
     *
     * @param url url to be redirected to
     * @param response
     */
    public static void addFromCookie(String url, HttpServletResponse response) {
        Cookie cookie = new Cookie(POST_LOGIN_URL_COOKIE, url);
        cookie.setMaxAge(Integer.MAX_VALUE);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        response.addCookie(cookie);
    }

    /**
     * Return the url to be redirected post successful authentication by SSO
     *
     * @param request from which url cookie will get
     * @return redirect url or <code>null</code> if no redirect login cookie present
     */
    public static String getFromCookie(HttpServletRequest request) {
        return getCookie(POST_LOGIN_URL_COOKIE, request);
    }

    /**
     * Clear All Cookies related to SSO
     *
     * @param response response from which SSO cookies will be removed
     * @return response.
     */
    public static HttpServletResponse clear(HttpServletResponse response) {
        remove(TOKEN_COOKIE, response);
        remove(POST_LOGIN_URL_COOKIE, response);
        return response;
    }

    private static String getCookie(String name, HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        return Arrays.stream(cookies)
                .filter(c -> name.equals(c.getName()))
                .findFirst()
                .map(Cookie::getValue)
                .orElse(null);
    }

    private static void remove(String name, HttpServletResponse resp) {
        resp.addCookie(expiredCookie(name));
    }

    private static Cookie expiredCookie(String name) {
        Cookie cookie = new Cookie(name, "");
        cookie.setMaxAge(0);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        return cookie;
    }

    private SsoCookies() {
    }
}
