// (c) Meta Platforms, Inc. and affiliates. Confidential and proprietary.

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

package com.facebook.samples.litho.kotlin.mountables.widgets

import android.content.Context
import android.view.View
import android.view.ViewTreeObserver
import android.widget.HorizontalScrollView
import com.facebook.litho.Component
import com.facebook.litho.ComponentTree
import com.facebook.litho.LithoLayoutContextExtraData.LithoLayoutExtraData
import com.facebook.litho.MeasureScope
import com.facebook.litho.MountableComponent
import com.facebook.litho.MountableComponentScope
import com.facebook.litho.MountableRenderResult
import com.facebook.litho.R
import com.facebook.litho.SimpleMountable
import com.facebook.litho.Size
import com.facebook.litho.SizeSpec
import com.facebook.litho.Style
import com.facebook.litho.useState
import com.facebook.litho.widget.HorizontalScrollEventsController
import com.facebook.litho.widget.HorizontalScrollLithoView
import com.facebook.litho.widget.ScrollStateListener
import com.facebook.rendercore.MeasureResult
import com.facebook.yoga.YogaDirection
import kotlin.math.max

private const val LAST_SCROLL_POSITION_UNSET = -1
private const val DEFAULT_OVER_SCROLL_MODE = View.OVER_SCROLL_IF_CONTENT_SCROLLS

fun HorizontalScroll(
    scrollbarEnabled: Boolean = true,
    eventsController: HorizontalScrollEventsController? = null,
    onScrollChangeListener: HorizontalScrollLithoView.OnScrollChangeListener? = null,
    scrollStateListener: ScrollStateListener? = null,
    incrementalMountEnabled: Boolean = false,
    overScrollMode: Int = DEFAULT_OVER_SCROLL_MODE,
    initialScrollPosition: Int = LAST_SCROLL_POSITION_UNSET,
    fillViewport: Boolean = false,
    style: Style? = null,
    child: () -> Component,
): Component {

  return HorizontalScroll(
      scrollbarEnabled = scrollbarEnabled,
      eventsController = eventsController,
      onScrollChangeListener = onScrollChangeListener,
      scrollStateListener = scrollStateListener,
      incrementalMountEnabled = incrementalMountEnabled,
      overScrollMode = overScrollMode,
      initialScrollPosition = initialScrollPosition,
      fillViewport = fillViewport,
      child = child(),
      style = style,
  )
}

class HorizontalScroll(
    private val scrollbarEnabled: Boolean = true,
    private val eventsController: HorizontalScrollEventsController? = null,
    private val onScrollChangeListener: HorizontalScrollLithoView.OnScrollChangeListener? = null,
    private val scrollStateListener: ScrollStateListener? = null,
    private val incrementalMountEnabled: Boolean = false,
    private val overScrollMode: Int = DEFAULT_OVER_SCROLL_MODE,
    private val initialScrollPosition: Int = LAST_SCROLL_POSITION_UNSET,
    private val fillViewport: Boolean = false,
    private val child: Component,
    private val style: Style? = null
) : MountableComponent() {

  override fun MountableComponentScope.render(): MountableRenderResult {
    val lastScrollPosition = useState {
      HorizontalScrollLithoView.ScrollPosition(initialScrollPosition)
    }

    val childComponentTree = useState {
      ComponentTree.createNestedComponentTree(context, child)
          .incrementalMount(incrementalMountEnabled)
          .build()
    }

    val resolvedScrollbarEnabled =
        getIntAttrValue(
            context,
            R.styleable.HorizontalScroll_android_scrollbars,
            R.styleable.HorizontalScroll,
            scrollbarEnabled)

    return MountableRenderResult(
        mountable =
            HorizontalScrollMountable(
                scrollbarEnabled = resolvedScrollbarEnabled,
                eventsController = eventsController,
                onScrollChangeListener = onScrollChangeListener,
                scrollStateListener = scrollStateListener,
                overScrollMode = overScrollMode,
                lastScrollPosition = lastScrollPosition.value,
                fillViewport = fillViewport,
                childComponent = child,
                childComponentTree = childComponentTree.value),
        style = style)
  }
}

internal class HorizontalScrollMountable(
    private val scrollbarEnabled: Boolean,
    private val eventsController: HorizontalScrollEventsController?,
    private val onScrollChangeListener: HorizontalScrollLithoView.OnScrollChangeListener?,
    private val scrollStateListener: ScrollStateListener?,
    private val overScrollMode: Int,
    private val lastScrollPosition: HorizontalScrollLithoView.ScrollPosition,
    private val fillViewport: Boolean,
    private val childComponent: Component,
    private val childComponentTree: ComponentTree
) : SimpleMountable<HorizontalScrollLithoView>(RenderType.VIEW) {

  override fun doesMountRenderTreeHosts(): Boolean = true

  override fun createContent(context: Context): HorizontalScrollLithoView =
      HorizontalScrollLithoView(context)

  override fun MeasureScope.measure(widthSpec: Int, heightSpec: Int): MeasureResult {
    val size = Size()
    val width = max(0, SizeSpec.getSize(widthSpec))

    // Measure the component with undefined width spec, as the contents of the
    // hscroll have unlimited horizontal space.
    childComponentTree.setRootAndSizeSpecSync(
        childComponent, SizeSpec.makeSizeSpec(0, SizeSpec.UNSPECIFIED), heightSpec, size)

    size.width = max(0, max(size.width, if (fillViewport) width else 0))
    size.height = max(0, size.height)

    val extraLayoutData: LithoLayoutExtraData? =
        layoutContext.layoutContextExtraData?.extraLayoutData as? LithoLayoutExtraData?
    val direction = extraLayoutData?.layoutDirection ?: DEFAULT_LAYOUT_DIRECTION
    return MeasureResult(
        size.width, size.height, HorizontalScrollLayoutData(size.width, size.height, direction))
  }

  override fun mount(c: Context, content: HorizontalScrollLithoView, layoutData: Any?) {
    val horizontalScrollLayoutData = layoutData as HorizontalScrollLayoutData

    content.isHorizontalScrollBarEnabled = scrollbarEnabled
    content.overScrollMode = overScrollMode
    content.mount(
        childComponentTree,
        lastScrollPosition,
        horizontalScrollLayoutData.measuredWidth,
        horizontalScrollLayoutData.measuredHeight,
        onScrollChangeListener,
        scrollStateListener)

    val viewTreeObserver: ViewTreeObserver = content.viewTreeObserver
    viewTreeObserver.addOnPreDrawListener(
        object : ViewTreeObserver.OnPreDrawListener {
          override fun onPreDraw(): Boolean {
            content.viewTreeObserver.removeOnPreDrawListener(this)
            if (lastScrollPosition.x == LAST_SCROLL_POSITION_UNSET) {
              if (horizontalScrollLayoutData.layoutDirection == YogaDirection.RTL) {
                content.fullScroll(HorizontalScrollView.FOCUS_RIGHT)
              }
              lastScrollPosition.x = content.scrollX
            } else {
              content.scrollX = lastScrollPosition.x
            }
            return true
          }
        })

    eventsController?.setScrollableView(content)
  }

  override fun unmount(c: Context, content: HorizontalScrollLithoView, layoutData: Any?) {
    content.unmount()

    eventsController?.setScrollableView(null)
  }

  companion object {
    private val DEFAULT_LAYOUT_DIRECTION = YogaDirection.LTR
  }
}

internal data class HorizontalScrollLayoutData(
    val measuredWidth: Int,
    val measuredHeight: Int,
    val layoutDirection: YogaDirection
)
