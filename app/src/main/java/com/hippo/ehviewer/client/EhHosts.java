/*
 * Copyright 2018 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.ehviewer.client;

/*
 * Created by Hippo on 2018/3/23.
 */

import android.content.Context;

import androidx.annotation.NonNull;

import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.Hosts;
import com.hippo.ehviewer.Settings;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.dnsoverhttps.DnsOverHttps;


public class EhHosts implements Dns {

    private static final Map<String, List<InetAddress>> builtInHosts;

    static {
        Map<String, List<InetAddress>> map = new HashMap<>();
        if (Settings.getBuiltInHosts()) {
            put(map, "e-hentai.org",
                    "104.20.18.168",
                    "104.20.19.168",
                    "172.67.2.238",
                    "178.162.139.33",
                    "178.162.139.34",
                    "178.162.139.36",
                    "178.162.145.131",
                    "178.162.145.132",
                    "178.162.145.152"
            );
            put(map, "repo.e-hentai.org", "94.100.28.57", "94.100.29.73");
            put(map, "forums.e-hentai.org", "94.100.18.243");
            put(map, "upld.e-hentai.org", "94.100.18.249", "94.100.18.247");
            put(map, "ehgt.org",
                    "109.236.85.28",
                    "62.112.8.21",
                    "89.39.106.43",
                    "2a00:7c80:0:123::3a85",
                    "2a00:7c80:0:12d::38a1",
                    "2a00:7c80:0:13b::37a4");
            put(map, "ehgt.org",
                    "109.236.85.28",
                    "62.112.8.21",
                    "89.39.106.43");
            put(map, "raw.githubusercontent.com", "151.101.0.133", "151.101.64.133", "151.101.128.133", "151.101.192.133");

        }

        if (Settings.getBuiltEXHosts()) {
            put(map, "exhentai.org",
                    "104.24.56.202",
                    "178.175.129.251",
                    "178.175.129.252",
                    "178.175.129.253",
                    "178.175.129.254",
                    "178.175.128.251",
                    "178.175.128.252",
                    "178.175.128.253",
                    "178.175.128.254",
                    "178.175.132.19",
                    "178.175.132.20",
                    "178.175.132.21",
                    "178.175.132.22"
//                    "172.67.187.219"
            );
            put(map, "upld.exhentai.org", "178.175.132.22", "178.175.129.254", "178.175.128.254");
            put(map, "s.exhentai.org",
//                    "104.24.56.202",
//                    "178.175.129.251",
//                    "178.175.129.252",
                    "178.175.129.253",
                    "178.175.129.254",
//                    "178.175.128.251",
//                    "178.175.128.252",
                    "178.175.128.253",
                    "178.175.128.254",
//                    "178.175.132.19",
//                    "178.175.132.20",
                    "178.175.132.21",
                    "178.175.132.22"
            );
        }

        builtInHosts = map;
    }

    private final Hosts hosts;
    private static DnsOverHttps dnsOverHttps;

    public EhHosts(Context context) {
        hosts = EhApplication.getHosts(context);
        DnsOverHttps.Builder builder = new DnsOverHttps.Builder()
                .client(new OkHttpClient.Builder().cache(EhApplication.getOkHttpCache(context)).build())
                .url(HttpUrl.get("https://77.88.8.1/dns-query"));
        dnsOverHttps = builder.post(true).build();
    }

    private static void put(Map<String, List<InetAddress>> map, String host, String... ips) {
        List<InetAddress> addresses = new ArrayList<>();
        for (String ip : ips) {
            addresses.add(Hosts.toInetAddress(host, ip));
        }
        map.put(host, addresses);
    }


    @NonNull
    @Override
    public List<InetAddress> lookup(@NonNull String hostname) throws UnknownHostException {

        List<InetAddress> inetAddresses = (List<InetAddress>) hosts.get(hostname);
        if (inetAddresses != null) {
            Collections.shuffle(inetAddresses, new Random(System.currentTimeMillis()));
            return inetAddresses;
        }
        if (Settings.getBuiltInHosts() || Settings.getBuiltEXHosts()) {
            inetAddresses = builtInHosts.get(hostname);
            if (inetAddresses != null) {
                Collections.shuffle(inetAddresses, new Random(System.currentTimeMillis()));
                return inetAddresses;
            }
        }
        if (Settings.getDoH()) {
            inetAddresses = dnsOverHttps.lookup(hostname);
            if (!inetAddresses.isEmpty()) {
                Collections.shuffle(inetAddresses, new Random(System.currentTimeMillis()));
                return inetAddresses;
            }
        }
        try {
            inetAddresses = Arrays.asList(InetAddress.getAllByName(hostname));
            Collections.shuffle(inetAddresses, new Random(System.currentTimeMillis()));
            return inetAddresses;
        } catch (NullPointerException e) {
            UnknownHostException unknownHostException =
                    new UnknownHostException("Broken system behaviour for dns lookup of " + hostname);
            unknownHostException.initCause(e);
            throw unknownHostException;
        }
    }
}
