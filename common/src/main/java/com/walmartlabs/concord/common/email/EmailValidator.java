package com.walmartlabs.concord.common.email;

/*
 * Adapted from Apache Commons Validator.
 * Copyright 2002-2025 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at https://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.IDN;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default-only email validation compatible with Apache Commons Validator 1.10.0.
 * Local-part and IP rules and the packaged TLD data are derived from that
 * Apache-2.0 implementation; runtime TLD overrides are not supported.
 * IP literals are parsed locally, without DNS lookups.
 */
public final class EmailValidator {
    private static final String SPECIAL = "\\p{Cntrl}\\(\\)<>@,;:'\\\\\\\"\\.\\[\\]";
    private static final String VALID = "(\\\\.)|[^\\s" + SPECIAL + "]";
    private static final String WORD = "((" + VALID + "|')+|(\"(\\\\\"|[^\"])*\"))";
    private static final Pattern EMAIL = Pattern.compile("^(.+)@(\\S+)$");
    private static final Pattern USER = Pattern.compile("^" + WORD + "(\\." + WORD + ")*$");
    private static final Pattern DOMAIN = Pattern.compile("^(?:\\p{Alnum}(?>[\\p{Alnum}-]{0,61}\\p{Alnum})?\\.)+(\\p{Alpha}(?>[\\p{Alnum}-]{0,61}\\p{Alnum})?)\\.?$");
    private static final Pattern IP_DOMAIN = Pattern.compile("^\\[(.*)\\]$");
    private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(?:\\.\\d{1,3}){3}");
    private static final Pattern PREFIX = Pattern.compile("\\d{1,3}");
    private static final Pattern ZONE = Pattern.compile("[^\\s/%]+");
    private static final Set<String> TLDS = loadTlds();

    private EmailValidator() {
    }

    public static boolean isValid(String email) {
        if (email == null || email.endsWith(".")) {
            return false;
        }
        Matcher match = EMAIL.matcher(email);
        if (!match.matches() || match.group(1).length() > 64 || !USER.matcher(match.group(1)).matches()) {
            return false;
        }
        String domain = match.group(2);
        Matcher ipDomain = IP_DOMAIN.matcher(domain);
        if (ipDomain.matches()) {
            String address = ipDomain.group(1);
            return isIpv4(address) || isIpv6(address);
        }
        try {
            domain = IDN.toASCII(domain);
        } catch (IllegalArgumentException ignored) {
            // As in Commons Validator, the original input then fails the ASCII domain pattern.
        }
        if (domain.length() > 253) {
            return false;
        }
        Matcher domainMatch = DOMAIN.matcher(domain);
        return domainMatch.matches() && TLDS.contains(domainMatch.group(1).toLowerCase(Locale.ROOT));
    }

    private static boolean isIpv4(String address) {
        if (!IPV4.matcher(address).matches()) {
            return false;
        }
        for (String part : address.split("\\.")) {
            if (part.length() > 1 && part.startsWith("0") || Integer.parseInt(part) > 255) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIpv6(String address) {
        String[] prefix = address.split("/", -1);
        if (prefix.length > 2 || prefix.length == 2 &&
                (!PREFIX.matcher(prefix[1]).matches() || Integer.parseInt(prefix[1]) > 128)) {
            return false;
        }
        String[] zone = prefix[0].split("%", -1);
        if (zone.length > 2 || zone.length == 2 && !ZONE.matcher(zone[1]).matches()) {
            return false;
        }
        address = zone[0];
        boolean compressed = address.contains("::");
        if (compressed && address.indexOf("::") != address.lastIndexOf("::")) {
            return false;
        }
        boolean startsCompressed = address.startsWith("::");
        boolean endsCompressed = address.endsWith("::");
        if (address.startsWith(":") && !startsCompressed || address.endsWith(":") && !endsCompressed) {
            return false;
        }
        java.util.List<String> octets = new java.util.ArrayList<>(Arrays.asList(address.split(":")));
        if (compressed) {
            if (endsCompressed) {
                octets.add("");
            } else if (startsCompressed && !octets.isEmpty()) {
                octets.remove(0);
            }
        }
        if (octets.size() > 8) {
            return false;
        }
        int count = 0;
        int empty = 0;
        for (int i = 0; i < octets.size(); i++) {
            String octet = octets.get(i);
            if (octet.trim().isEmpty()) {
                if (++empty > 1) {
                    return false;
                }
            } else {
                empty = 0;
                if (i == octets.size() - 1 && octet.contains(".")) {
                    if (!isIpv4(octet)) {
                        return false;
                    }
                    count += 2;
                    continue;
                }
                if (octet.length() > 4) {
                    return false;
                }
                try {
                    int value = Integer.parseInt(octet, 16);
                    if (value < 0 || value > 0xffff) {
                        return false;
                    }
                } catch (NumberFormatException e) {
                    return false;
                }
            }
            count++;
        }
        return count <= 8 && (count >= 8 || compressed);
    }

    private static Set<String> loadTlds() {
        InputStream input = EmailValidator.class.getResourceAsStream("tlds.txt");
        if (input == null) {
            throw new IllegalStateException("Missing email TLD data");
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            Set<String> result = new HashSet<>();
            reader.lines().filter(line -> !line.isEmpty() && !line.startsWith("#")).forEach(result::add);
            return Set.copyOf(result);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load email TLD data", e);
        }
    }
}
