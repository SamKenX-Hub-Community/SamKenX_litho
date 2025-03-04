/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
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

package com.facebook.litho;

import static com.facebook.litho.LifecycleStep.getSteps;
import static org.assertj.core.api.Assertions.assertThat;

import com.facebook.litho.config.ComponentsConfiguration;
import com.facebook.litho.testing.BackgroundLayoutLooperRule;
import com.facebook.litho.testing.LegacyLithoViewRule;
import com.facebook.litho.testing.testrunner.LithoTestRunner;
import com.facebook.litho.widget.LayoutSpecLifecycleTester;
import com.facebook.litho.widget.LayoutSpecLifecycleTesterSpec;
import com.facebook.litho.widget.events.EventWithoutAnnotation;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.LooperMode;

@LooperMode(LooperMode.Mode.LEGACY)
@RunWith(LithoTestRunner.class)
public class LayoutSpecLifecycleTest {

  public final @Rule LegacyLithoViewRule mLegacyLithoViewRule = new LegacyLithoViewRule();
  public @Rule BackgroundLayoutLooperRule mBackgroundLayoutLooperRule =
      new BackgroundLayoutLooperRule();

  @Test
  public void lifecycle_onSetRootWithoutLayout_shouldNotCallLifecycleMethods() {
    final List<LifecycleStep.StepInfo> info = new ArrayList<>();
    final Component component =
        LayoutSpecLifecycleTester.create(mLegacyLithoViewRule.getContext()).steps(info).build();
    mLegacyLithoViewRule.setRoot(component).idle();

    assertThat(getSteps(info))
        .describedAs("Only render lifecycle methods should be called")
        .containsExactly(
            LifecycleStep.ON_CREATE_INITIAL_STATE,
            LifecycleStep.ON_CREATE_TREE_PROP,
            LifecycleStep.ON_CALCULATE_CACHED_VALUE,
            LifecycleStep.ON_CREATE_LAYOUT);
  }

  @Test
  public void lifecycle_onSetRootWithLayout_shouldCallLifecycleMethods() {
    ComponentsConfiguration.isAnimationDisabled = false;
    final List<LifecycleStep.StepInfo> info = new ArrayList<>();
    final Component component =
        LayoutSpecLifecycleTester.create(mLegacyLithoViewRule.getContext()).steps(info).build();
    mLegacyLithoViewRule.setRoot(component);

    mLegacyLithoViewRule.attachToWindow().measure().layout();

    assertThat(getSteps(info))
        .describedAs("Should call the lifecycle methods in expected order")
        .containsExactly(
            LifecycleStep.ON_CREATE_INITIAL_STATE,
            LifecycleStep.ON_CREATE_TREE_PROP,
            LifecycleStep.ON_CALCULATE_CACHED_VALUE,
            LifecycleStep.ON_CREATE_LAYOUT,
            LifecycleStep.ON_CREATE_TRANSITION,
            LifecycleStep.ON_ATTACHED);
    ComponentsConfiguration.isAnimationDisabled = true;
  }

  @Test
  public void lifecycle_release_shouldCallLifecycleMethodOnDetach() {
    final List<LifecycleStep.StepInfo> info = new ArrayList<>();
    final Component component =
        LayoutSpecLifecycleTester.create(mLegacyLithoViewRule.getContext()).steps(info).build();
    mLegacyLithoViewRule.setRoot(component);

    mLegacyLithoViewRule.attachToWindow().measure().layout();
    info.clear();
    mLegacyLithoViewRule.release();

    assertThat(getSteps(info))
        .describedAs("Should call onDetached")
        .containsExactly(LifecycleStep.ON_DETACHED);
  }

  @Test
  public void lifecycle_subsequentSetRoot_shouldCallLifecycleMethod() {
    final List<LifecycleStep.StepInfo> info = new ArrayList<>();
    final LayoutSpecLifecycleTester component =
        LayoutSpecLifecycleTester.create(mLegacyLithoViewRule.getContext()).steps(info).build();
    mLegacyLithoViewRule.setRoot(component);
    mLegacyLithoViewRule.attachToWindow().measure().layout();
    info.clear();

    final LayoutSpecLifecycleTester newComponent =
        LayoutSpecLifecycleTester.create(mLegacyLithoViewRule.getContext()).steps(info).build();
    mLegacyLithoViewRule.setRoot(newComponent);
    mLegacyLithoViewRule.attachToWindow().measure().layout();

    assertThat(getSteps(info))
        .describedAs("Should call the lifecycle methods in expected order")
        .containsExactly(
            LifecycleStep.ON_CREATE_TREE_PROP,
            LifecycleStep.ON_CALCULATE_CACHED_VALUE,
            LifecycleStep.ON_CREATE_LAYOUT);
  }

  @Test
  public void lifecycle_updateState_shouldCallLifecycleMethod() {
    // TODO: T66662176 Remove code to disable and enable reconciliation.
    ComponentsConfiguration.isReconciliationEnabled = false;
    final List<LifecycleStep.StepInfo> info = new ArrayList<>();
    LayoutSpecLifecycleTesterSpec.Caller caller = new LayoutSpecLifecycleTesterSpec.Caller();
    final Component component =
        LayoutSpecLifecycleTester.create(mLegacyLithoViewRule.getContext())
            .steps(info)
            .caller(caller)
            .build();
    mLegacyLithoViewRule.setRoot(component);
    mLegacyLithoViewRule.attachToWindow().measure().layout();
    info.clear();
    caller.updateStateSync();
    assertThat(getSteps(info))
        .describedAs("Should call the lifecycle methods in expected order")
        .containsExactly(
            LifecycleStep.ON_UPDATE_STATE,
            LifecycleStep.ON_CREATE_TREE_PROP,
            LifecycleStep.ON_CALCULATE_CACHED_VALUE,
            LifecycleStep.ON_CREATE_LAYOUT);
    ComponentsConfiguration.isReconciliationEnabled = true;
  }

  @Test
  public void lifecycle_updateStateWithTransition_shouldCallLifecycleMethod() {
    // TODO: T66662176 Remove code to disable and enable reconciliation.
    ComponentsConfiguration.isReconciliationEnabled = false;
    final List<LifecycleStep.StepInfo> info = new ArrayList<>();
    LayoutSpecLifecycleTesterSpec.Caller caller = new LayoutSpecLifecycleTesterSpec.Caller();
    final Component component =
        LayoutSpecLifecycleTester.create(mLegacyLithoViewRule.getContext())
            .steps(info)
            .caller(caller)
            .build();
    mLegacyLithoViewRule.setRoot(component);
    mLegacyLithoViewRule.attachToWindow().measure().layout();
    info.clear();
    caller.updateStateWithTransition();
    mBackgroundLayoutLooperRule.runToEndOfTasksSync();
    assertThat(getSteps(info))
        .describedAs("Should call the lifecycle methods in expected order")
        .containsExactly(
            LifecycleStep.ON_UPDATE_STATE_WITH_TRANSITION,
            LifecycleStep.ON_CREATE_TREE_PROP,
            LifecycleStep.ON_CALCULATE_CACHED_VALUE,
            LifecycleStep.ON_CREATE_LAYOUT);
    ComponentsConfiguration.isReconciliationEnabled = true;
  }

  @Test
  public void lifecycle_dispatchEventWithoutAnnotation_shouldCallOnEventWithoutAnnotation() {
    final List<LifecycleStep.StepInfo> info = new ArrayList<>();
    LayoutSpecLifecycleTesterSpec.Caller caller = new LayoutSpecLifecycleTesterSpec.Caller();
    final Component component =
        LayoutSpecLifecycleTester.create(mLegacyLithoViewRule.getContext())
            .steps(info)
            .caller(caller)
            .build();
    mLegacyLithoViewRule.setRoot(component);
    mLegacyLithoViewRule.attachToWindow().measure().layout();
    info.clear();
    EventWithoutAnnotation eventDispatched = new EventWithoutAnnotation(1, true, "hello");
    caller.dispatchEventWithoutAnnotation(eventDispatched);
    EventWithoutAnnotation eventReceived = caller.getEventWithoutAnnotation();

    assertThat(eventReceived).isNotNull();
    assertThat(eventDispatched.count).isSameAs(eventReceived.count);
    assertThat(eventDispatched.isDirty).isSameAs(eventReceived.isDirty);
    assertThat(eventDispatched.message).isSameAs(eventReceived.message);
  }
}
