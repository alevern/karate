/*
 * The MIT License
 *
 * Copyright 2018 Intuit Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.intuit.karate.core;

import com.intuit.karate.StringUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class ScenarioResult implements Comparable<ScenarioResult> {

    private final List<StepResult> stepResults = new ArrayList();
    private final Scenario scenario;

    private StepResult failedStep;

    private String executorName;
    private long startTime;
    private long endTime;
    private long durationNanos;

    @Override
    public int compareTo(ScenarioResult sr) {
        if (sr == null) {
            return 1;
        }
        int delta = scenario.getLine() - sr.scenario.getLine();
        if (delta != 0) {
            return delta;
        }
        return scenario.getExampleIndex() - sr.scenario.getExampleIndex();
    }

    public String getFailureMessageForDisplay() {
        if (failedStep == null) {
            return null;
        }
        // val message = feature + ":" + step.getLine + " " + result.getStep.getText
        Step step = failedStep.getStep();
        String featureName = scenario.getFeature().getResource().getRelativePath();
        return featureName + ":" + step.getLine() + " " + step.getText();
    }

    public StepResult addFakeStepResult(String message, Throwable error) {
        Step step = new Step(scenario, -1);
        step.setLine(scenario.getLine());
        step.setPrefix("*");
        step.setText(message);
        Result result = error == null ? Result.passed(0) : Result.failed(0, error, step);
        StepResult sr = new StepResult(step, result, error == null ? null : error.getMessage(), null, null);
        addStepResult(sr);
        return sr;
    }

    public void addStepResult(StepResult stepResult) {
        stepResults.add(stepResult);
        Result result = stepResult.getResult();
        durationNanos += result.getDurationNanos();
        if (result.isFailed()) {
            failedStep = stepResult;
        }
    }

    private static void recurse(List<Map> list, StepResult stepResult, int depth) {
        if (stepResult.getCallResults() != null) {
            for (FeatureResult fr : stepResult.getCallResults()) {
                Step call = new Step(stepResult.getStep().getFeature(), -1);
                call.setLine(stepResult.getStep().getLine());
                call.setPrefix(StringUtils.repeat('>', depth));
                call.setText(fr.getCallName());
                call.setDocString(fr.getCallArgPretty());
                StepResult callResult = new StepResult(call, Result.passed(0), null, null, null);
                callResult.setHidden(stepResult.isHidden());
                list.add(callResult.toCucumberJson());
                for (StepResult sr : fr.getAllScenarioStepResultsNotHidden()) {
                    Map<String, Object> map = sr.toCucumberJson();
                    String temp = (String) map.get("keyword");
                    map.put("keyword", StringUtils.repeat('>', depth + 1) + ' ' + temp);
                    list.add(map);
                    recurse(list, sr, depth + 1);
                }
            }
        }
    }

    private List<Map> getStepResults(boolean background) {
        List<Map> list = new ArrayList(stepResults.size());
        for (StepResult stepResult : stepResults) {
            if (stepResult.isHidden()) {
                continue;
            }
            if (background == stepResult.getStep().isBackground()) {
                list.add(stepResult.toCucumberJson());
                recurse(list, stepResult, 0);
            }
        }
        return list;
    }

    public static ScenarioResult fromKarateJson(Feature feature, Map<String, Object> map) {
        int sectionIndex = (Integer) map.get("section");
        int exampleIndex = (Integer) map.get("example");
        FeatureSection section = feature.getSection(sectionIndex);
        Scenario scenario = new Scenario(feature, section, exampleIndex);
        ScenarioResult sr = new ScenarioResult(scenario, null);
        String executorName = (String) map.get("executorName");
        Number startTime = (Number) map.get("startTime");
        Number endTime = (Number) map.get("endTime");
        sr.setExecutorName(executorName);
        if (startTime != null) {
            sr.setStartTime(startTime.longValue());
        }
        if (endTime != null) {
            sr.setEndTime(endTime.longValue());
        }
        List<Map<String, Object>> list = (List) map.get("stepResults");
        if (list != null) {
            for (Map<String, Object> stepResultMap : list) {
                StepResult stepResult = StepResult.fromKarateJson(scenario, stepResultMap);
                sr.addStepResult(stepResult);
            }
        }
        return sr;
    }

    public Map<String, Object> toKarateJson() {
        Map<String, Object> map = new HashMap();
        map.put("section", scenario.getSection().getIndex());
        map.put("example", scenario.getExampleIndex());
        map.put("executorName", executorName);
        map.put("startTime", startTime);
        map.put("endTime", endTime);
        List<Map<String, Object>> list = new ArrayList(stepResults.size());
        map.put("stepResults", list);
        for (StepResult sr : stepResults) {
            list.add(sr.toKarateJson());
        }
        return map;
    }

    public Map<String, Object> toCucumberJson() {
        Map<String, Object> map = new HashMap();
        map.put("name", scenario.getName());
        map.put("steps", getStepResults(false));
        map.put("line", scenario.getLine());
        map.put("id", StringUtils.toIdString(scenario.getName()));
        map.put("description", scenario.getDescription());
        map.put("type", "scenario");
        map.put("keyword", "Scenario");
        if (scenario.getTags() != null) {
            map.put("tags", tagsToCucumberJson(scenario.getTagsEffective().getOriginal()));
        }
        return map;
    }

    public static List<Map> tagsToCucumberJson(Collection<Tag> tags) {
        List<Map> list = new ArrayList(tags.size());
        for (Tag tag : tags) {
            Map<String, Object> tagMap = new HashMap(2);
            tagMap.put("line", tag.getLine());
            tagMap.put("name", '@' + tag.getText());
            list.add(tagMap);
        }
        return list;
    }

    public Map<String, Object> backgroundToCucumberJson() {
        if (!scenario.getFeature().isBackgroundPresent()) {
            return null;
        }
        Map<String, Object> map = new HashMap();
        map.put("name", "");
        map.put("steps", getStepResults(true));
        map.put("line", scenario.getFeature().getBackground().getLine());
        map.put("description", "");
        map.put("type", Background.TYPE);
        map.put("keyword", Background.KEYWORD);
        return map;
    }

    public ScenarioResult(Scenario scenario, List<StepResult> stepResults) {
        this.scenario = scenario;
        if (stepResults != null) {
            this.stepResults.addAll(stepResults);
        }
    }

    public Scenario getScenario() {
        return scenario;
    }

    public List<StepResult> getStepResults() {
        return stepResults;
    }

    public List<StepResult> getStepResultsNotHidden() {
        List<StepResult> list = new ArrayList(stepResults.size());
        for (StepResult sr : stepResults) {
            if (sr.isHidden()) {
                continue;
            }
            list.add(sr);
        }
        return list;
    }

    public boolean isFailed() {
        return failedStep != null;
    }

    public StepResult getFailedStep() {
        return failedStep;
    }

    public Throwable getError() {
        return failedStep == null ? null : failedStep.getResult().getError();
    }

    public long getDurationNanos() {
        return durationNanos;
    }

    public double getDurationMillis() {
        return Engine.nanosToMillis(durationNanos);
    }

    public String getExecutorName() {
        return executorName;
    }

    public void setExecutorName(String executorName) {
        this.executorName = executorName;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    @Override
    public String toString() {
        return failedStep == null ? scenario.toString() : failedStep + "";
    }

}
