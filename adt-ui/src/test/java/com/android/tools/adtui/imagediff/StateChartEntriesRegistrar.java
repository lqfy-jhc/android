/*
 * Copyright (C) 2016 The Android Open Source Project
 *
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
 */
package com.android.tools.adtui.imagediff;

import com.android.tools.adtui.chart.statechart.StateChart;
import com.android.tools.adtui.model.DefaultDataSeries;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.StateChartModel;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

class StateChartEntriesRegistrar extends ImageDiffEntriesRegistrar {

  public StateChartEntriesRegistrar() {
    registerSimpleStateChart();
    registerMultipleSeriesStateChart();
    registerTextStateChart();
    registerRepeatedState();
    registerMouseOverSeriesStateChart();
    registerMouseExitSeriesStateChart();
    registerMouseOverAlphaSeriesStateChart();
  }

  private void registerSimpleStateChart() {
    register(new StateChartImageDiffEntry("state_chart_baseline.png") {
      @Override
      protected void generateComponent() {
        // Create a simple state chart with only one series
        addSeries();
      }
    });
  }

  private void registerMultipleSeriesStateChart() {
    register(new StateChartImageDiffEntry("state_chart_multiple_series_baseline.png") {
      @Override
      protected void generateComponent() {
        // Create a state chart with multiple series
        addSeries();
        addSeries();
      }
    });
  }

  private void registerMouseOverSeriesStateChart() {
    register(new StateChartImageDiffEntry("state_chart_mouse_over.png") {
      @Override
      protected void setUp() {
        super.setUp();
        // Move the mouse over an element other than the first (which is transparent).
        myStateChart.mouseMoved(new MouseEvent(myStateChart, 0, 0, 0, 130, 0, 0, false, 0));
      }

      @Override
      protected void generateComponent() {
        // Create a state chart with multiple series
        addSeries();
      }
    });
  }

  private void registerMouseExitSeriesStateChart() {
    register(new StateChartImageDiffEntry("state_chart_mouse_exit.png") {
      @Override
      protected void setUp() {
        super.setUp();
        // Ensure that mouse exit moves off all elements.
        myStateChart.mouseExited(new MouseEvent(myStateChart, 0, 0, 0, 0, 0, 0, false, 0));
      }

      @Override
      protected void generateComponent() {
        // Create a state chart with multiple series
        addSeries();
      }
    });
  }

  private void registerMouseOverAlphaSeriesStateChart() {
    register(new StateChartImageDiffEntry("state_chart_mouse_over_alpha.png") {
      @Override
      protected void setUp() {
        super.setUp();
        // Ensure that mouse exit moves off all elements.
        myStateChart.mouseMoved(new MouseEvent(myStateChart, 0, 0, 0, 0, 0, 0, false, 0));
      }

      @Override
      protected void generateComponent() {
        // Create a state chart with multiple series
        addSeries();
      }
    });
  }

  private void registerRepeatedState() {
    // The similarity threshold is smaller than the default one because ignoring repeated states and not ignoring it
    // differs only by a tiny border between repeated values, which might not be catch by the default threshold.
    float thresholdSimilarityOverride = 0.01f;

    register(new StateChartImageDiffEntry("state_chart_repeated_baseline.png", thresholdSimilarityOverride) {
      @Override
      protected void generateComponent() {
        myXRange.set(0, 100);
        myStateChart.setHeightGap(0.4f);
        addSeries();
      }

      @Override
      protected void generateTestData() {
        DefaultDataSeries<TestState> series = myData.get(0);
        series.add(0, TestState.STATE1);
        series.add(5, TestState.STATE1);
        series.add(7, TestState.STATE1);

        series.add(8, TestState.STATE2);
        series.add(12, TestState.STATE2);
        series.add(16, TestState.STATE2);
        series.add(50, TestState.STATE2);
        series.add(101, TestState.STATE2);
      }
    });
  }

  private void registerTextStateChart() {
    // The threshold for image similarity is overridden by a higher value than the default one in this test,
    // because it's mostly composed of text and the font differ slightly depending on the OS.
    float thresholdSimilarityOverride = 1.5f;

    register(new StateChartImageDiffEntry("state_chart_text_baseline.png", thresholdSimilarityOverride) {
      @Override
      protected void generateComponent() {
        // The component generated by this test is mostly composed by texts, so it's better to use a TrueType Font.
        // TODO: this might hide some issues. We need to use the same font used in studio, to reflect what is seen by the user.
        myStateChart.setFont(ImageDiffUtil.getDefaultFont());

        // Set the render mode of the state chart to text
        myStateChart.setRenderMode(StateChart.RenderMode.TEXT);
        // Add a considerable amount of series to the state chart,
        // because the text of a single state chart doesn't occupy a lot of the image
        for (int i = 0; i < 15; i++) {
          addSeries();
        }
      }
    });
  }

  private static abstract class StateChartImageDiffEntry extends AnimatedComponentImageDiffEntry {

    protected enum TestState {
      NONE,
      STATE1,
      STATE2
    }

    /**
     * Arbitrary values to be added in the state charts.
     */
    private static final TestState[] MY_VALUES = {TestState.NONE, TestState.STATE1, TestState.STATE2, TestState.NONE, TestState.STATE1,
      TestState.STATE2};

    /**
     * Arbitrary flags to determine if a new state should be added to a state chart at some iteration.
     */
    private static final boolean[] NEW_STATE_CONTROL = {true, false, false, false, true, false, false};

    /**
     * Stores the index of the flag that will determine whether a value is going to be inserted in a state chart.
     */
    private int myNewStateControlArrayIndex;

    /**
     * Stores the index of the next value to be inserted in a state chart.
     */
    private int myValuesArrayIndex;

    StateChart<TestState> myStateChart;

    private StateChartModel<TestState> myStateChartModel;

    protected List<DefaultDataSeries<TestState>> myData;

    StateChartImageDiffEntry(String baselineFilename, float similarityThreshold) {
      super(baselineFilename, similarityThreshold);
    }

    StateChartImageDiffEntry(String baselineFilename) {
      super(baselineFilename);
    }

    @Override
    protected void setUp() {
      myData = new ArrayList<>();
      myStateChartModel = new StateChartModel<>();
      myStateChart = new StateChart<>(myStateChartModel, getTestStateColor());
      myContentPane.add(myStateChart, BorderLayout.CENTER);
      myComponents.add(myStateChartModel);
      myNewStateControlArrayIndex = 0;
      myValuesArrayIndex = 0;
    }

    @Override
    protected void generateTestData() {
      for (int i = 0; i < TOTAL_VALUES; i++) {
        for (DefaultDataSeries<TestState> series : myData) {
          if (NEW_STATE_CONTROL[myNewStateControlArrayIndex++]) {
            int valueIndex = myValuesArrayIndex++ % MY_VALUES.length;
            // Don't add repeated states
            if (series.size() == 0 || series.getY(series.size() - 1) != MY_VALUES[valueIndex]) {
              series.add(myCurrentTimeUs, MY_VALUES[valueIndex]);
            }
          }
          myNewStateControlArrayIndex %= NEW_STATE_CONTROL.length;
        }
        myCurrentTimeUs += TIME_DELTA_US;
      }
    }

    private static EnumMap<TestState, Color> getTestStateColor() {
      EnumMap<TestState, Color> colors = new EnumMap<>(TestState.class);
      colors.put(TestState.NONE, new Color(0, 0, 0, 0));
      // Using colors other than primary so we can see a change with mouse over effect.
      colors.put(TestState.STATE1, new Color(0x558A71));
      colors.put(TestState.STATE2, new Color(0x8FB3EA));
      return colors;
    }

    /**
     * Add a series to the state chart.
     */
    protected void addSeries() {
      DefaultDataSeries<TestState> series = new DefaultDataSeries<>();
      RangedSeries<TestState> rangedSeries = new RangedSeries<>(myXRange, series);
      myData.add(series);
      myStateChartModel.addSeries(rangedSeries);
    }
  }
}
