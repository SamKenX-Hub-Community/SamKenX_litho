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

import static com.facebook.litho.SizeSpec.EXACTLY;
import static com.facebook.litho.SizeSpec.makeSizeSpec;
import static com.facebook.litho.testing.TestViewComponent.create;
import static com.facebook.litho.testing.helper.ComponentTestHelper.measureAndLayout;
import static com.facebook.litho.testing.helper.ComponentTestHelper.mountComponent;
import static org.assertj.core.api.Assertions.assertThat;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import com.facebook.litho.config.ComponentsConfiguration;
import com.facebook.litho.testing.LegacyLithoViewRule;
import com.facebook.litho.testing.TestComponent;
import com.facebook.litho.testing.TestDrawableComponent;
import com.facebook.litho.testing.TestViewComponent;
import com.facebook.litho.testing.ViewGroupWithLithoViewChildren;
import com.facebook.litho.testing.Whitebox;
import com.facebook.litho.testing.testrunner.LithoTestRunner;
import com.facebook.litho.widget.ComponentWrapperTester;
import com.facebook.litho.widget.HorizontalScroll;
import com.facebook.litho.widget.LayoutSpecLifecycleTester;
import com.facebook.litho.widget.LayoutSpecVisibilityEventTester;
import com.facebook.litho.widget.TextDrawable;
import com.facebook.rendercore.extensions.ExtensionState;
import com.facebook.rendercore.visibility.VisibilityItem;
import com.facebook.rendercore.visibility.VisibilityMountExtension;
import com.facebook.yoga.YogaEdge;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;

@LooperMode(LooperMode.Mode.LEGACY)
@RunWith(LithoTestRunner.class)
public class VisibilityEventsTest {
  private static final int LEFT = 0;
  private static final int RIGHT = 15;

  private ComponentContext mContext;
  private LithoView mLithoView;
  private FrameLayout mParent;
  public final @Rule LegacyLithoViewRule mLegacyLithoViewRule = new LegacyLithoViewRule();

  @Before
  public void setup() {
    mContext = mLegacyLithoViewRule.getContext();
    mLithoView = new LithoView(mContext);
    mLegacyLithoViewRule.useLithoView(mLithoView);

    mParent = new FrameLayout(mContext.getAndroidContext());
    mParent.setLeft(0);
    mParent.setTop(0);
    mParent.setRight(15);
    mParent.setBottom(15);
    mParent.addView(mLithoView);
  }

  @Test
  public void testVisibleEvent() {
    final ComponentContext c = mLegacyLithoViewRule.getContext();
    final List<LifecycleStep.StepInfo> steps = new ArrayList<>();
    final LayoutSpecLifecycleTester component =
        LayoutSpecLifecycleTester.create(c)
            .steps(steps)
            .widthPx(10)
            .heightPx(5)
            .marginPx(YogaEdge.TOP, 5)
            .build();

    mLegacyLithoViewRule
        .setRoot(component)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(10, EXACTLY), makeSizeSpec(5, EXACTLY))
        .measure()
        .layout();

    assertThat(LifecycleStep.getSteps(steps))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);

    mLegacyLithoViewRule.getLithoView().notifyVisibleBoundsChanged(new Rect(0, 0, 20, 20), true);

    assertThat(LifecycleStep.getSteps(steps))
        .describedAs("Visible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_VISIBLE);
  }

  @Test
  public void testNoVisibleEventWhenNotProcessingVisibleOutputs() {
    final ComponentContext c = mLegacyLithoViewRule.getContext();
    final List<LifecycleStep.StepInfo> steps = new ArrayList<>();
    final LayoutSpecLifecycleTester component =
        LayoutSpecLifecycleTester.create(c)
            .steps(steps)
            .widthPx(10)
            .heightPx(5)
            .marginPx(YogaEdge.TOP, 5)
            .build();

    mLegacyLithoViewRule
        .setRoot(component)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(10, EXACTLY), makeSizeSpec(5, EXACTLY))
        .measure()
        .layout();

    assertThat(LifecycleStep.getSteps(steps))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);

    // invoke notifyVisibleBoundsChanged, but pass 'false' to process visibility outputs.
    mLegacyLithoViewRule.getLithoView().notifyVisibleBoundsChanged(new Rect(0, 0, 20, 20), false);

    assertThat(LifecycleStep.getSteps(steps))
        .describedAs("Visible event should still not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);
  }

  @Test
  public void testVisibleEventWithHeightRatio() {
    final ComponentContext c = mLegacyLithoViewRule.getContext();
    final List<LifecycleStep.StepInfo> steps = new ArrayList<>();
    final LayoutSpecLifecycleTester component =
        LayoutSpecLifecycleTester.create(c)
            .steps(steps)
            .visibleHeightRatio(0.4f)
            .widthPx(10)
            .heightPx(5)
            .marginPx(YogaEdge.TOP, 5)
            .build();

    mLegacyLithoViewRule
        .setRoot(component)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(10, EXACTLY), makeSizeSpec(5, EXACTLY))
        .measure()
        .layout();

    mLegacyLithoViewRule.getLithoView().notifyVisibleBoundsChanged(new Rect(0, 0, 10, 1), true);
    assertThat(LifecycleStep.getSteps(steps))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);

    mLegacyLithoViewRule.getLithoView().notifyVisibleBoundsChanged(new Rect(0, 0, 10, 2), true);
    assertThat(LifecycleStep.getSteps(steps))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);

    mLegacyLithoViewRule.getLithoView().notifyVisibleBoundsChanged(new Rect(0, 0, 10, 3), true);
    assertThat(LifecycleStep.getSteps(steps))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);

    mLegacyLithoViewRule.getLithoView().notifyVisibleBoundsChanged(new Rect(0, 0, 10, 4), true);
    assertThat(LifecycleStep.getSteps(steps))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);

    mLegacyLithoViewRule.getLithoView().notifyVisibleBoundsChanged(new Rect(0, 0, 10, 5), true);
    assertThat(LifecycleStep.getSteps(steps))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);

    mLegacyLithoViewRule.getLithoView().notifyVisibleBoundsChanged(new Rect(0, 0, 10, 6), true);
    assertThat(LifecycleStep.getSteps(steps))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);

    mLegacyLithoViewRule.getLithoView().notifyVisibleBoundsChanged(new Rect(0, 0, 10, 7), true);
    assertThat(LifecycleStep.getSteps(steps))
        .describedAs("Visible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_VISIBLE);
  }

  @Test
  public void testVisibleEventWithWidthRatio() {
    final ComponentContext c = mLegacyLithoViewRule.getContext();
    final List<LifecycleStep.StepInfo> steps = new ArrayList<>();
    final LayoutSpecLifecycleTester component =
        LayoutSpecLifecycleTester.create(c)
            .steps(steps)
            .visibleWidthRatio(0.4f)
            .widthPx(10)
            .heightPx(5)
            .marginPx(YogaEdge.TOP, 5)
            .build();

    mLegacyLithoViewRule
        .setRoot(component)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(10, EXACTLY), makeSizeSpec(5, EXACTLY))
        .measure()
        .layout();

    assertThat(LifecycleStep.getSteps(steps))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);

    mLegacyLithoViewRule.getLithoView().notifyVisibleBoundsChanged(new Rect(0, 0, 3, 10), true);
    assertThat(LifecycleStep.getSteps(steps))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);

    mLegacyLithoViewRule.getLithoView().notifyVisibleBoundsChanged(new Rect(0, 0, 5, 10), true);
    assertThat(LifecycleStep.getSteps(steps))
        .describedAs("Visible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_VISIBLE);
  }

  @Test
  public void testVisibleEventWithHeightAndWidthRatio() {
    final ComponentContext c = mLegacyLithoViewRule.getContext();
    final List<LifecycleStep.StepInfo> steps = new ArrayList<>();
    final LayoutSpecLifecycleTester component =
        LayoutSpecLifecycleTester.create(c)
            .steps(steps)
            .visibleWidthRatio(0.4f)
            .visibleHeightRatio(0.4f)
            .widthPx(10)
            .heightPx(5)
            .marginPx(YogaEdge.TOP, 5)
            .build();

    mLegacyLithoViewRule
        .setRoot(component)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(10, EXACTLY), makeSizeSpec(5, EXACTLY))
        .measure()
        .layout();

    // Neither width or height are in visible range
    mLegacyLithoViewRule.getLithoView().notifyVisibleBoundsChanged(new Rect(0, 0, 3, 6), true);
    assertThat(LifecycleStep.getSteps(steps))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);

    // Width but not height are in visible range
    mLegacyLithoViewRule.getLithoView().notifyVisibleBoundsChanged(new Rect(0, 0, 5, 6), true);
    assertThat(LifecycleStep.getSteps(steps))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);

    // Height but not width are in visible range
    mLegacyLithoViewRule.getLithoView().notifyVisibleBoundsChanged(new Rect(0, 0, 3, 8), true);
    assertThat(LifecycleStep.getSteps(steps))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);

    // Height and width are both in visible range
    mLegacyLithoViewRule.getLithoView().notifyVisibleBoundsChanged(new Rect(0, 0, 5, 8), true);
    assertThat(LifecycleStep.getSteps(steps))
        .describedAs("Visible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_VISIBLE);
  }

  @Test
  public void testFocusedOccupiesHalfViewport() {
    final ComponentContext c = mLegacyLithoViewRule.getContext();
    final List<LifecycleStep.StepInfo> steps = new ArrayList<>();
    final LayoutSpecLifecycleTester component =
        LayoutSpecLifecycleTester.create(c).steps(steps).widthPx(10).heightPx(10).build();

    mLegacyLithoViewRule
        .setRoot(component)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(10, EXACTLY), makeSizeSpec(10, EXACTLY))
        .measure()
        .layout();

    assertThat(LifecycleStep.getSteps(steps))
        .describedAs("Focused visible event should be dispatched")
        .contains(LifecycleStep.ON_FOCUSED_EVENT_VISIBLE);
  }

  @Test
  public void testFocusedOccupiesLessThanHalfViewport() {
    final ComponentContext c = mLegacyLithoViewRule.getContext();
    final List<LifecycleStep.StepInfo> steps = new ArrayList<>();
    final LayoutSpecLifecycleTester component =
        LayoutSpecLifecycleTester.create(c).steps(steps).widthPx(10).heightPx(3).build();

    mLegacyLithoViewRule
        .setRoot(component)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(10, EXACTLY), makeSizeSpec(10, EXACTLY))
        .measure()
        .layout();

    assertThat(LifecycleStep.getSteps(steps))
        .describedAs("Focused visible event should be dispatched")
        .contains(LifecycleStep.ON_FOCUSED_EVENT_VISIBLE);
  }

  @Test
  public void testMultipleFocusAndUnfocusEvents() {
    final TestComponent content = create(mContext).build();
    final EventHandler<FocusedVisibleEvent> focusedHandler = new EventHandler<>(content, 2);
    final EventHandler<UnfocusedVisibleEvent> unfocusedHandler = new EventHandler<>(content, 3);
    final Component root =
        Column.create(mContext)
            .child(
                Wrapper.create(mContext)
                    .delegate(content)
                    .focusedHandler(focusedHandler)
                    .unfocusedHandler(unfocusedHandler)
                    .widthPx(15)
                    .heightPx(5)
                    .marginPx(YogaEdge.TOP, 8))
            .build();
    mLegacyLithoViewRule
        .setRoot(root)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(100, EXACTLY), makeSizeSpec(100, EXACTLY))
        .measure()
        .layout();

    mLithoView.notifyVisibleBoundsChanged(new Rect(0, 0, 0, 0), true);

    // Mount test view in the middle of the view port (focused)
    content.getDispatchedEventHandlers().clear();
    mLithoView.notifyVisibleBoundsChanged(new Rect(LEFT, 8, RIGHT, 13), true);
    assertThat(content.getDispatchedEventHandlers()).containsOnly(focusedHandler);

    // Mount test view on the edge of the viewport (not focused)
    content.getDispatchedEventHandlers().clear();
    mLithoView.notifyVisibleBoundsChanged(new Rect(LEFT, 14, RIGHT, 19), true);
    assertThat(content.getDispatchedEventHandlers()).containsOnly(unfocusedHandler);

    // Mount test view in the middle of the view port (focused)
    content.getDispatchedEventHandlers().clear();
    mLithoView.notifyVisibleBoundsChanged(new Rect(LEFT, 8, RIGHT, 14), true);
    assertThat(content.getDispatchedEventHandlers()).containsOnly(focusedHandler);

    // Mount test view on the edge of the viewport (not focused)
    content.getDispatchedEventHandlers().clear();
    mLithoView.notifyVisibleBoundsChanged(new Rect(LEFT, 1, RIGHT, 6), true);
    assertThat(content.getDispatchedEventHandlers()).containsOnly(unfocusedHandler);
  }

  @Test
  public void testFullImpressionEvent() {
    final ComponentContext c = mLegacyLithoViewRule.getContext();
    final List<LifecycleStep.StepInfo> steps = new ArrayList<>();
    final LayoutSpecLifecycleTester component =
        LayoutSpecLifecycleTester.create(c)
            .steps(steps)
            .widthPx(10)
            .heightPx(5)
            .marginPx(YogaEdge.TOP, 5)
            .build();

    mLegacyLithoViewRule
        .setRoot(component)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(10, EXACTLY), makeSizeSpec(10, EXACTLY))
        .measure()
        .layout();

    assertThat(LifecycleStep.getSteps(steps))
        .describedAs("Full Impression visible event should be dispatched")
        .contains(LifecycleStep.ON_FULL_IMPRESSION_VISIBLE_EVENT);
  }

  @Test
  public void testVisibility1fTop() {
    final ComponentContext c = mLegacyLithoViewRule.getContext();
    final List<LifecycleStep.StepInfo> steps = new ArrayList<>();
    final LayoutSpecLifecycleTester component =
        LayoutSpecLifecycleTester.create(c)
            .steps(steps)
            .visibleHeightRatio(1f)
            .widthPx(10)
            .heightPx(5)
            .marginPx(YogaEdge.TOP, 5)
            .build();

    mLegacyLithoViewRule
        .setRoot(component)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(10, EXACTLY), makeSizeSpec(10, EXACTLY))
        .measure()
        .layout();

    assertThat(LifecycleStep.getSteps(steps))
        .describedAs("Visible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_VISIBLE);
  }

  @Test
  public void testVisibility1fBottom() {
    final ComponentContext c = mLegacyLithoViewRule.getContext();
    final List<LifecycleStep.StepInfo> steps = new ArrayList<>();
    final LayoutSpecLifecycleTester component =
        LayoutSpecLifecycleTester.create(c)
            .steps(steps)
            .visibleHeightRatio(1f)
            .widthPx(10)
            .heightPx(5)
            .build();

    mLegacyLithoViewRule
        .setRoot(component)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(10, EXACTLY), makeSizeSpec(10, EXACTLY))
        .measure()
        .layout();

    assertThat(LifecycleStep.getSteps(steps))
        .describedAs("Visible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_VISIBLE);
  }

  @Test
  public void testInvisibleEvent() {
    final ComponentContext c = mLegacyLithoViewRule.getContext();
    final List<LifecycleStep.StepInfo> steps = new ArrayList<>();
    final LayoutSpecLifecycleTester component =
        LayoutSpecLifecycleTester.create(c)
            .steps(steps)
            .widthPx(10)
            .heightPx(5)
            .marginPx(YogaEdge.TOP, 5)
            .build();
    mLegacyLithoViewRule
        .setRoot(component)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(10, EXACTLY), makeSizeSpec(10, EXACTLY))
        .measure()
        .layout();

    assertThat(LifecycleStep.getSteps(steps))
        .describedAs("Invisible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_INVISIBLE);

    mLithoView.notifyVisibleBoundsChanged(new Rect(LEFT, 0, RIGHT, 5), true);
    assertThat(LifecycleStep.getSteps(steps))
        .describedAs("Invisible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_INVISIBLE);
  }

  @Test
  public void testVisibleRectChangedEventItemVisible() {
    final TestComponent content = create(mContext).build();
    final EventHandler<VisibilityChangedEvent> visibilityChangedHandler =
        new EventHandler<>(content, 3);
    final Component root =
        Column.create(mContext)
            .child(
                Wrapper.create(mContext)
                    .delegate(content)
                    .visibilityChangedHandler(visibilityChangedHandler)
                    .widthPx(10)
                    .heightPx(10))
            .build();
    mLegacyLithoViewRule
        .setRoot(root)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(10, EXACTLY), makeSizeSpec(10, EXACTLY))
        .measure()
        .layout();

    VisibilityChangedEvent visibilityChangedEvent =
        (VisibilityChangedEvent) content.getEventState(visibilityChangedHandler);
    assertThat(visibilityChangedEvent.visibleHeight).isEqualTo(10);
    assertThat(visibilityChangedEvent.visibleWidth).isEqualTo(10);
    assertThat(visibilityChangedEvent.percentVisibleHeight).isEqualTo(100f);
    assertThat(visibilityChangedEvent.percentVisibleWidth).isEqualTo(100f);

    content.getDispatchedEventHandlers().clear();

    mLithoView.notifyVisibleBoundsChanged(new Rect(LEFT, 0, RIGHT, 4), true);
    assertThat(content.getDispatchedEventHandlers()).contains(visibilityChangedHandler);

    visibilityChangedEvent =
        (VisibilityChangedEvent) content.getEventState(visibilityChangedHandler);

    assertThat(visibilityChangedEvent.visibleHeight).isEqualTo(4);
    assertThat(visibilityChangedEvent.visibleWidth).isEqualTo(10);
    assertThat(visibilityChangedEvent.percentVisibleHeight).isEqualTo(40f);
    assertThat(visibilityChangedEvent.percentVisibleWidth).isEqualTo(100f);

    mLithoView.notifyVisibleBoundsChanged(new Rect(LEFT, 0, RIGHT, 5), true);
    assertThat(content.getDispatchedEventHandlers()).contains(visibilityChangedHandler);

    visibilityChangedEvent =
        (VisibilityChangedEvent) content.getEventState(visibilityChangedHandler);

    assertThat(visibilityChangedEvent.visibleHeight).isEqualTo(5);
    assertThat(visibilityChangedEvent.visibleWidth).isEqualTo(10);
    assertThat(visibilityChangedEvent.percentVisibleHeight).isEqualTo(50f);
    assertThat(visibilityChangedEvent.percentVisibleWidth).isEqualTo(100f);
  }

  @Test
  public void testVisibleRectChangedEventItemNotVisible() {
    final TestComponent content = create(mContext).build();
    final EventHandler<VisibilityChangedEvent> visibilityChangedHandler =
        new EventHandler<>(content, 3);
    final Component root =
        Column.create(mContext)
            .child(
                Wrapper.create(mContext)
                    .delegate(content)
                    .visibilityChangedHandler(visibilityChangedHandler)
                    .widthPx(10)
                    .heightPx(5)
                    .marginPx(YogaEdge.TOP, 5))
            .build();
    mLegacyLithoViewRule
        .setRoot(root)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(10, EXACTLY), makeSizeSpec(10, EXACTLY))
        .measure()
        .layout();

    content.getDispatchedEventHandlers().clear();

    mLithoView.notifyVisibleBoundsChanged(new Rect(LEFT, 0, RIGHT, 5), true);
    assertThat(content.getDispatchedEventHandlers()).contains(visibilityChangedHandler);

    VisibilityChangedEvent visibilityChangedEvent =
        (VisibilityChangedEvent) content.getEventState(visibilityChangedHandler);

    assertThat(visibilityChangedEvent.visibleHeight).isEqualTo(0);
    assertThat(visibilityChangedEvent.visibleWidth).isEqualTo(0);
    assertThat(visibilityChangedEvent.percentVisibleHeight).isEqualTo(0.0f);
    assertThat(visibilityChangedEvent.percentVisibleWidth).isEqualTo(0.0f);
  }

  @Test
  public void whenItemIsFullyVisible_VisibleTopAndLeftShouldBe0() {
    final TestComponent content = create(mContext).build();
    final EventHandler<VisibilityChangedEvent> visibilityChangedHandler =
        new EventHandler<>(content, 3);
    final Component root =
        Column.create(mContext)
            .child(
                Wrapper.create(mContext)
                    .delegate(content)
                    .visibilityChangedHandler(visibilityChangedHandler)
                    .widthPx(10)
                    .heightPx(10))
            .build();
    mLegacyLithoViewRule
        .setRoot(root)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(10, EXACTLY), makeSizeSpec(1000, EXACTLY))
        .measure()
        .layout();

    VisibilityChangedEvent visibilityChangedEvent =
        (VisibilityChangedEvent) content.getEventState(visibilityChangedHandler);
    assertThat(visibilityChangedEvent.visibleTop).isEqualTo(0);
    assertThat(visibilityChangedEvent.visibleLeft).isEqualTo(0);
    assertThat(visibilityChangedEvent.visibleHeight).isEqualTo(10);
    assertThat(visibilityChangedEvent.visibleWidth).isEqualTo(10);
    assertThat(visibilityChangedEvent.percentVisibleHeight).isEqualTo(100f);
    assertThat(visibilityChangedEvent.percentVisibleWidth).isEqualTo(100f);
  }

  @Test
  public void testVisibleRectChangedEventLargeView() {
    final TestComponent content = create(mContext).build();
    final EventHandler<VisibilityChangedEvent> visibilityChangedHandler =
        new EventHandler<>(content, 3);
    final Component root =
        Column.create(mContext)
            .child(
                Wrapper.create(mContext)
                    .delegate(content)
                    .visibilityChangedHandler(visibilityChangedHandler)
                    .widthPx(10)
                    .heightPx(10))
            .build();
    mLegacyLithoViewRule
        .setRoot(root)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(10, EXACTLY), makeSizeSpec(1000, EXACTLY))
        .measure()
        .layout();

    VisibilityChangedEvent visibilityChangedEvent =
        (VisibilityChangedEvent) content.getEventState(visibilityChangedHandler);
    assertThat(visibilityChangedEvent.visibleTop).isEqualTo(0);
    assertThat(visibilityChangedEvent.visibleLeft).isEqualTo(0);
    assertThat(visibilityChangedEvent.visibleHeight).isEqualTo(10);
    assertThat(visibilityChangedEvent.visibleWidth).isEqualTo(10);
    assertThat(visibilityChangedEvent.percentVisibleHeight).isEqualTo(100f);
    assertThat(visibilityChangedEvent.percentVisibleWidth).isEqualTo(100f);

    content.getDispatchedEventHandlers().clear();

    mLithoView.notifyVisibleBoundsChanged(new Rect(LEFT, 0, RIGHT, 4), true);
    assertThat(content.getDispatchedEventHandlers()).contains(visibilityChangedHandler);

    visibilityChangedEvent =
        (VisibilityChangedEvent) content.getEventState(visibilityChangedHandler);

    assertThat(visibilityChangedEvent.visibleHeight).isEqualTo(4);
    assertThat(visibilityChangedEvent.visibleWidth).isEqualTo(10);
    assertThat(visibilityChangedEvent.percentVisibleHeight).isEqualTo(40f);
    assertThat(visibilityChangedEvent.percentVisibleWidth).isEqualTo(100f);

    mLithoView.notifyVisibleBoundsChanged(new Rect(LEFT, 0, RIGHT, 5), true);
    assertThat(content.getDispatchedEventHandlers()).contains(visibilityChangedHandler);

    visibilityChangedEvent =
        (VisibilityChangedEvent) content.getEventState(visibilityChangedHandler);

    assertThat(visibilityChangedEvent.visibleHeight).isEqualTo(5);
    assertThat(visibilityChangedEvent.visibleWidth).isEqualTo(10);
    assertThat(visibilityChangedEvent.percentVisibleHeight).isEqualTo(50f);
    assertThat(visibilityChangedEvent.percentVisibleWidth).isEqualTo(100f);

    mLithoView.notifyVisibleBoundsChanged(new Rect(5, 5, 10, 10), true);
    assertThat(content.getDispatchedEventHandlers()).contains(visibilityChangedHandler);

    visibilityChangedEvent =
        (VisibilityChangedEvent) content.getEventState(visibilityChangedHandler);

    assertThat(visibilityChangedEvent.visibleTop).isEqualTo(5);
    assertThat(visibilityChangedEvent.visibleLeft).isEqualTo(5);
    assertThat(visibilityChangedEvent.visibleHeight).isEqualTo(5);
    assertThat(visibilityChangedEvent.visibleWidth).isEqualTo(5);
    assertThat(visibilityChangedEvent.percentVisibleHeight).isEqualTo(50f);
    assertThat(visibilityChangedEvent.percentVisibleWidth).isEqualTo(50f);
  }

  @Test
  public void testVisibleAndInvisibleEvents() {
    final ComponentContext c = mLegacyLithoViewRule.getContext();
    final List<LifecycleStep.StepInfo> steps = new ArrayList<>();
    final LayoutSpecLifecycleTester component =
        LayoutSpecLifecycleTester.create(c)
            .steps(steps)
            .widthPx(10)
            .heightPx(5)
            .marginPx(YogaEdge.TOP, 5)
            .build();

    mLegacyLithoViewRule
        .setRoot(component)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(10, EXACTLY), makeSizeSpec(10, EXACTLY))
        .measure()
        .layout();

    assertThat(LifecycleStep.getSteps(steps))
        .describedAs("Visible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps))
        .describedAs("Invisible event should be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_INVISIBLE);

    steps.clear();
    mLegacyLithoViewRule
        .getLithoView()
        .notifyVisibleBoundsChanged(new Rect(LEFT, 0, RIGHT, 5), true);
    assertThat(LifecycleStep.getSteps(steps))
        .describedAs("Invisible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_INVISIBLE);
    assertThat(LifecycleStep.getSteps(steps))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);

    steps.clear();
    mLegacyLithoViewRule
        .getLithoView()
        .notifyVisibleBoundsChanged(new Rect(LEFT, 3, RIGHT, 9), true);
    assertThat(LifecycleStep.getSteps(steps))
        .describedAs("Visible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps))
        .describedAs("Invisible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_INVISIBLE);

    steps.clear();
    mLegacyLithoViewRule
        .getLithoView()
        .notifyVisibleBoundsChanged(new Rect(LEFT, 10, RIGHT, 15), true);
    assertThat(LifecycleStep.getSteps(steps))
        .describedAs("Invisible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_INVISIBLE);
    assertThat(LifecycleStep.getSteps(steps))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);
  }

  @Test
  public void testMultipleVisibleEvents() {
    final ComponentContext c = mLegacyLithoViewRule.getContext();
    final List<LifecycleStep.StepInfo> steps1 = new ArrayList<>();
    final List<LifecycleStep.StepInfo> steps2 = new ArrayList<>();
    final List<LifecycleStep.StepInfo> steps3 = new ArrayList<>();

    final LayoutSpecLifecycleTester component1 =
        LayoutSpecLifecycleTester.create(c).steps(steps1).widthPx(10).heightPx(5).build();
    final LayoutSpecLifecycleTester component2 =
        LayoutSpecLifecycleTester.create(c).steps(steps2).widthPx(10).heightPx(5).build();
    final LayoutSpecLifecycleTester component3 =
        LayoutSpecLifecycleTester.create(c).steps(steps3).widthPx(10).heightPx(5).build();

    final Component root =
        Column.create(mContext)
            .key("root")
            .child(component1)
            .child(component2)
            .child(component3)
            .build();

    mLegacyLithoViewRule
        .setRoot(root)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(10, EXACTLY), makeSizeSpec(10, EXACTLY))
        .measure()
        .layout();

    assertThat(LifecycleStep.getSteps(steps1))
        .describedAs("Visible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps2))
        .describedAs("Visible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps3))
        .describedAs("Visible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps3))
        .describedAs("Visible event should be dispatched")
        .contains(LifecycleStep.ON_VISIBILITY_CHANGED);

    steps1.clear();
    steps2.clear();
    steps3.clear();
    mLegacyLithoViewRule
        .getLithoView()
        .notifyVisibleBoundsChanged(new Rect(LEFT, 0, RIGHT, 0), true);
    assertThat(LifecycleStep.getSteps(steps1))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps2))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps3))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps3))
        .describedAs("Visible event should be dispatched")
        .contains(LifecycleStep.ON_VISIBILITY_CHANGED);

    steps1.clear();
    steps2.clear();
    steps3.clear();
    mLegacyLithoViewRule
        .getLithoView()
        .notifyVisibleBoundsChanged(new Rect(LEFT, 0, RIGHT, 3), true);
    assertThat(LifecycleStep.getSteps(steps1))
        .describedAs("Visible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps2))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps3))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps3))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_VISIBILITY_CHANGED);

    steps1.clear();
    steps2.clear();
    mLegacyLithoViewRule
        .getLithoView()
        .notifyVisibleBoundsChanged(new Rect(LEFT, 3, RIGHT, 11), true);
    assertThat(LifecycleStep.getSteps(steps1))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps2))
        .describedAs("Visible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps3))
        .describedAs("Visible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps3))
        .describedAs("Visible event should be dispatched")
        .contains(LifecycleStep.ON_VISIBILITY_CHANGED);
  }

  @Test
  public void testMultipleVisibleAndInvisibleEvents() {
    final ComponentContext c = mLegacyLithoViewRule.getContext();
    final List<LifecycleStep.StepInfo> steps1 = new ArrayList<>();
    final List<LifecycleStep.StepInfo> steps2 = new ArrayList<>();
    final List<LifecycleStep.StepInfo> steps3 = new ArrayList<>();

    final LayoutSpecLifecycleTester component1 =
        LayoutSpecLifecycleTester.create(c).steps(steps1).widthPx(10).heightPx(5).build();
    final LayoutSpecLifecycleTester component2 =
        LayoutSpecLifecycleTester.create(c).steps(steps2).widthPx(10).heightPx(5).build();
    final LayoutSpecLifecycleTester component3 =
        LayoutSpecLifecycleTester.create(c).steps(steps3).widthPx(10).heightPx(5).build();

    final Component root =
        Column.create(mContext)
            .key("root")
            .child(component1)
            .child(component2)
            .child(component3)
            .build();

    mLegacyLithoViewRule
        .setRoot(root)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(15, EXACTLY), makeSizeSpec(15, EXACTLY))
        .measure()
        .layout();

    assertThat(LifecycleStep.getSteps(steps1))
        .describedAs("Visible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps2))
        .describedAs("Visible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps3))
        .describedAs("Visible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps1))
        .describedAs("Invisible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_INVISIBLE);
    assertThat(LifecycleStep.getSteps(steps2))
        .describedAs("Invisible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_INVISIBLE);
    assertThat(LifecycleStep.getSteps(steps3))
        .describedAs("Invisible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_INVISIBLE);

    steps1.clear();
    steps2.clear();
    steps3.clear();
    mLegacyLithoViewRule
        .getLithoView()
        .notifyVisibleBoundsChanged(new Rect(LEFT, 0, RIGHT, 15), true);
    assertThat(LifecycleStep.getSteps(steps1))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps2))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps3))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps1))
        .describedAs("Invisible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_INVISIBLE);
    assertThat(LifecycleStep.getSteps(steps2))
        .describedAs("Invisible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_INVISIBLE);
    assertThat(LifecycleStep.getSteps(steps3))
        .describedAs("Invisible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_INVISIBLE);

    steps1.clear();
    steps2.clear();
    steps3.clear();
    mLegacyLithoViewRule
        .getLithoView()
        .notifyVisibleBoundsChanged(new Rect(LEFT, 0, RIGHT, 0), true);
    assertThat(LifecycleStep.getSteps(steps1))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps2))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps3))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps1))
        .describedAs("Invisible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_INVISIBLE);
    assertThat(LifecycleStep.getSteps(steps2))
        .describedAs("Invisible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_INVISIBLE);
    assertThat(LifecycleStep.getSteps(steps3))
        .describedAs("Invisible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_INVISIBLE);

    steps1.clear();
    steps2.clear();
    steps3.clear();
    mLegacyLithoViewRule
        .getLithoView()
        .notifyVisibleBoundsChanged(new Rect(LEFT, 0, RIGHT, 0), true);
    assertThat(LifecycleStep.getSteps(steps1))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps2))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps3))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps1))
        .describedAs("Invisible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_INVISIBLE);
    assertThat(LifecycleStep.getSteps(steps2))
        .describedAs("Invisible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_INVISIBLE);
    assertThat(LifecycleStep.getSteps(steps3))
        .describedAs("Invisible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_INVISIBLE);

    steps1.clear();
    steps2.clear();
    steps3.clear();
    mLegacyLithoViewRule
        .getLithoView()
        .notifyVisibleBoundsChanged(new Rect(LEFT, 0, RIGHT, 3), true);
    assertThat(LifecycleStep.getSteps(steps1))
        .describedAs("Visible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps2))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps3))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps1))
        .describedAs("Invisible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_INVISIBLE);
    assertThat(LifecycleStep.getSteps(steps2))
        .describedAs("Invisible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_INVISIBLE);
    assertThat(LifecycleStep.getSteps(steps3))
        .describedAs("Invisible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_INVISIBLE);

    steps1.clear();
    steps2.clear();
    steps3.clear();
    mLegacyLithoViewRule
        .getLithoView()
        .notifyVisibleBoundsChanged(new Rect(LEFT, 3, RIGHT, 11), true);
    assertThat(LifecycleStep.getSteps(steps1))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps2))
        .describedAs("Visible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps3))
        .describedAs("Visible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps1))
        .describedAs("Invisible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_INVISIBLE);
    assertThat(LifecycleStep.getSteps(steps2))
        .describedAs("Invisible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_INVISIBLE);
    assertThat(LifecycleStep.getSteps(steps3))
        .describedAs("Invisible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_INVISIBLE);

    steps1.clear();
    steps2.clear();
    steps3.clear();
    mLegacyLithoViewRule
        .getLithoView()
        .notifyVisibleBoundsChanged(new Rect(LEFT, 5, RIGHT, 11), true);
    assertThat(LifecycleStep.getSteps(steps1))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps2))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps3))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps1))
        .describedAs("Invisible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_INVISIBLE);
    assertThat(LifecycleStep.getSteps(steps2))
        .describedAs("Invisible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_INVISIBLE);
    assertThat(LifecycleStep.getSteps(steps3))
        .describedAs("Invisible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_INVISIBLE);
  }

  @Test
  public void testSkipFullyVisible() {
    final ComponentContext c = mLegacyLithoViewRule.getContext();
    final List<LifecycleStep.StepInfo> steps1 = new ArrayList<>();
    final List<LifecycleStep.StepInfo> steps2 = new ArrayList<>();
    final List<LifecycleStep.StepInfo> steps3 = new ArrayList<>();

    final LayoutSpecLifecycleTester component1 =
        LayoutSpecLifecycleTester.create(c).steps(steps1).widthPx(10).heightPx(5).build();
    final LayoutSpecLifecycleTester component2 =
        LayoutSpecLifecycleTester.create(c).steps(steps2).widthPx(10).heightPx(5).build();
    final LayoutSpecLifecycleTester component3 =
        LayoutSpecLifecycleTester.create(c).steps(steps3).widthPx(10).heightPx(5).build();

    final Component root =
        Column.create(mContext)
            .key("root")
            .child(component1)
            .child(component2)
            .child(component3)
            .build();

    mLegacyLithoViewRule
        .setRoot(root)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(15, EXACTLY), makeSizeSpec(15, EXACTLY))
        .measure()
        .layout();

    Map<String, VisibilityItem> visibilityItemMap = getVisibilityIdToItemMap();
    for (String key : visibilityItemMap.keySet()) {
      VisibilityItem item = visibilityItemMap.get(key);
      assertThat(item.wasFullyVisible()).isTrue();
    }

    assertThat(LifecycleStep.getSteps(steps1))
        .describedAs("Visible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps2))
        .describedAs("Visible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps3))
        .describedAs("Visible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps1))
        .describedAs("Invisible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_INVISIBLE);
    assertThat(LifecycleStep.getSteps(steps2))
        .describedAs("Invisible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_INVISIBLE);
    assertThat(LifecycleStep.getSteps(steps3))
        .describedAs("Invisible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_INVISIBLE);

    steps1.clear();
    steps2.clear();
    steps3.clear();

    mLegacyLithoViewRule
        .getLithoView()
        .notifyVisibleBoundsChanged(new Rect(LEFT, 0, RIGHT, 15), true);
    assertThat(LifecycleStep.getSteps(steps1))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps2))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps3))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps1))
        .describedAs("Invisible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_INVISIBLE);
    assertThat(LifecycleStep.getSteps(steps2))
        .describedAs("Invisible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_INVISIBLE);
    assertThat(LifecycleStep.getSteps(steps3))
        .describedAs("Invisible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_INVISIBLE);
    //
    visibilityItemMap = getVisibilityIdToItemMap();
    assertThat(visibilityItemMap.size()).isEqualTo(3);
    for (String key : visibilityItemMap.keySet()) {
      VisibilityItem item = visibilityItemMap.get(key);
      assertThat(item.wasFullyVisible()).isTrue();
    }

    steps1.clear();
    steps2.clear();
    steps3.clear();

    mLegacyLithoViewRule
        .getLithoView()
        .notifyVisibleBoundsChanged(new Rect(LEFT, 3, RIGHT, 12), true);
    assertThat(LifecycleStep.getSteps(steps1))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps2))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps3))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps1))
        .describedAs("Invisible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_INVISIBLE);
    assertThat(LifecycleStep.getSteps(steps2))
        .describedAs("Invisible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_INVISIBLE);
    assertThat(LifecycleStep.getSteps(steps3))
        .describedAs("Invisible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_INVISIBLE);

    visibilityItemMap = getVisibilityIdToItemMap();
    assertThat(visibilityItemMap.size()).isEqualTo(3);
    int fullyVisibleCount = 0;
    for (String key : visibilityItemMap.keySet()) {
      VisibilityItem item = visibilityItemMap.get(key);
      fullyVisibleCount += item.wasFullyVisible() ? 1 : 0;
    }
    assertThat(fullyVisibleCount).isEqualTo(1);

    steps1.clear();
    steps2.clear();
    steps3.clear();

    mLegacyLithoViewRule
        .getLithoView()
        .notifyVisibleBoundsChanged(new Rect(LEFT, 0, RIGHT, 0), true);
    assertThat(LifecycleStep.getSteps(steps1))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps2))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps3))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps1))
        .describedAs("Invisible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_INVISIBLE);
    assertThat(LifecycleStep.getSteps(steps2))
        .describedAs("Invisible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_INVISIBLE);
    assertThat(LifecycleStep.getSteps(steps3))
        .describedAs("Invisible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_INVISIBLE);

    visibilityItemMap = getVisibilityIdToItemMap();
    assertThat(visibilityItemMap.size()).isEqualTo(0);

    steps1.clear();
    steps2.clear();
    steps3.clear();

    mLegacyLithoViewRule
        .getLithoView()
        .notifyVisibleBoundsChanged(new Rect(LEFT, 0, RIGHT, 0), true);
    assertThat(LifecycleStep.getSteps(steps1))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps2))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps3))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps1))
        .describedAs("Invisible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_INVISIBLE);
    assertThat(LifecycleStep.getSteps(steps2))
        .describedAs("Invisible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_INVISIBLE);
    assertThat(LifecycleStep.getSteps(steps3))
        .describedAs("Invisible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_INVISIBLE);

    steps1.clear();
    steps2.clear();
    steps3.clear();

    mLegacyLithoViewRule
        .getLithoView()
        .notifyVisibleBoundsChanged(new Rect(LEFT, 3, RIGHT, 12), true);
    assertThat(LifecycleStep.getSteps(steps1))
        .describedAs("Visible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps2))
        .describedAs("Visible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps3))
        .describedAs("Visible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps1))
        .describedAs("Invisible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_INVISIBLE);
    assertThat(LifecycleStep.getSteps(steps2))
        .describedAs("Invisible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_INVISIBLE);
    assertThat(LifecycleStep.getSteps(steps3))
        .describedAs("Invisible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_INVISIBLE);

    visibilityItemMap = getVisibilityIdToItemMap();
    assertThat(visibilityItemMap.size()).isEqualTo(3);
    fullyVisibleCount = 0;
    for (String key : visibilityItemMap.keySet()) {
      VisibilityItem item = visibilityItemMap.get(key);
      fullyVisibleCount += item.wasFullyVisible() ? 1 : 0;
    }
    assertThat(fullyVisibleCount).isEqualTo(1);

    steps1.clear();
    steps2.clear();
    steps3.clear();

    mLegacyLithoViewRule
        .getLithoView()
        .notifyVisibleBoundsChanged(new Rect(LEFT, 0, RIGHT, 15), true);
    assertThat(LifecycleStep.getSteps(steps1))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps2))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps3))
        .describedAs("Visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps1))
        .describedAs("Invisible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_INVISIBLE);
    assertThat(LifecycleStep.getSteps(steps2))
        .describedAs("Invisible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_INVISIBLE);
    assertThat(LifecycleStep.getSteps(steps3))
        .describedAs("Invisible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_INVISIBLE);

    visibilityItemMap = getVisibilityIdToItemMap();
    assertThat(visibilityItemMap.size()).isEqualTo(3);
    for (String key : visibilityItemMap.keySet()) {
      VisibilityItem item = visibilityItemMap.get(key);
      assertThat(item.wasFullyVisible()).isTrue();
    }
  }

  @Test
  public void testDispatchFocusedHandler() {
    final ComponentContext c = mLegacyLithoViewRule.getContext();
    final List<LifecycleStep.StepInfo> steps1 = new ArrayList<>();
    final List<LifecycleStep.StepInfo> steps2 = new ArrayList<>();
    final List<LifecycleStep.StepInfo> steps3 = new ArrayList<>();

    final LayoutSpecLifecycleTester component1 =
        LayoutSpecLifecycleTester.create(c).steps(steps1).widthPx(10).heightPx(5).build();
    final LayoutSpecLifecycleTester component2 =
        LayoutSpecLifecycleTester.create(c).steps(steps2).widthPx(10).heightPx(5).build();
    final LayoutSpecLifecycleTester component3 =
        LayoutSpecLifecycleTester.create(c).steps(steps3).widthPx(10).heightPx(5).build();

    final Component root =
        Column.create(mContext)
            .key("root")
            .child(component1)
            .child(component2)
            .child(component3)
            .build();

    mLegacyLithoViewRule
        .setRoot(root)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(10, EXACTLY), makeSizeSpec(15, EXACTLY))
        .measure()
        .layout();

    Map<String, VisibilityItem> visibilityItemLongSparseArray = getVisibilityIdToItemMap();
    for (String key : visibilityItemLongSparseArray.keySet()) {
      VisibilityItem item = visibilityItemLongSparseArray.get(key);
      assertThat(item.wasFullyVisible()).isTrue();
    }

    assertThat(LifecycleStep.getSteps(steps1))
        .describedAs("Focused visible event should be dispatched")
        .contains(LifecycleStep.ON_FOCUSED_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps2))
        .describedAs("Focused visible event should be dispatched")
        .contains(LifecycleStep.ON_FOCUSED_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps3))
        .describedAs("Focused visible event should be dispatched")
        .contains(LifecycleStep.ON_FOCUSED_EVENT_VISIBLE);

    assertThat(LifecycleStep.getSteps(steps1))
        .describedAs("Unfocused visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_UNFOCUSED_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps2))
        .describedAs("Unfocused visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_UNFOCUSED_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps3))
        .describedAs("Unfocused visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_UNFOCUSED_EVENT_VISIBLE);

    steps1.clear();
    steps2.clear();
    steps3.clear();
    mLegacyLithoViewRule
        .getLithoView()
        .notifyVisibleBoundsChanged(new Rect(LEFT, 4, RIGHT, 15), true);

    assertThat(LifecycleStep.getSteps(steps1))
        .describedAs("Focused visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_FOCUSED_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps2))
        .describedAs("Focused visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_FOCUSED_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps3))
        .describedAs("Focused visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_FOCUSED_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps1))
        .describedAs("Unfocused visible event should be dispatched")
        .contains(LifecycleStep.ON_UNFOCUSED_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps2))
        .describedAs("Unfocused visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_UNFOCUSED_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps3))
        .describedAs("Unfocused visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_UNFOCUSED_EVENT_VISIBLE);

    steps1.clear();
    steps2.clear();
    steps3.clear();
    mLegacyLithoViewRule
        .getLithoView()
        .notifyVisibleBoundsChanged(new Rect(LEFT, 0, RIGHT, 15), true);

    assertThat(LifecycleStep.getSteps(steps1))
        .describedAs("Focused visible event should be dispatched")
        .contains(LifecycleStep.ON_FOCUSED_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps2))
        .describedAs("Focused visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_FOCUSED_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps3))
        .describedAs("Focused visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_FOCUSED_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps1))
        .describedAs("Unfocused visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_UNFOCUSED_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps2))
        .describedAs("Unfocused visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_UNFOCUSED_EVENT_VISIBLE);
    assertThat(LifecycleStep.getSteps(steps3))
        .describedAs("Unfocused visible event should not be dispatched")
        .doesNotContain(LifecycleStep.ON_UNFOCUSED_EVENT_VISIBLE);
  }

  @Test
  public void testDetachWithReleasedTreeTriggersInvisibilityItems() {
    final TestComponent content = create(mContext).build();
    final EventHandler<InvisibleEvent> invisibleEventHandler = new EventHandler<>(content, 2);
    final Component root =
        Column.create(mContext)
            .child(
                Wrapper.create(mContext)
                    .delegate(content)
                    .invisibleHandler(invisibleEventHandler)
                    .widthPx(10)
                    .heightPx(10))
            .build();

    mLegacyLithoViewRule
        .setRoot(root)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(100, EXACTLY), makeSizeSpec(100, EXACTLY))
        .measure()
        .layout();

    mLithoView.notifyVisibleBoundsChanged(new Rect(LEFT, 0, RIGHT, 10), true);
    mLithoView.release();

    assertThat(content.getDispatchedEventHandlers()).contains(invisibleEventHandler);
  }

  @Test
  public void testSetComponentWithDifferentKeyGeneratesVisibilityEvents() {
    final TestComponent component1 = create(mContext).key("component1").build();
    final EventHandler<VisibleEvent> visibleEventHandler1 = new EventHandler<>(component1, 1);
    final EventHandler<InvisibleEvent> invisibleEventHandler1 = new EventHandler<>(component1, 2);
    final EventHandler<FocusedVisibleEvent> focusedEventHandler1 =
        new EventHandler<>(component1, 3);
    final EventHandler<UnfocusedVisibleEvent> unfocusedEventHandler1 =
        new EventHandler<>(component1, 4);
    final Component root =
        Column.create(mContext)
            .child(
                Wrapper.create(mContext)
                    .delegate(component1)
                    .visibleHandler(visibleEventHandler1)
                    .invisibleHandler(invisibleEventHandler1)
                    .focusedHandler(focusedEventHandler1)
                    .unfocusedHandler(unfocusedEventHandler1)
                    .widthPx(10)
                    .heightPx(10))
            .build();

    mLegacyLithoViewRule
        .setRoot(root)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(100, EXACTLY), makeSizeSpec(100, EXACTLY))
        .measure()
        .layout();

    assertThat(component1.getDispatchedEventHandlers()).contains(visibleEventHandler1);
    assertThat(component1.getDispatchedEventHandlers()).contains(focusedEventHandler1);

    final TestComponent component2 = create(mContext).key("component2").build();
    final EventHandler<VisibleEvent> visibleEventHandler2 = new EventHandler<>(component2, 3);

    final Component newRoot =
        Column.create(mContext)
            .child(
                Wrapper.create(mContext)
                    .delegate(component2)
                    .visibleHandler(visibleEventHandler2)
                    .widthPx(10)
                    .heightPx(10))
            .build();
    mLegacyLithoViewRule.setRoot(newRoot);

    measureAndLayout(mLithoView);

    assertThat(component1.getDispatchedEventHandlers()).contains(invisibleEventHandler1);
    assertThat(component1.getDispatchedEventHandlers()).contains(unfocusedEventHandler1);
    assertThat(component2.getDispatchedEventHandlers()).contains(visibleEventHandler2);
  }

  @Test
  public void testSetDifferentComponentTreeWithSameKeysStillCallsInvisibleAndVisibleEvents() {
    final TestComponent firstComponent = create(mContext).build();
    final TestComponent secondComponent = create(mContext).build();
    final EventHandler<VisibleEvent> visibleEventHandler1 = new EventHandler<>(firstComponent, 1);
    final EventHandler<InvisibleEvent> invisibleEventHandler1 =
        new EventHandler<>(firstComponent, 2);
    final EventHandler<VisibleEvent> visibleEventHandler2 = new EventHandler<>(secondComponent, 1);
    final EventHandler<InvisibleEvent> invisibleEventHandler2 =
        new EventHandler<>(secondComponent, 2);

    final Component root =
        Column.create(mContext)
            .child(
                Wrapper.create(mContext)
                    .delegate(firstComponent)
                    .visibleHandler(visibleEventHandler1)
                    .invisibleHandler(invisibleEventHandler1)
                    .widthPx(10)
                    .heightPx(10))
            .build();

    mLegacyLithoViewRule
        .setRoot(root)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(100, EXACTLY), makeSizeSpec(100, EXACTLY))
        .measure()
        .layout();
    assertThat(firstComponent.getDispatchedEventHandlers()).containsExactly(visibleEventHandler1);

    firstComponent.getDispatchedEventHandlers().clear();

    final Component newRoot =
        Column.create(mContext)
            .child(
                Wrapper.create(mContext)
                    .delegate(secondComponent)
                    .visibleHandler(visibleEventHandler2)
                    .invisibleHandler(invisibleEventHandler2)
                    .widthPx(10)
                    .heightPx(10))
            .build();
    mLegacyLithoViewRule
        .useComponentTree(ComponentTree.create(mContext).build())
        .setRoot(newRoot)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(100, EXACTLY), makeSizeSpec(100, EXACTLY))
        .measure()
        .layout();

    assertThat(firstComponent.getDispatchedEventHandlers()).containsExactly(invisibleEventHandler1);
    assertThat(secondComponent.getDispatchedEventHandlers()).containsExactly(visibleEventHandler2);
  }

  @Test
  public void testSetComponentTreeToNullDispatchesInvisibilityEvents() {
    final TestComponent component = create(mContext).build();
    final EventHandler<VisibleEvent> visibleEventHandler = new EventHandler<>(component, 1);
    final EventHandler<InvisibleEvent> invisibleEventHandler = new EventHandler<>(component, 2);

    final Component root =
        Column.create(mContext)
            .child(
                Wrapper.create(mContext)
                    .delegate(component)
                    .visibleHandler(visibleEventHandler)
                    .invisibleHandler(invisibleEventHandler)
                    .widthPx(10)
                    .heightPx(10))
            .build();
    mLegacyLithoViewRule
        .useComponentTree(ComponentTree.create(mContext).build())
        .setRoot(root)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(100, EXACTLY), makeSizeSpec(100, EXACTLY))
        .measure()
        .layout();

    assertThat(component.getDispatchedEventHandlers()).containsExactly(visibleEventHandler);

    component.getDispatchedEventHandlers().clear();

    mLegacyLithoViewRule.useComponentTree(null);

    assertThat(component.getDispatchedEventHandlers()).containsExactly(invisibleEventHandler);
  }

  @Test
  public void testTransientStateDoesNotTriggerVisibilityEvents() {
    final TestComponent content = create(mContext).build();
    final EventHandler<VisibleEvent> visibleEventHandler = new EventHandler<>(content, 2);
    final Component root =
        Column.create(mContext)
            .child(
                Wrapper.create(mContext)
                    .delegate(content)
                    .visibleHandler(visibleEventHandler)
                    .widthPx(10)
                    .heightPx(10))
            .build();
    mLegacyLithoViewRule
        .useComponentTree(ComponentTree.create(mContext).build())
        .setRoot(root)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(100, EXACTLY), makeSizeSpec(100, EXACTLY))
        .measure()
        .layout();

    mLithoView.notifyVisibleBoundsChanged(new Rect(0, -10, 10, -5), true);
    content.getDispatchedEventHandlers().clear();

    mLithoView.setHasTransientState(true);
    assertThat(content.getDispatchedEventHandlers()).doesNotContain(visibleEventHandler);

    mLithoView.setMountStateDirty();
    mLithoView.notifyVisibleBoundsChanged(new Rect(0, -10, 10, -5), true);
    assertThat(content.getDispatchedEventHandlers()).doesNotContain(visibleEventHandler);

    mLithoView.setHasTransientState(false);
    assertThat(content.getDispatchedEventHandlers()).contains(visibleEventHandler);
  }

  @Test
  public void
      visibilityOutputs_setTransientStateFalse_parentInTransientState_processVisibilityOutputs() {
    final TestComponent content = create(mContext).build();
    final EventHandler<VisibleEvent> visibleEventHandler = new EventHandler<>(content, 2);
    final Component root =
        Column.create(mContext)
            .child(
                Wrapper.create(mContext)
                    .delegate(content)
                    .visibleHandler(visibleEventHandler)
                    .widthPx(10)
                    .heightPx(10))
            .build();

    final View view = (View) mLithoView.getParent();
    view.setHasTransientState(true);

    mLegacyLithoViewRule
        .useComponentTree(ComponentTree.create(mContext).build())
        .setRoot(root)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(100, EXACTLY), makeSizeSpec(100, EXACTLY))
        .measure()
        .layout();

    mLithoView.notifyVisibleBoundsChanged(new Rect(0, -10, 10, -5), true);
    content.getDispatchedEventHandlers().clear();

    mLithoView.setHasTransientState(true);
    assertThat(content.getDispatchedEventHandlers()).doesNotContain(visibleEventHandler);

    mLithoView.setMountStateDirty();
    mLithoView.notifyVisibleBoundsChanged(new Rect(0, -10, 10, -5), true);
    assertThat(content.getDispatchedEventHandlers()).doesNotContain(visibleEventHandler);

    mLithoView.setHasTransientState(false);
    assertThat(content.getDispatchedEventHandlers()).contains(visibleEventHandler);
    view.setHasTransientState(false);
  }

  @Test
  public void testRemovingComponentTriggersInvisible() {
    final TestComponent content = create(mContext).build();
    final EventHandler<VisibleEvent> visibleEventHandler = new EventHandler<>(content, 1);
    final EventHandler<InvisibleEvent> invisibleEventHandler = new EventHandler<>(content, 2);
    final Component wrappedContent =
        Wrapper.create(mContext)
            .delegate(content)
            .widthPx(10)
            .heightPx(5)
            .visibleHandler(visibleEventHandler)
            .invisibleHandler(invisibleEventHandler)
            .build();

    final Component root = Column.create(mContext).child(wrappedContent).build();
    mLegacyLithoViewRule
        .useComponentTree(ComponentTree.create(mContext).build())
        .setRoot(root)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(10, EXACTLY), makeSizeSpec(10, EXACTLY))
        .measure()
        .layout();

    assertThat(content.getDispatchedEventHandlers()).contains(visibleEventHandler);
    assertThat(content.getDispatchedEventHandlers()).doesNotContain(invisibleEventHandler);

    content.getDispatchedEventHandlers().clear();

    mLithoView.setComponent(Column.create(mContext).build());
    assertThat(content.getDispatchedEventHandlers()).contains(invisibleEventHandler);
    assertThat(content.getDispatchedEventHandlers()).doesNotContain(visibleEventHandler);

    content.getDispatchedEventHandlers().clear();
    mLithoView.setComponent(Column.create(mContext).child(wrappedContent).build());
    assertThat(content.getDispatchedEventHandlers()).contains(visibleEventHandler);
    assertThat(content.getDispatchedEventHandlers()).doesNotContain(invisibleEventHandler);
  }

  @Test
  public void testMultipleVisibilityEventsOnSameNode() {
    final ComponentContext context = mLegacyLithoViewRule.getContext();
    final LifecycleTracker lifecycleTracker1 = new LifecycleTracker();
    final LifecycleTracker lifecycleTracker2 = new LifecycleTracker();
    final LifecycleTracker lifecycleTracker3 = new LifecycleTracker();

    final ComponentWrapperTester content1 =
        ComponentWrapperTester.create(context)
            .content(Column.create(context).build())
            .lifecycleTracker(lifecycleTracker1)
            .build();
    final ComponentWrapperTester content2 =
        ComponentWrapperTester.create(context)
            .content(content1)
            .lifecycleTracker(lifecycleTracker2)
            .build();
    final ComponentWrapperTester content3 =
        ComponentWrapperTester.create(context)
            .content(content2)
            .lifecycleTracker(lifecycleTracker3)
            .build();

    mLegacyLithoViewRule
        .useComponentTree(ComponentTree.create(mContext).build())
        .setRoot(content3)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(10, EXACTLY), makeSizeSpec(10, EXACTLY))
        .measure()
        .layout();

    assertThat(lifecycleTracker1.getSteps())
        .describedAs("Visible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(lifecycleTracker2.getSteps())
        .describedAs("Visible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_VISIBLE);
    assertThat(lifecycleTracker3.getSteps())
        .describedAs("Visible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_VISIBLE);

    assertThat(lifecycleTracker1.getSteps())
        .describedAs("Focus event should be dispatched")
        .contains(LifecycleStep.ON_FOCUSED_EVENT_VISIBLE);
    assertThat(lifecycleTracker2.getSteps())
        .describedAs("Focus event should be dispatched")
        .contains(LifecycleStep.ON_FOCUSED_EVENT_VISIBLE);
    assertThat(lifecycleTracker3.getSteps())
        .describedAs("Focus event should be dispatched")
        .contains(LifecycleStep.ON_FOCUSED_EVENT_VISIBLE);

    assertThat(lifecycleTracker1.getSteps())
        .describedAs("FullImpressionVisible event should be dispatched")
        .contains(LifecycleStep.ON_FULL_IMPRESSION_VISIBLE_EVENT);
    assertThat(lifecycleTracker2.getSteps())
        .describedAs("FullImpressionVisible event should be dispatched")
        .contains(LifecycleStep.ON_FULL_IMPRESSION_VISIBLE_EVENT);
    assertThat(lifecycleTracker3.getSteps())
        .describedAs("FullImpressionVisible event should be dispatched")
        .contains(LifecycleStep.ON_FULL_IMPRESSION_VISIBLE_EVENT);

    lifecycleTracker1.reset();
    lifecycleTracker2.reset();
    lifecycleTracker3.reset();
    mLegacyLithoViewRule.getLithoView().unbind();

    assertThat(lifecycleTracker1.getSteps())
        .describedAs("Invisible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_INVISIBLE);
    assertThat(lifecycleTracker2.getSteps())
        .describedAs("Invisible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_INVISIBLE);
    assertThat(lifecycleTracker3.getSteps())
        .describedAs("Invisible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_INVISIBLE);

    assertThat(lifecycleTracker1.getSteps())
        .describedAs("Unfocus event should be dispatched")
        .contains(LifecycleStep.ON_UNFOCUSED_EVENT_VISIBLE);
    assertThat(lifecycleTracker2.getSteps())
        .describedAs("Unfocus event should be dispatched")
        .contains(LifecycleStep.ON_UNFOCUSED_EVENT_VISIBLE);
    assertThat(lifecycleTracker3.getSteps())
        .describedAs("Unfocus event should be dispatched")
        .contains(LifecycleStep.ON_UNFOCUSED_EVENT_VISIBLE);
  }

  @Test
  public void testSetVisibilityHint() {
    final TestComponent component = create(mContext).build();
    final EventHandler<VisibleEvent> visibleEventHandler = new EventHandler<>(component, 1);
    final EventHandler<InvisibleEvent> invisibleEventHandler = new EventHandler<>(component, 2);
    final EventHandler<FocusedVisibleEvent> focusedEventHandler = new EventHandler<>(component, 3);
    final EventHandler<UnfocusedVisibleEvent> unfocusedEventHandler =
        new EventHandler<>(component, 4);
    final EventHandler<FullImpressionVisibleEvent> fullImpressionHandler =
        new EventHandler<>(component, 5);

    final Component root =
        Column.create(mContext)
            .child(
                Wrapper.create(mContext)
                    .delegate(component)
                    .visibleHandler(visibleEventHandler)
                    .invisibleHandler(invisibleEventHandler)
                    .focusedHandler(focusedEventHandler)
                    .unfocusedHandler(unfocusedEventHandler)
                    .fullImpressionHandler(fullImpressionHandler)
                    .widthPx(10)
                    .heightPx(10))
            .build();

    mLegacyLithoViewRule
        .useComponentTree(ComponentTree.create(mContext).build())
        .setRoot(root)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(100, EXACTLY), makeSizeSpec(100, EXACTLY))
        .measure()
        .layout();

    assertThat(component.getDispatchedEventHandlers()).contains(visibleEventHandler);
    assertThat(component.getDispatchedEventHandlers()).contains(focusedEventHandler);
    assertThat(component.getDispatchedEventHandlers()).contains(fullImpressionHandler);

    component.getDispatchedEventHandlers().clear();
    mLithoView.setVisibilityHint(false, false);

    assertThat(component.getDispatchedEventHandlers()).contains(invisibleEventHandler);
    assertThat(component.getDispatchedEventHandlers()).contains(unfocusedEventHandler);

    component.getDispatchedEventHandlers().clear();
    mLithoView.setVisibilityHint(true, false);

    assertThat(component.getDispatchedEventHandlers()).contains(visibleEventHandler);
    assertThat(component.getDispatchedEventHandlers()).contains(focusedEventHandler);
    assertThat(component.getDispatchedEventHandlers()).contains(fullImpressionHandler);
  }

  @Test
  public void testSetVisibilityHintRecursive() {
    // TODO(festevezga, T68365308) - replace with SimpleMountSpecTesterSpec
    final TestComponent testComponentInner = TestDrawableComponent.create(mContext).build();
    final EventHandler<VisibleEvent> visibleEventHandlerInner =
        new EventHandler<>(testComponentInner, 1);
    final EventHandler<InvisibleEvent> invisibleEventHandlerInner =
        new EventHandler<>(testComponentInner, 2);

    final Component mountedTestComponentInner =
        Column.create(mContext)
            .child(
                Wrapper.create(mContext)
                    .delegate(testComponentInner)
                    .visibleHandler(visibleEventHandlerInner)
                    .invisibleHandler(invisibleEventHandlerInner)
                    .widthPx(10)
                    .heightPx(10))
            .build();

    mLegacyLithoViewRule
        .useComponentTree(ComponentTree.create(mContext).build())
        .setRoot(mountedTestComponentInner)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(100, EXACTLY), makeSizeSpec(100, EXACTLY))
        .measure()
        .layout();

    final LithoView child = mountComponent(mContext, mountedTestComponentInner, true, true);

    assertThat(testComponentInner.getDispatchedEventHandlers().size()).isEqualTo(1);
    assertThat(testComponentInner.getDispatchedEventHandlers().contains(visibleEventHandlerInner));
    testComponentInner.getDispatchedEventHandlers().clear();

    final ViewGroupWithLithoViewChildren viewGroup =
        new ViewGroupWithLithoViewChildren(mContext.getAndroidContext());

    final ViewGroup parent = (ViewGroup) child.getParent();
    parent.removeView(child);
    viewGroup.addView(child);

    final LithoView parentView =
        mountComponent(
            mContext, TestViewComponent.create(mContext).testView(viewGroup).build(), true, true);

    parentView.setVisibilityHint(false, false);

    assertThat(testComponentInner.getDispatchedEventHandlers().size()).isEqualTo(1);
    assertThat(
        testComponentInner.getDispatchedEventHandlers().contains(invisibleEventHandlerInner));
    testComponentInner.getDispatchedEventHandlers().clear();

    parentView.setVisibilityHint(true, false);

    assertThat(testComponentInner.getDispatchedEventHandlers().size()).isEqualTo(1);
    assertThat(testComponentInner.getDispatchedEventHandlers().contains(visibleEventHandlerInner));
  }

  @Test
  public void testMultipleVisibleEventsIncrementalMountDisabled() {
    final TestComponent content1 = create(mContext).build();
    final TestComponent content2 = create(mContext).build();
    final TestComponent content3 = create(mContext).build();
    final EventHandler<VisibleEvent> visibleEventHandler1 = new EventHandler<>(content1, 1);
    final EventHandler<VisibleEvent> visibleEventHandler2 = new EventHandler<>(content2, 2);
    final EventHandler<VisibleEvent> visibleEventHandler3 = new EventHandler<>(content3, 3);
    final EventHandler<VisibilityChangedEvent> visibilityChangedHandler =
        new EventHandler<>(content3, 4);

    final Component root =
        Column.create(mContext)
            .child(
                Wrapper.create(mContext)
                    .delegate(content1)
                    .visibleHandler(visibleEventHandler1)
                    .widthPx(10)
                    .heightPx(5))
            .child(
                Wrapper.create(mContext)
                    .delegate(content2)
                    .visibleHandler(visibleEventHandler2)
                    .widthPx(10)
                    .heightPx(5))
            .child(
                Wrapper.create(mContext)
                    .delegate(content3)
                    .visibleHandler(visibleEventHandler3)
                    .visibilityChangedHandler(visibilityChangedHandler)
                    .widthPx(10)
                    .heightPx(5))
            .build();

    mLegacyLithoViewRule
        .useComponentTree(ComponentTree.create(mContext).incrementalMount(false).build())
        .setRoot(root)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(10, EXACTLY), makeSizeSpec(10, EXACTLY))
        .measure()
        .layout();

    assertThat(content1.getDispatchedEventHandlers()).contains(visibleEventHandler1);
    assertThat(content2.getDispatchedEventHandlers()).contains(visibleEventHandler2);
    assertThat(content3.getDispatchedEventHandlers()).contains(visibleEventHandler3);
    assertThat(content3.getDispatchedEventHandlers()).contains(visibilityChangedHandler);

    content1.getDispatchedEventHandlers().clear();
    content2.getDispatchedEventHandlers().clear();
    content3.getDispatchedEventHandlers().clear();
    mLithoView.notifyVisibleBoundsChanged(new Rect(LEFT, 0, RIGHT, 0), true);
    assertThat(content1.getDispatchedEventHandlers()).doesNotContain(visibleEventHandler1);
    assertThat(content2.getDispatchedEventHandlers()).doesNotContain(visibleEventHandler2);
    assertThat(content3.getDispatchedEventHandlers()).doesNotContain(visibleEventHandler3);
    assertThat(content3.getDispatchedEventHandlers()).contains(visibilityChangedHandler);

    content1.getDispatchedEventHandlers().clear();
    content2.getDispatchedEventHandlers().clear();
    content3.getDispatchedEventHandlers().clear();
    mLithoView.notifyVisibleBoundsChanged(new Rect(LEFT, 0, RIGHT, 3), true);
    assertThat(content1.getDispatchedEventHandlers()).contains(visibleEventHandler1);
    assertThat(content2.getDispatchedEventHandlers()).doesNotContain(visibleEventHandler2);
    assertThat(content3.getDispatchedEventHandlers()).doesNotContain(visibleEventHandler3);
    assertThat(content3.getDispatchedEventHandlers()).doesNotContain(visibilityChangedHandler);

    content1.getDispatchedEventHandlers().clear();
    content2.getDispatchedEventHandlers().clear();
    mLithoView.notifyVisibleBoundsChanged(new Rect(LEFT, 3, RIGHT, 11), true);
    assertThat(content1.getDispatchedEventHandlers()).doesNotContain(visibleEventHandler1);
    assertThat(content2.getDispatchedEventHandlers()).contains(visibleEventHandler2);
    assertThat(content3.getDispatchedEventHandlers()).contains(visibleEventHandler3);
    assertThat(content3.getDispatchedEventHandlers()).contains(visibilityChangedHandler);
  }

  @Test
  public void testSetVisibilityHintIncrementalMountDisabled() {
    final TestComponent component = create(mContext).build();
    final EventHandler<VisibleEvent> visibleEventHandler = new EventHandler<>(component, 1);
    final EventHandler<InvisibleEvent> invisibleEventHandler = new EventHandler<>(component, 2);
    final EventHandler<FocusedVisibleEvent> focusedEventHandler = new EventHandler<>(component, 3);
    final EventHandler<UnfocusedVisibleEvent> unfocusedEventHandler =
        new EventHandler<>(component, 4);
    final EventHandler<FullImpressionVisibleEvent> fullImpressionHandler =
        new EventHandler<>(component, 5);

    final Component root =
        Column.create(mContext)
            .child(
                Wrapper.create(mContext)
                    .delegate(component)
                    .visibleHandler(visibleEventHandler)
                    .invisibleHandler(invisibleEventHandler)
                    .focusedHandler(focusedEventHandler)
                    .unfocusedHandler(unfocusedEventHandler)
                    .fullImpressionHandler(fullImpressionHandler)
                    .widthPx(10)
                    .heightPx(10))
            .build();

    mLegacyLithoViewRule
        .useComponentTree(ComponentTree.create(mContext).incrementalMount(false).build())
        .setRoot(root)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(100, EXACTLY), makeSizeSpec(100, EXACTLY))
        .measure()
        .layout();

    assertThat(component.getDispatchedEventHandlers()).contains(visibleEventHandler);
    assertThat(component.getDispatchedEventHandlers()).contains(focusedEventHandler);
    assertThat(component.getDispatchedEventHandlers()).contains(fullImpressionHandler);

    component.getDispatchedEventHandlers().clear();
    mLithoView.setVisibilityHint(false, false);

    assertThat(component.getDispatchedEventHandlers()).contains(invisibleEventHandler);
    assertThat(component.getDispatchedEventHandlers()).contains(unfocusedEventHandler);

    component.getDispatchedEventHandlers().clear();
    mLithoView.setVisibilityHint(true, false);

    assertThat(component.getDispatchedEventHandlers()).contains(visibleEventHandler);
    assertThat(component.getDispatchedEventHandlers()).contains(focusedEventHandler);
    assertThat(component.getDispatchedEventHandlers()).contains(fullImpressionHandler);
  }

  @Test
  public void testVisibilityProcessingNoScrollChange() {

    // TODO(T118124771): Test failure because of incorrect visible bounds
    if (ComponentsConfiguration.lithoViewSelfManageViewPortChanges) {
      return;
    }

    final TestComponent content = create(mContext).build();
    final EventHandler<VisibleEvent> visibleEventHandler = new EventHandler<>(content, 2);
    final Component root =
        Column.create(mContext)
            .child(
                Wrapper.create(mContext)
                    .delegate(content)
                    .visibleHandler(visibleEventHandler)
                    .widthPx(10)
                    .heightPx(5)
                    .marginPx(YogaEdge.TOP, 5))
            .build();

    mLegacyLithoViewRule
        .useComponentTree(ComponentTree.create(mContext).incrementalMount(false).build())
        .setRoot(root)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(10, EXACTLY), makeSizeSpec(5, EXACTLY))
        .measure()
        .layout();

    assertThat(content.getDispatchedEventHandlers()).doesNotContain(visibleEventHandler);

    mLithoView.setBottom(10);
    mLithoView.performLayout(true, 0, 0, RIGHT, 10);

    assertThat(content.getDispatchedEventHandlers()).contains(visibleEventHandler);
  }

  @Test
  public void setNewComponentTree_noMount_noVisibilityEventsDispatched() {
    final TestComponent content = create(mContext).build();
    final EventHandler<VisibleEvent> visibleEventHandler = new EventHandler<>(content, 2);
    final EventHandler<InvisibleEvent> invisibleEventHandler = new EventHandler<>(content, 1);

    final Component root =
        Column.create(mContext)
            .child(
                Wrapper.create(mContext)
                    .delegate(content)
                    .visibleHandler(visibleEventHandler)
                    .invisibleHandler(invisibleEventHandler)
                    .widthPx(10)
                    .heightPx(5)
                    .marginPx(YogaEdge.TOP, 5))
            .build();

    final ComponentTree componentTree = ComponentTree.create(mContext, root).build();
    mLithoView.setComponentTree(componentTree);

    assertThat(content.getDispatchedEventHandlers()).doesNotContain(visibleEventHandler);

    content.getDispatchedEventHandlers().clear();

    final ComponentTree newComponentTree = ComponentTree.create(mContext, root).build();
    mLithoView.setComponentTree(newComponentTree);

    assertThat(content.getDispatchedEventHandlers()).doesNotContain(invisibleEventHandler);
    assertThat(content.getDispatchedEventHandlers()).doesNotContain(visibleEventHandler);
  }

  @Test
  public void processVisibility_componentIsMounted() {
    final Output<Object> textDrawableOutput = new Output<>();
    final Output<Object> viewOutput = new Output<>();
    final Output<Object> nullOutput = new Output<>();

    final Component root =
        Column.create(mContext)
            .child(
                LayoutSpecVisibilityEventTester.create(mContext)
                    .textOutput(textDrawableOutput)
                    .viewOutput(viewOutput)
                    .nullOutput(nullOutput))
            .build();

    mLegacyLithoViewRule
        .setRoot(root)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(100, EXACTLY), makeSizeSpec(100, EXACTLY))
        .measure()
        .layout();

    assertThat(textDrawableOutput.get()).isNotNull();
    assertThat(textDrawableOutput.get()).isInstanceOf(TextDrawable.class);

    assertThat(viewOutput.get()).isNotNull();
    assertThat(viewOutput.get()).isInstanceOf(ComponentHost.class);

    assertThat(nullOutput.get()).isNull();
  }

  private Map<String, VisibilityItem> getVisibilityIdToItemMap() {
    LithoHostListenerCoordinator lithoHostListenerCoordinator =
        Whitebox.getInternalState(mLithoView, "mLithoHostListenerCoordinator");
    ExtensionState<VisibilityMountExtension.VisibilityMountExtensionState> extensionState =
        Whitebox.getInternalState(lithoHostListenerCoordinator, "mVisibilityExtensionState");

    if (extensionState != null) {
      return VisibilityMountExtension.getVisibilityIdToItemMap(extensionState);
    }

    return null;
  }

  private static Component createHorizontalScrollChildren(
      ComponentContext c, final int numberOfItems, List<List<LifecycleStep.StepInfo>> stepsList) {
    final Row.Builder rowBuilder = Row.create(c);
    for (int i = 0; i < numberOfItems; ++i) {
      List<LifecycleStep.StepInfo> steps = new ArrayList<>();
      final LayoutSpecLifecycleTester component =
          LayoutSpecLifecycleTester.create(c)
              .steps(steps)
              .widthPx(10)
              .heightPx(5)
              .marginPx(YogaEdge.TOP, 5)
              .build();
      rowBuilder.child(component);

      stepsList.add(steps);
    }
    return rowBuilder.build();
  }

  @Test
  public void testVisibleEventHorizontalScroll() {

    // TODO(T118124771): Test failure because of incorrect visible bounds
    if (ComponentsConfiguration.lithoViewSelfManageViewPortChanges) {
      return;
    }

    final ComponentContext c = mLegacyLithoViewRule.getContext();
    final List<List<LifecycleStep.StepInfo>> stepsList = new ArrayList<>();
    final int numberOfItems = 2;

    final Component component =
        HorizontalScroll.create(c)
            .incrementalMountEnabled(true)
            .contentProps(createHorizontalScrollChildren(c, numberOfItems, stepsList))
            .build();

    mLegacyLithoViewRule
        .setRoot(component)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(10, EXACTLY), makeSizeSpec(10, EXACTLY))
        .measure()
        .layout();

    assertThat(LifecycleStep.getSteps(stepsList.get(0)))
        .describedAs("Visible event should not be dispatched")
        .contains(LifecycleStep.ON_EVENT_VISIBLE);

    assertThat(LifecycleStep.getSteps(stepsList.get(1)))
        .describedAs("Visible event should be dispatched")
        .doesNotContain(LifecycleStep.ON_EVENT_VISIBLE);

    HorizontalScrollView lScrollView =
        ((HorizontalScrollView)
            (mLegacyLithoViewRule.getLithoView().getMountItemAt(0).getContent()));
    ReflectionHelpers.setField(lScrollView, "mScrollX", 10);
    ReflectionHelpers.setField(lScrollView, "mScrollY", 0);
    lScrollView.scrollBy(10, 0);
    mLegacyLithoViewRule.dispatchGlobalLayout();

    assertThat(LifecycleStep.getSteps(stepsList.get(1)))
        .describedAs("Visible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_VISIBLE);
  }

  @Test
  public void testOnInvisibleWhenVisibleRectBecomesEmpty() {
    // Test only relevant when IM continues when the visible rect is empty.
    if (!ComponentsConfiguration.shouldContinueIncrementalMountWhenVisibileRectIsEmpty) {
      return;
    }

    final ComponentContext c = mLegacyLithoViewRule.getContext();
    final List<LifecycleStep.StepInfo> steps = new ArrayList<>();
    final LayoutSpecLifecycleTester component =
        LayoutSpecLifecycleTester.create(c).steps(steps).widthPx(10).heightPx(10).build();

    // Set root with non-empty size specs.
    mLegacyLithoViewRule
        .setRoot(component)
        .setSizeSpecs(makeSizeSpec(10, EXACTLY), makeSizeSpec(10, EXACTLY))
        .attachToWindow()
        .measure()
        .layout();

    // Ensure onVisible is fired.
    assertThat(LifecycleStep.getSteps(steps))
        .describedAs("Visible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_VISIBLE);

    // Clear the steps.
    steps.clear();

    // Keep the same root, but now set the width size specs to 0 and remeasure / layout.
    mLegacyLithoViewRule
        .setSizeSpecs(makeSizeSpec(0, EXACTLY), makeSizeSpec(10, EXACTLY))
        .measure()
        .layout();

    // Ensure onInvisible is now fired.
    assertThat(LifecycleStep.getSteps(steps))
        .describedAs("Invisible event should be dispatched")
        .contains(LifecycleStep.ON_EVENT_INVISIBLE);
  }
}
