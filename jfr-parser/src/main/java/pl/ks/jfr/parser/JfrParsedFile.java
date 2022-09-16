/*
 * Copyright 2022 Krzysztof Slusarski
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
package pl.ks.jfr.parser;

import static java.util.function.Function.identity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.apache.logging.log4j.util.Strings;
import pl.ks.collapsed.CollapsedStack;

public class JfrParsedFile {
    private final List<JfrParsedExecutionSampleEvent> executionSamples = new ArrayList<>();
    private final List<JfrParsedAllocationEvent> allocationSamples = new ArrayList<>();
    private final List<JfrParsedLockEvent> lockSamples = new ArrayList<>();
    private final Map<String, String> canonicalStrings = new ConcurrentHashMap<>();

    private final Instant parseStartDate = Instant.now();
    private Instant minEventDate;
    private Instant maxEventDate;

    void addExecutionSampleEvent(JfrParsedExecutionSampleEvent event) {
        synchronized (executionSamples) {
            executionSamples.add(event);
        }
    }

    void addAllocationSampleEvent(JfrParsedAllocationEvent event) {
        synchronized (allocationSamples) {
            allocationSamples.add(event);
        }
    }

    void addLockSampleEvent(JfrParsedLockEvent event) {
        synchronized (lockSamples) {
            lockSamples.add(event);
        }
    }

    String getCanonicalString(String str) {
        String canonical = canonicalStrings.putIfAbsent(str, str);
        return canonical == null ? str : canonical;
    }

    void calculateAggregatedDates() {
        minEventDate = getMinDate(List.of(
                getMinDate(executionSamples, JfrParsedExecutionSampleEvent::getEventTime),
                getMinDate(allocationSamples, JfrParsedAllocationEvent::getEventTime),
                getMinDate(lockSamples, JfrParsedLockEvent::getEventTime)
        ), identity());
        maxEventDate = getMaxDate(List.of(
                getMaxDate(executionSamples, JfrParsedExecutionSampleEvent::getEventTime),
                getMaxDate(allocationSamples, JfrParsedAllocationEvent::getEventTime),
                getMaxDate(lockSamples, JfrParsedLockEvent::getEventTime)
        ), identity());
    }

    private <T> Instant getMinDate(List<T> events, Function<T, Instant> toDateFunction) {
        return events.stream().parallel()
                .map(toDateFunction)
                .reduce(Instant.MAX, (i1, i2) -> i1.compareTo(i2) > 0 ? i2 : i1);
    }

    private <T> Instant getMaxDate(List<T> events, Function<T, Instant> toDateFunction) {
        return events.stream().parallel()
                .map(toDateFunction)
                .reduce(Instant.MIN, (i1, i2) -> i1.compareTo(i2) < 0 ? i2 : i1);
    }

    public List<JfrParsedExecutionSampleEvent> getExecutionSamples() {
        return executionSamples;
    }

    public List<JfrParsedAllocationEvent> getAllocationSamples() {
        return allocationSamples;
    }

    public List<JfrParsedLockEvent> getLockSamples() {
        return lockSamples;
    }

    public Instant getParseStartDate() {
        return parseStartDate;
    }

    public Instant getMinEventDate() {
        return minEventDate;
    }

    public Instant getMaxEventDate() {
        return maxEventDate;
    }

    public <T> CollapsedStack asCollapsed(List<T> samples, Function<T, String[]> toStackFunction) {
        CollapsedStack collapsedStack = new CollapsedStack();
        samples.stream().parallel()
                .map(toStackFunction)
                .forEach(stack -> {
                    String stackJoined = Strings.join(Arrays.asList(stack), ';');
                    collapsedStack.add(stackJoined, 1L);
                });
        return collapsedStack;
    }
}
