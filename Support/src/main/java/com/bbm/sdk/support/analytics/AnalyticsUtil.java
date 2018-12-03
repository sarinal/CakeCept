/*
 * Copyright (c) 2018 BlackBerry.  All Rights Reserved.
 *
 * You must obtain a license from and pay any applicable license fees to
 * BlackBerry before you may reproduce, modify or distribute this
 * software, or any work that includes all or part of this software.
 *
 * This file may contain contributions from others. Please review this entire
 * file for other proprietary rights or license notices.
 */

package com.bbm.sdk.support.analytics;


import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.bbmds.Stat;
import com.bbm.sdk.bbmds.internal.Existence;
import com.bbm.sdk.bbmds.internal.lists.ObservableList;
import com.bbm.sdk.reactive.Mutable;
import com.bbm.sdk.reactive.ObservableValue;
import com.bbm.sdk.reactive.SingleshotMonitor;
import com.bbm.sdk.support.util.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utilities for accessing the analytics provided in the BBM Enteprise SDK.
 */
public class AnalyticsUtil {

    public static class Statistics<T> {

        public T stats;
        public Existence exists = Existence.MAYBE;
    }

    /**
     * Provides analytics statistics from the {@link Stat} list in a simple JSON format.
     * When the statistics have been populated the {@link Statistics#exists} stats will be true.
     *
     * <pre>
     * example output:
     *
     * {
     *     "setup.err": 6,
     *     "msg": ["Picture", "Picture", "Picture", "Picture", "Text", "Text", "File"]
     *     "mailbox.addMember": ["ok, "ok", "ok", "500", "400"]
     * }
     * </pre>
     * @return an observable stats of type Statistics.
     */
    public static ObservableValue<Statistics<JSONObject>> getStatisticsAsJSON() {
        JSONObject properties = new JSONObject();
        Statistics<JSONObject> result = new Statistics<>();
        result.stats = properties;
        Mutable<Statistics<JSONObject>> mutableResult = new Mutable<>(result);

        SingleshotMonitor.run(new SingleshotMonitor.RunUntilTrue() {
            @Override
            public boolean run() {

                ObservableList<Stat> obsStatList = BBMEnterprise.getInstance().getBbmdsProtocol().getStatList();
                obsStatList.get();
                if (obsStatList.isPending()) {
                    return false;
                }

                List<Stat> statList = obsStatList.get();
                //get stats from bbmcore and plus to properties
                for (Stat stat: statList) {
                    try {
                        if (stat.part.size() > 0) {
                            if (stat.part.size() != stat.count.size()) {
                                Logger.e("Cannot report stat, part/count size mismatch" + stat.toString());
                                break;
                            }
                            //Create a JSONArray to hold the "parts"
                            JSONArray values = new JSONArray();
                            //Loop through each part
                            for (int j = 0; j < stat.part.size(); j++) {
                                //Get the "count" (number of occurrences) for that part
                                long count = stat.count.get(j);
                                String part = stat.part.get(j);
                                for (int i = 0; i < count; i++) {
                                    //Copy that part "count" times into the parts JSONArray
                                    values.put(part);
                                }
                            }
                            properties.put(stat.name, values);
                        } else {
                            //There are no "parts" just add the name and the stats
                            properties.put(stat.name, stat.count.get(0));
                        }

                    } catch (JSONException e) {
                        Logger.e(e);
                    }
                }

                result.exists = Existence.YES;
                mutableResult.dirty();

                return true;
            }
        });

        return mutableResult;
    }

    /**
     * Provides analytics statistics from the {@link Stat} list in a key-value format.
     * When the statistics have been populated the {@link Statistics#exists} stats will be true.
     * <pre>
     * example output:
     *     key                      | value
     *     "setup.err"              | "6",
     *     "msg.Picture"            | "4",
     *     "msg.Text"               | "2",
     *     "msg.File"               | "1",
     *     "mailbox.addMember.ok"   | "2",
     *     "mailbox.addMember.500"  | "1",
     *     "mailbox.addMember.400"  | "1",
     * </pre>
     * @return an observable stats of type Statistics.
     */
    public static ObservableValue<Statistics<Map<String, String>>> getStatisticsAsMap() {
        HashMap<String, String> properties = new HashMap<>();
        Statistics<Map<String, String>> result = new Statistics<>();
        result.stats = properties;
        Mutable<Statistics<Map<String, String>>> mutableResult = new Mutable<>(result);

        SingleshotMonitor.run(new SingleshotMonitor.RunUntilTrue() {
            @Override
            public boolean run() {

                ObservableList<Stat> obsStatList = BBMEnterprise.getInstance().getBbmdsProtocol().getStatList();
                if (obsStatList.isPending()) {
                    return false;
                }

                List<Stat> statList = obsStatList.get();
                //get stats from bbmcore and plus to properties
                for (Stat stat: statList) {
                    if (stat.part.size() > 0) {
                        if (stat.part.size() != stat.count.size()) {
                            Logger.e("Cannot report stat, part/count size mismatch" + stat.toString());
                            break;
                        }
                        //Loop through each part
                        for (int j = 0; j < stat.part.size(); j++) {
                            //Get the "count" (number of occurrences) for that part
                            long count = stat.count.get(j);
                            String part = stat.part.get(j);
                            properties.put(stat.name + "." + part, Long.toString(count));
                        }

                    } else {
                        //There are no "parts" just add the name and the stats
                        properties.put(stat.name, Long.toString(stat.count.get(0)));
                    }
                }

                result.exists = Existence.YES;
                mutableResult.dirty();

                return true;
            }
        });

        return mutableResult;
    }

}
