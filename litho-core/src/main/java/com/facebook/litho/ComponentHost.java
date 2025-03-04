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

import static com.facebook.litho.AccessibilityUtils.isAccessibilityEnabled;
import static com.facebook.litho.ComponentHostUtils.maybeSetDrawableState;
import static com.facebook.litho.LithoRenderUnit.getRenderUnit;
import static com.facebook.litho.LithoRenderUnit.isTouchableDisabled;
import static com.facebook.litho.ThreadUtils.assertMainThread;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.collection.SparseArrayCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.ViewCompat;
import com.facebook.litho.config.ComponentsConfiguration;
import com.facebook.proguard.annotations.DoNotStrip;
import com.facebook.rendercore.Host;
import com.facebook.rendercore.MountItem;
import com.facebook.rendercore.MountState;
import com.facebook.rendercore.transitions.DisappearingHost;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link ViewGroup} that can host the mounted state of a {@link Component}. This is used by
 * {@link MountState} to wrap mounted drawables to handle click events and update drawable states
 * accordingly.
 */
@DoNotStrip
public class ComponentHost extends Host implements DisappearingHost {

  @IdRes public static final int COMPONENT_NODE_INFO_ID = R.id.component_node_info;

  public static final String TEXTURE_TOO_BIG = "TextureTooBig";
  public static final String TEXTURE_ZERO_DIM = "TextureZeroDim";
  public static final String PARTIAL_ALPHA_TEXTURE_TOO_BIG = "PartialAlphaTextureTooBig";
  private static final int SCRAP_ARRAY_INITIAL_SIZE = 4;

  private static boolean sHasWarnedAboutPartialAlpha = false;

  private final SparseArrayCompat<MountItem> mMountItems = new SparseArrayCompat<>();
  private SparseArrayCompat<MountItem> mScrapMountItemsArray;

  private final SparseArrayCompat<MountItem> mViewMountItems = new SparseArrayCompat<>();
  private SparseArrayCompat<MountItem> mScrapViewMountItemsArray;

  private final SparseArrayCompat<MountItem> mDrawableMountItems = new SparseArrayCompat<>();
  private SparseArrayCompat<MountItem> mScrapDrawableMountItems;

  private @Nullable ArrayList<MountItem> mDisappearingItems;

  private CharSequence mContentDescription;
  private SparseArray<Object> mViewTags;

  private final InterleavedDispatchDraw mDispatchDraw = new InterleavedDispatchDraw();

  private int[] mChildDrawingOrder = new int[0];
  private boolean mIsChildDrawingOrderDirty;

  private boolean mInLayout;
  private boolean mHadChildWithDuplicateParentState = false;

  @Nullable private ComponentAccessibilityDelegate mComponentAccessibilityDelegate;
  private boolean mIsComponentAccessibilityDelegateSet = false;

  private ComponentLongClickListener mOnLongClickListener;
  private ComponentFocusChangeListener mOnFocusChangeListener;
  private ComponentTouchListener mOnTouchListener;
  private @Nullable EventHandler<InterceptTouchEvent> mOnInterceptTouchEventHandler;

  private TouchExpansionDelegate mTouchExpansionDelegate;

  interface ExceptionLogMessageProvider {
    StringBuilder getLogMessage();
  }

  /**
   * {@link ViewGroup#getClipChildren()} was only added in API 18, will need to keep track of this
   * flag ourselves on the lower versions
   */
  private boolean mClipChildren = true;

  private boolean mClippingTemporaryDisabled = false;
  private boolean mClippingToRestore = false;

  /**
   * Is {@code true} if and only if any accessible mounted child content has extra A11Y nodes. This
   * is {@code false} by default, and is set for every mount, unmount, and update call.
   */
  private boolean mImplementsVirtualViews = false;

  public ComponentHost(Context context) {
    this(context, null);
  }

  public ComponentHost(ComponentContext context) {
    this(context.getAndroidContext(), null);
  }

  public ComponentHost(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    setWillNotDraw(false);
    setChildrenDrawingOrderEnabled(true);
    refreshAccessibilityDelegatesIfNeeded(isAccessibilityEnabled(context));
  }

  @Override
  public void mount(int index, MountItem mountItem) {
    mount(index, mountItem, mountItem.getRenderTreeNode().getBounds());
  }

  /**
   * Mounts the given {@link MountItem} with unique index.
   *
   * @param index index of the {@link MountItem}. Guaranteed to be the same index as is passed for
   *     the corresponding {@code unmount(index, mountItem)} call.
   * @param mountItem item to be mounted into the host.
   * @param bounds the bounds of the item that is to be mounted into the host
   */
  public void mount(int index, MountItem mountItem, Rect bounds) {
    final Object content = mountItem.getContent();
    final LithoRenderUnit renderUnit = getRenderUnit(mountItem);
    if (content instanceof Drawable) {
      mountDrawable(index, mountItem, bounds);
    } else if (content instanceof View) {
      mViewMountItems.put(index, mountItem);
      mountView((View) content, renderUnit.getFlags());
      maybeRegisterTouchExpansion(index, mountItem);
      maybeRegisterViewForAccessibility(renderUnit, (View) content);
    }

    mMountItems.put(index, mountItem);
    updateAccessibilityState(renderUnit);
  }

  private void ensureDisappearingItems() {
    if (mDisappearingItems == null) {
      mDisappearingItems = new ArrayList<>();
    }
  }

  @Override
  public void unmount(MountItem item) {
    final int index;
    final int indexOfValue = mMountItems.indexOfValue(item);

    if (indexOfValue == -1) {
      ensureScrapMountItemsArray();
      final int indexOfValueInScrap = mScrapMountItemsArray.indexOfValue(item);
      index = mScrapMountItemsArray.keyAt(indexOfValueInScrap);
    } else {
      index = mMountItems.keyAt(indexOfValue);
    }

    unmount(index, item);
  }

  /**
   * Unmounts the given {@link MountItem} with unique index.
   *
   * @param index index of the {@link MountItem}. Guaranteed to be the same index as was passed for
   *     the corresponding {@code mount(index, mountItem)} call.
   * @param mountItem item to be unmounted from the host.
   */
  @Override
  public void unmount(int index, MountItem mountItem) {
    final Object content = mountItem.getContent();
    if (content instanceof Drawable) {
      unmountDrawable((Drawable) content);
      ComponentHostUtils.removeItem(index, mDrawableMountItems, mScrapDrawableMountItems);
    } else if (content instanceof View) {
      unmountView((View) content);
      ComponentHostUtils.removeItem(index, mViewMountItems, mScrapViewMountItemsArray);
      mIsChildDrawingOrderDirty = true;
      maybeUnregisterTouchExpansion(index, mountItem);
    }

    ComponentHostUtils.removeItem(index, mMountItems, mScrapMountItemsArray);
    releaseScrapDataStructuresIfNeeded();
    updateAccessibilityState(getRenderUnit(mountItem));
  }

  /**
   * This method is needed because if the disappearing item ended up being remounted to the root,
   * then the index can be different than the one it was created with.
   *
   * @param mountItem
   */
  @Override
  public void startDisappearingMountItem(MountItem mountItem) {
    final int index = mMountItems.keyAt(mMountItems.indexOfValue(mountItem));
    startUnmountDisappearingItem(index, mountItem);
  }

  void startUnmountDisappearingItem(int index, MountItem mountItem) {
    final Object content = mountItem.getContent();

    if (content instanceof Drawable) {
      ComponentHostUtils.removeItem(index, mDrawableMountItems, mScrapDrawableMountItems);
    } else if (content instanceof View) {
      ComponentHostUtils.removeItem(index, mViewMountItems, mScrapViewMountItemsArray);
      mIsChildDrawingOrderDirty = true;
      maybeUnregisterTouchExpansion(index, mountItem);
    }
    ComponentHostUtils.removeItem(index, mMountItems, mScrapMountItemsArray);
    releaseScrapDataStructuresIfNeeded();
    ensureDisappearingItems();
    mDisappearingItems.add(mountItem);
  }

  @Override
  public boolean finaliseDisappearingItem(MountItem disappearingItem) {
    ensureDisappearingItems();
    if (!mDisappearingItems.remove(disappearingItem)) {
      return false;
    }

    final Object content = disappearingItem.getContent();
    if (content instanceof Drawable) {
      unmountDrawable((Drawable) content);
    } else if (content instanceof View) {
      unmountView((View) content);
      mIsChildDrawingOrderDirty = true;
    }

    updateAccessibilityState(getRenderUnit(disappearingItem));

    return true;
  }

  boolean hasDisappearingItems() {
    return CollectionsUtils.isNotNullOrEmpty(mDisappearingItems);
  }

  private void maybeMoveTouchExpansionIndexes(MountItem item, int oldIndex, int newIndex) {
    final Rect expandedTouchBounds = getRenderUnit(item).getTouchBoundsExpansion();
    if (expandedTouchBounds == null || mTouchExpansionDelegate == null) {
      return;
    }

    mTouchExpansionDelegate.moveTouchExpansionIndexes(oldIndex, newIndex);
  }

  private void maybeRegisterTouchExpansion(int index, MountItem item) {
    final LithoRenderUnit renderUnit = getRenderUnit(item);
    final Rect expandedTouchBounds = renderUnit.getTouchBoundsExpansion();
    if (expandedTouchBounds == null) {
      return;
    }

    final Object content = item.getContent();
    if (this.equals(content)) {
      // Don't delegate to ourselves or we'll cause a StackOverflowError
      return;
    }

    if (mTouchExpansionDelegate == null) {
      mTouchExpansionDelegate = new TouchExpansionDelegate(this);
      setTouchDelegate(mTouchExpansionDelegate);
    }

    mTouchExpansionDelegate.registerTouchExpansion(index, (View) content, item);
  }

  private void maybeUnregisterTouchExpansion(int index, MountItem item) {
    if (mTouchExpansionDelegate == null) {
      return;
    }

    final Object content = item.getContent();
    if (this.equals(content)) {
      // Recursive delegation is never unregistered
      return;
    }

    mTouchExpansionDelegate.unregisterTouchExpansion(index);
  }

  /** @return number of {@link MountItem}s that are currently mounted in the host. */
  @Override
  public int getMountItemCount() {
    return mMountItems.size();
  }

  /** @return the {@link MountItem} that was mounted with the given index. */
  @Override
  public MountItem getMountItemAt(int index) {
    return mMountItems.valueAt(index);
  }

  /**
   * Hosts are guaranteed to have only one accessible component in them due to the way the view
   * hierarchy is constructed in {@link LayoutState}. There might be other non-accessible components
   * in the same hosts such as a background/foreground component though. This is why this method
   * iterates over all mount items in order to find the accessible one.
   */
  @Nullable
  MountItem getAccessibleMountItem() {
    for (int i = 0; i < getMountItemCount(); i++) {
      MountItem item = getMountItemAt(i);
      // For inexplicable reason, item is null sometimes.
      if (item != null && getRenderUnit(item).isAccessible()) {
        return item;
      }
    }

    return null;
  }

  /** @return list of drawables that are mounted on this host. */
  public List<Drawable> getDrawables() {
    final int size = mDrawableMountItems.size();
    if (size == 0) {
      return Collections.emptyList();
    }

    final List<Drawable> drawables = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      Drawable drawable = (Drawable) mDrawableMountItems.valueAt(i).getContent();
      drawables.add(drawable);
    }

    return drawables;
  }

  /** @return list of names of content mounted on this host. */
  public List<String> getContentNames() {
    final int contentSize = mMountItems.size();
    if (contentSize == 0) {
      return Collections.emptyList();
    }

    final List<String> contentNames = new ArrayList<>(contentSize);
    for (int i = 0; i < contentSize; i++) {
      contentNames.add(getMountItemName(getMountItemAt(i)));
    }

    return contentNames;
  }

  /** @return the text content that is mounted on this host. */
  @DoNotStrip
  public List<TextContent> getTextContent() {
    return ComponentHostUtils.extractTextContent(ComponentHostUtils.extractContent(mMountItems));
  }

  /**
   * This is a helper method to get all the text (as {@link CharSequence}) that is contained inside
   * this {@link ComponentHost}.
   *
   * <p>We should be able to remove this method once the Kotlin migration is finished and doing this
   * kind of filtering option is easier.
   *
   * <p>The correct behavior of this method relies on the correct implementation of {@link
   * TextContent} in Mountables.
   */
  public List<CharSequence> getTextContentText() {
    List<TextContent> textContentList = getTextContent();
    List<CharSequence> textList = new ArrayList<>();
    for (TextContent textContent : textContentList) {
      textList.addAll(textContent.getTextList());
    }

    return textList;
  }

  /** @return the image content that is mounted on this host. */
  public ImageContent getImageContent() {
    return ComponentHostUtils.extractImageContent(ComponentHostUtils.extractContent(mMountItems));
  }

  /** @return the content descriptons that are set on content mounted on this host */
  @Nullable
  @Override
  public CharSequence getContentDescription() {
    return mContentDescription;
  }

  /**
   * Host views implement their own content description handling instead of just delegating to the
   * underlying view framework for performance reasons as the framework sets/resets content
   * description very frequently on host views and the underlying accessibility notifications might
   * cause performance issues. This is safe to do because the framework owns the accessibility state
   * and knows how to update it efficiently.
   */
  @Override
  public void setContentDescription(@Nullable CharSequence contentDescription) {
    if (ComponentsConfiguration.shouldDelegateContentDescriptionChangeEvent) {
      if (mContentDescription == null) {
        if (contentDescription == null) {
          return;
        }
      } else if (mContentDescription.equals(contentDescription)) {
        return;
      }
    }
    mContentDescription = contentDescription;

    if (!TextUtils.isEmpty(contentDescription)
        && ViewCompat.getImportantForAccessibility(this)
            == ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
      ViewCompat.setImportantForAccessibility(this, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    if (ComponentsConfiguration.shouldDelegateContentDescriptionChangeEvent
        && !TextUtils.isEmpty(contentDescription)) {
      // To fix the issue that the TYPE_WINDOW_CONTENT_CHANGED event doesn't get triggered.
      // More details at here: https://fburl.com/aoa2apq5
      super.setContentDescription(contentDescription);
    }
    maybeInvalidateAccessibilityState();
  }

  @Override
  public void setTag(int key, @Nullable Object tag) {
    super.setTag(key, tag);
    if (key == COMPONENT_NODE_INFO_ID && tag != null) {
      refreshAccessibilityDelegatesIfNeeded(isAccessibilityEnabled(getContext()));

      if (mComponentAccessibilityDelegate != null) {
        mComponentAccessibilityDelegate.setNodeInfo((NodeInfo) tag);
      }
    }
  }

  /**
   * Moves the MountItem associated to oldIndex in the newIndex position. This happens when a
   * LithoView needs to re-arrange the internal order of its items. If an item is already present in
   * newIndex the item is guaranteed to be either unmounted or moved to a different index by
   * subsequent calls to either {@link ComponentHost#unmount(int, MountItem)} or {@link
   * ComponentHost#moveItem(MountItem, int, int)}.
   *
   * @param item The item that has been moved.
   * @param oldIndex The current index of the MountItem.
   * @param newIndex The new index of the MountItem.
   */
  @Override
  public void moveItem(MountItem item, int oldIndex, int newIndex) {
    if (item == null && mScrapMountItemsArray != null) {
      item = mScrapMountItemsArray.get(oldIndex);
    }

    if (item == null) {
      return;
    }

    // Check if we're trying to move a mount item from a place where it doesn't exist.
    // If so, fail early and throw exception with description.
    if (isIllegalMountItemMove(item, oldIndex)) {
      final String givenMountItemDescription = item.getRenderTreeNode().generateDebugString(null);
      final MountItem existingMountItem = mMountItems.get(oldIndex);
      final String existingMountItemDescription =
          existingMountItem != null
              ? existingMountItem.getRenderTreeNode().generateDebugString(null)
              : "null";

      throw new IllegalStateException(
          "Attempting to move MountItem from index: "
              + oldIndex
              + " to index: "
              + newIndex
              + ", but given MountItem does not exist at provided old index.\nGiven MountItem: "
              + givenMountItemDescription
              + "\nExisting MountItem at old index: "
              + existingMountItemDescription);
    }

    maybeMoveTouchExpansionIndexes(item, oldIndex, newIndex);

    final Object content = item.getContent();

    if (content instanceof Drawable) {
      moveDrawableItem(item, oldIndex, newIndex);
    } else if (content instanceof View) {
      mIsChildDrawingOrderDirty = true;

      if (mViewMountItems.get(newIndex) != null) {
        ensureScrapViewMountItemsArray();

        ComponentHostUtils.scrapItemAt(newIndex, mViewMountItems, mScrapViewMountItemsArray);
      }

      ComponentHostUtils.moveItem(oldIndex, newIndex, mViewMountItems, mScrapViewMountItemsArray);
    }

    if (mMountItems.get(newIndex) != null) {
      ensureScrapMountItemsArray();

      ComponentHostUtils.scrapItemAt(newIndex, mMountItems, mScrapMountItemsArray);
    }

    ComponentHostUtils.moveItem(oldIndex, newIndex, mMountItems, mScrapMountItemsArray);

    releaseScrapDataStructuresIfNeeded();
  }

  private boolean isIllegalMountItemMove(MountItem mountItem, int moveFromIndex) {
    // If the mount item exists at the given index in the mount items array, this is a legal move.
    if (mountItem == mMountItems.get(moveFromIndex)) {
      return false;
    }

    // If the mount item exists at the given index in the scrap array, this is a legal move.
    // Otherwise, it is illegal.
    return mScrapMountItemsArray == null || mountItem != mScrapMountItemsArray.get(moveFromIndex);
  }

  /**
   * Sets view tags on this host.
   *
   * @param viewTags the map containing the tags by id.
   */
  public void setViewTags(@Nullable SparseArray<Object> viewTags) {
    mViewTags = viewTags;
  }

  /**
   * Sets a long click listener on this host.
   *
   * @param listener The listener to set on this host.
   */
  void setComponentLongClickListener(ComponentLongClickListener listener) {
    mOnLongClickListener = listener;
    this.setOnLongClickListener(listener);
  }

  /** @return The previously set long click listener */
  @Nullable
  ComponentLongClickListener getComponentLongClickListener() {
    return mOnLongClickListener;
  }

  /**
   * Sets a focus change listener on this host.
   *
   * @param listener The listener to set on this host.
   */
  void setComponentFocusChangeListener(ComponentFocusChangeListener listener) {
    mOnFocusChangeListener = listener;
    this.setOnFocusChangeListener(listener);
  }

  /** @return The previously set focus change listener */
  ComponentFocusChangeListener getComponentFocusChangeListener() {
    return mOnFocusChangeListener;
  }

  /**
   * Sets a touch listener on this host.
   *
   * @param listener The listener to set on this host.
   */
  void setComponentTouchListener(ComponentTouchListener listener) {
    mOnTouchListener = listener;
    setOnTouchListener(listener);
  }

  /**
   * Sets an {@link EventHandler} that will be invoked when {@link
   * ComponentHost#onInterceptTouchEvent} is called.
   *
   * @param interceptTouchEventHandler the handler to be set on this host.
   */
  void setInterceptTouchEventHandler(
      @Nullable EventHandler<InterceptTouchEvent> interceptTouchEventHandler) {
    mOnInterceptTouchEventHandler = interceptTouchEventHandler;
  }

  @Override
  public boolean onInterceptTouchEvent(MotionEvent ev) {
    if (mOnInterceptTouchEventHandler != null) {
      return EventDispatcherUtils.dispatchOnInterceptTouch(mOnInterceptTouchEventHandler, this, ev);
    }

    return super.onInterceptTouchEvent(ev);
  }

  /** @return The previous set touch listener. */
  @Nullable
  public ComponentTouchListener getComponentTouchListener() {
    return mOnTouchListener;
  }

  private void updateAccessibilityState(final LithoRenderUnit renderUnit) {
    // If the item has extra A11Y nodes then virtual views are implemented.
    Component component = renderUnit.getComponent();
    if (renderUnit.isAccessible()
        && component instanceof SpecGeneratedComponent
        && ((SpecGeneratedComponent) component).implementsExtraAccessibilityNodes()) {
      setImplementsVirtualViews(true);
    }

    maybeInvalidateAccessibilityState();

    // If there are no more mounted items then virtual views are implemented.
    if (getMountItemCount() == 0) {
      setImplementsVirtualViews(false);
    }
  }

  private void maybeRegisterViewForAccessibility(
      final LithoRenderUnit renderUnit, final View view) {
    Component component = renderUnit.getComponent();
    if (view instanceof ComponentHost) {
      // We already registered the accessibility delegate when building the host.
      return;
    }

    if (component instanceof SpecGeneratedComponent) {
      // Do not change the current behavior we have for SpecGeneratedComponents.
      return;
    }

    NodeInfo nodeInfo = (NodeInfo) view.getTag(COMPONENT_NODE_INFO_ID);
    if (mIsComponentAccessibilityDelegateSet && nodeInfo != null) {
      // Check if AccessibilityDelegate is set on the host, it means accessibility is enabled.
      registerAccessibilityDelegateOnView(view, nodeInfo);
    }
  }

  private void registerAccessibilityDelegateOnView(View view, NodeInfo nodeInfo) {
    ViewCompat.setAccessibilityDelegate(
        view,
        new ComponentAccessibilityDelegate(
            view, nodeInfo, view.isFocusable(), ViewCompat.getImportantForAccessibility(view)));
  }

  /**
   * Invalidates the accessibility tree of this host if an AccessibilityDelegate is set and any
   * children implement virtual views.
   */
  void maybeInvalidateAccessibilityState() {
    if (hasAccessibilityDelegateAndVirtualViews() && mComponentAccessibilityDelegate != null) {
      mComponentAccessibilityDelegate.invalidateRoot();
    }
  }

  boolean implementsVirtualViews() {
    return mImplementsVirtualViews;
  }

  void setImplementsVirtualViews(boolean implementsVirtualViews) {
    mImplementsVirtualViews = implementsVirtualViews;
  }

  /**
   * When a ViewGroup gets a child with duplicateParentState=true added to it, it forever sets a
   * flag (FLAG_NOTIFY_CHILDREN_ON_DRAWABLE_STATE_CHANGE) which makes the View crash if it ever has
   * addStatesFromChildren set to true. We track this so we know not to recycle ComponentHosts that
   * have had this flag set.
   */
  boolean hadChildWithDuplicateParentState() {
    return mHadChildWithDuplicateParentState;
  }

  private boolean hasAccessibilityDelegateAndVirtualViews() {
    return mIsComponentAccessibilityDelegateSet && mImplementsVirtualViews;
  }

  @Override
  public boolean dispatchHoverEvent(MotionEvent event) {
    return (mComponentAccessibilityDelegate != null
            && mImplementsVirtualViews
            && mComponentAccessibilityDelegate.dispatchHoverEvent(event))
        || super.dispatchHoverEvent(event);
  }

  public List<CharSequence> getContentDescriptions() {
    final List<CharSequence> contentDescriptions = new ArrayList<>();
    for (int i = 0, size = mDrawableMountItems.size(); i < size; i++) {
      final CharSequence contentDescription =
          getRenderUnit(mDrawableMountItems.valueAt(i)).getContentDescription();
      if (contentDescription != null) {
        contentDescriptions.add(contentDescription);
      }
    }
    final CharSequence hostContentDescription = getContentDescription();
    if (hostContentDescription != null) {
      contentDescriptions.add(hostContentDescription);
    }

    return contentDescriptions;
  }

  private void mountView(View view, int flags) {
    final boolean childShouldDuplicateParentState = LithoRenderUnit.isDuplicateParentState(flags);
    if (childShouldDuplicateParentState) {
      view.setDuplicateParentStateEnabled(true);
      mHadChildWithDuplicateParentState = true;
    }

    if (view instanceof ComponentHost && LithoRenderUnit.isDuplicateChildrenStates(flags)) {
      ((ComponentHost) view).setAddStatesFromChildren(true);
    }

    mIsChildDrawingOrderDirty = true;

    LayoutParams lp = view.getLayoutParams();
    if (lp == null) {
      lp = generateDefaultLayoutParams();
      view.setLayoutParams(lp);
    }

    if (mInLayout) {
      super.addViewInLayout(view, -1, view.getLayoutParams(), true);
      invalidate();
    } else {
      super.addView(view, -1, view.getLayoutParams());
    }
  }

  private void unmountView(View view) {
    mIsChildDrawingOrderDirty = true;

    if (mInLayout) {
      super.removeViewInLayout(view);
    } else {
      super.removeView(view);
    }

    view.setDuplicateParentStateEnabled(false);
    if (view instanceof ComponentHost && ((ComponentHost) view).addStatesFromChildren()) {
      ((ComponentHost) view).setAddStatesFromChildren(false);
    }
  }

  TouchExpansionDelegate getTouchExpansionDelegate() {
    return mTouchExpansionDelegate;
  }

  @Override
  public void dispatchDraw(Canvas canvas) {
    mDispatchDraw.start(canvas);

    try {
      super.dispatchDraw(canvas);
    } catch (LithoMetadataExceptionWrapper e) {
      int mountItemCount = getMountItemCount();
      StringBuilder componentNames = new StringBuilder("[");
      for (int i = 0; i < mountItemCount; i++) {
        MountItem item = mMountItems.get(i);
        componentNames.append(
            (item != null) ? getRenderUnit(item).getComponent().getSimpleName() : "null");
        if (i < mountItemCount - 1) {
          componentNames.append(", ");
        } else {
          componentNames.append("]");
        }
      }
      e.addCustomMetadata("component_names_from_mount_items", componentNames.toString());
      throw e;
    }

    // Cover the case where the host has no child views, in which case
    // getChildDrawingOrder() will not be called and the draw index will not
    // be incremented. This will also cover the case where drawables must be
    // painted after the last child view in the host.
    if (mDispatchDraw.isRunning()) {
      mDispatchDraw.drawNext();
    }

    mDispatchDraw.end();

    // Everything from mMountItems was drawn at this point. Then ViewGroup took care of drawing
    // disappearing views, as they still added as children. Thus the only thing left to draw is
    // disappearing drawables
    for (int index = 0, size = mDisappearingItems == null ? 0 : mDisappearingItems.size();
        index < size;
        ++index) {
      final Object content = mDisappearingItems.get(index).getContent();
      if (content instanceof Drawable) {
        ((Drawable) content).draw(canvas);
      }
    }

    DebugDraw.draw(this, canvas);
  }

  @Override
  protected int getChildDrawingOrder(int childCount, int i) {
    updateChildDrawingOrderIfNeeded();

    // This method is called in very different contexts within a ViewGroup
    // e.g. when handling input events, drawing, etc. We only want to call
    // the draw methods if the InterleavedDispatchDraw is active.
    if (mDispatchDraw.isRunning()) {
      mDispatchDraw.drawNext();
    }

    return mChildDrawingOrder[i];
  }

  @Override
  public boolean shouldDelayChildPressedState() {
    return false;
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    assertMainThread();

    boolean handled = false;

    if (isEnabled()) {
      // Iterate drawable from last to first to respect drawing order.
      for (int i = mDrawableMountItems.size() - 1; i >= 0; i--) {
        final MountItem item = mDrawableMountItems.valueAt(i);

        if (item.getContent() instanceof Touchable
            && !isTouchableDisabled(getRenderUnit(item).getFlags())) {
          final Touchable t = (Touchable) item.getContent();
          if (t.shouldHandleTouchEvent(event) && t.onTouchEvent(event, this)) {
            handled = true;
            break;
          }
        }
      }
    }

    if (!handled) {
      handled = super.onTouchEvent(event);
    }

    return handled;
  }

  void performLayout(boolean changed, int l, int t, int r, int b) {}

  @Override
  protected final void onLayout(boolean changed, int l, int t, int r, int b) {
    mInLayout = true;
    maybeEmitLayoutError(r - l, b - t);
    performLayout(changed, l, t, r, b);
    mInLayout = false;
  }

  @Override
  public void requestLayout() {
    // Don't request a layout if it will be blocked by any parent. Requesting a layout that is
    // then ignored by an ancestor means that this host will remain in a state where it thinks that
    // it has requested layout, and will therefore ignore future layout requests. This will lead to
    // problems if a child (e.g. a ViewPager) requests a layout later on, since the request will be
    // wrongly ignored by this host.
    ViewParent parent = this;
    while (parent instanceof ComponentHost) {
      final ComponentHost host = (ComponentHost) parent;
      if (!host.shouldRequestLayout()) {
        return;
      }

      parent = parent.getParent();
    }

    super.requestLayout();
  }

  protected boolean shouldRequestLayout() {
    // Don't bubble during layout.
    return !mInLayout;
  }

  @Override
  @SuppressLint("MissingSuperCall")
  protected boolean verifyDrawable(Drawable who) {
    return true;
  }

  @Override
  protected void drawableStateChanged() {
    super.drawableStateChanged();

    for (int i = 0, size = mDrawableMountItems.size(); i < size; i++) {
      final MountItem mountItem = mDrawableMountItems.valueAt(i);
      final LithoRenderUnit renderUnit = getRenderUnit(mountItem);
      maybeSetDrawableState(this, (Drawable) mountItem.getContent(), renderUnit.getFlags());
    }
  }

  @Override
  public void jumpDrawablesToCurrentState() {
    super.jumpDrawablesToCurrentState();

    for (int i = 0, size = mDrawableMountItems.size(); i < size; i++) {
      final Drawable drawable = (Drawable) mDrawableMountItems.valueAt(i).getContent();
      DrawableCompat.jumpToCurrentState(drawable);
    }
  }

  @Override
  public void setVisibility(int visibility) {
    super.setVisibility(visibility);

    final int size = mDrawableMountItems.size();
    if (size > 0) {
      // We only do a main thread assert if there are drawable mount items because visibility may
      // be set on a LithoView during background layout inflation (AsyncLayoutInflater) before
      // we have any mounted content - we don't want to crash in that case.
      assertMainThread();
      for (int i = 0; i < size; i++) {
        final Drawable drawable = (Drawable) mDrawableMountItems.valueAt(i).getContent();
        drawable.setVisible(visibility == View.VISIBLE, false);
      }
    }
  }

  @Override
  public Object getTag(int key) {
    if (mViewTags != null) {
      final Object value = mViewTags.get(key);
      if (value != null) {
        return value;
      }
    }

    return super.getTag(key);
  }

  protected void refreshAccessibilityDelegatesIfNeeded(boolean isAccessibilityEnabled) {
    if (isAccessibilityEnabled == mIsComponentAccessibilityDelegateSet) {
      return;
    }

    if (isAccessibilityEnabled && mComponentAccessibilityDelegate == null) {
      mComponentAccessibilityDelegate =
          new ComponentAccessibilityDelegate(
              this, this.isFocusable(), ViewCompat.getImportantForAccessibility(this));
    }

    ViewCompat.setAccessibilityDelegate(
        this, isAccessibilityEnabled ? mComponentAccessibilityDelegate : null);
    mIsComponentAccessibilityDelegateSet = isAccessibilityEnabled;

    if (!isAccessibilityEnabled) {
      return;
    }

    for (int i = 0, size = getChildCount(); i < size; i++) {
      final View child = getChildAt(i);
      if (child instanceof ComponentHost) {
        ((ComponentHost) child).refreshAccessibilityDelegatesIfNeeded(true);
      } else {
        final NodeInfo nodeInfo = (NodeInfo) child.getTag(COMPONENT_NODE_INFO_ID);
        if (nodeInfo != null) {
          registerAccessibilityDelegateOnView(child, nodeInfo);
        }
      }
    }
  }

  @Override
  public void setAccessibilityDelegate(@Nullable View.AccessibilityDelegate accessibilityDelegate) {
    super.setAccessibilityDelegate(accessibilityDelegate);

    // We cannot compare against mComponentAccessibilityDelegate directly, since it is not the
    // delegate that we receive here. Instead, we'll set this to true at the point that we set that
    // delegate explicitly.
    mIsComponentAccessibilityDelegateSet = false;
  }

  @Override
  public void setClipChildren(boolean clipChildren) {
    if (mClippingTemporaryDisabled) {
      mClippingToRestore = clipChildren;
      return;
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
      // There is no ViewGroup.getClipChildren() method on API < 18, will keep track this way
      mClipChildren = clipChildren;
    }
    super.setClipChildren(clipChildren);
  }

  @Override
  public boolean getClipChildren() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
      // There is no ViewGroup.getClipChildren() method on API < 18
      return mClipChildren;
    } else {
      return super.getClipChildren();
    }
  }

  /**
   * Temporary disables child clipping, the previous state could be restored by calling {@link
   * #restoreChildClipping()}. While clipping is disabled calling {@link #setClipChildren(boolean)}
   * would have no immediate effect, but the restored state would reflect the last set value
   */
  void temporaryDisableChildClipping() {
    if (mClippingTemporaryDisabled) {
      return;
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      mClippingToRestore = getClipChildren();
    } else {
      mClippingToRestore = mClipChildren;
    }

    // The order here is crucial, we first need to set clipping then update
    // mClippingTemporaryDisabled flag
    setClipChildren(false);

    mClippingTemporaryDisabled = true;
  }

  /**
   * Restores child clipping to the state it was in when {@link #temporaryDisableChildClipping()}
   * was called, unless there were attempts to set a new value, while the clipping was disabled,
   * then would be restored to the last set value
   */
  void restoreChildClipping() {
    if (!mClippingTemporaryDisabled) {
      return;
    }

    // The order here is crucial, we first need to update mClippingTemporaryDisabled flag then set
    // clipping
    mClippingTemporaryDisabled = false;

    setClipChildren(mClippingToRestore);
  }

  /**
   * Litho handles adding/removing views automatically using mount/unmount calls. Manually adding/
   * removing views will mess up Litho's bookkeeping of added views and cause weird crashes down the
   * line.
   */
  @Deprecated
  @Override
  public void addView(View child) {
    throw new UnsupportedOperationException(
        "Adding Views manually within LithoViews is not supported");
  }

  /**
   * Litho handles adding/removing views automatically using mount/unmount calls. Manually adding/
   * removing views will mess up Litho's bookkeeping of added views and cause weird crashes down the
   * line.
   */
  @Deprecated
  @Override
  public void addView(View child, int index) {
    throw new UnsupportedOperationException(
        "Adding Views manually within LithoViews is not supported");
  }

  /**
   * Litho handles adding/removing views automatically using mount/unmount calls. Manually adding/
   * removing views will mess up Litho's bookkeeping of added views and cause weird crashes down the
   * line.
   */
  @Deprecated
  @Override
  public void addView(View child, int index, ViewGroup.LayoutParams params) {
    throw new UnsupportedOperationException(
        "Adding Views manually within LithoViews is not supported");
  }

  /**
   * Litho handles adding/removing views automatically using mount/unmount calls. Manually adding/
   * removing views will mess up Litho's bookkeeping of added views and cause weird crashes down the
   * line.
   */
  @Deprecated
  @Override
  protected boolean addViewInLayout(
      View child, int index, ViewGroup.LayoutParams params, boolean preventRequestLayout) {
    throw new UnsupportedOperationException(
        "Adding Views manually within LithoViews is not supported");
  }

  /**
   * Litho handles adding/removing views automatically using mount/unmount calls. Manually adding/
   * removing views will mess up Litho's bookkeeping of added views and cause weird crashes down the
   * line.
   */
  @Deprecated
  @Override
  protected void attachViewToParent(View child, int index, ViewGroup.LayoutParams params) {
    throw new UnsupportedOperationException(
        "Adding Views manually within LithoViews is not supported");
  }

  /**
   * Litho handles adding/removing views automatically using mount/unmount calls. Manually adding/
   * removing views will mess up Litho's bookkeeping of added views and cause weird crashes down the
   * line.
   */
  @Deprecated
  @Override
  public void removeView(View view) {
    throw new UnsupportedOperationException(
        "Removing Views manually within LithoViews is not supported");
  }

  /**
   * Litho handles adding/removing views automatically using mount/unmount calls. Manually adding/
   * removing views will mess up Litho's bookkeeping of added views and cause weird crashes down the
   * line.
   */
  @Deprecated
  @Override
  public void removeViewInLayout(View view) {
    throw new UnsupportedOperationException(
        "Removing Views manually within LithoViews is not supported");
  }

  /**
   * Litho handles adding/removing views automatically using mount/unmount calls. Manually adding/
   * removing views will mess up Litho's bookkeeping of added views and cause weird crashes down the
   * line.
   */
  @Deprecated
  @Override
  public void removeViewsInLayout(int start, int count) {
    throw new UnsupportedOperationException(
        "Removing Views manually within LithoViews is not supported");
  }

  /**
   * Litho handles adding/removing views automatically using mount/unmount calls. Manually adding/
   * removing views will mess up Litho's bookkeeping of added views and cause weird crashes down the
   * line.
   */
  @Deprecated
  @Override
  public void removeViewAt(int index) {
    throw new UnsupportedOperationException(
        "Removing Views manually within LithoViews is not supported");
  }

  /**
   * Litho handles adding/removing views automatically using mount/unmount calls. Manually adding/
   * removing views will mess up Litho's bookkeeping of added views and cause weird crashes down the
   * line.
   */
  @Deprecated
  @Override
  public void removeViews(int start, int count) {
    throw new UnsupportedOperationException(
        "Removing Views manually within LithoViews is not supported");
  }

  /**
   * Litho handles adding/removing views automatically using mount/unmount calls. Manually adding/
   * removing views will mess up Litho's bookkeeping of added views and cause weird crashes down the
   * line.
   */
  @Deprecated
  @Override
  public void removeAllViewsInLayout() {
    throw new UnsupportedOperationException(
        "Removing Views manually within LithoViews is not supported");
  }

  /**
   * Litho handles adding/removing views automatically using mount/unmount calls. Manually adding/
   * removing views will mess up Litho's bookkeeping of added views and cause weird crashes down the
   * line.
   */
  @Deprecated
  @Override
  protected void removeDetachedView(View child, boolean animate) {
    throw new UnsupportedOperationException(
        "Removing Views manually within LithoViews is not supported");
  }

  /**
   * Manually adds a View as a child of this ComponentHost for the purposes of testing. **This
   * should only be used for tests as this is not safe and will likely cause weird crashes if used
   * in a production environment**.
   */
  @VisibleForTesting
  public void addViewForTest(View view) {
    final LayoutParams params =
        view.getLayoutParams() == null ? generateDefaultLayoutParams() : view.getLayoutParams();
    super.addView(view, -1, params);
  }

  /**
   * Returns the Drawables associated with this ComponentHost for animations, for example the
   * background Drawable and/or the drawable that otherwise has a transitionKey on it that has
   * caused it to be hosted in this ComponentHost.
   *
   * <p>The core purpose of exposing these drawables is so that when animating the bounds of this
   * ComponentHost, we also properly animate the bounds of its contained Drawables at the same time.
   */
  public @Nullable List<Drawable> getLinkedDrawablesForAnimation() {
    List<Drawable> drawables = null;

    for (int i = 0, size = mDrawableMountItems.size(); i < size; i++) {
      final MountItem mountItem = mDrawableMountItems.valueAt(i);
      if ((getRenderUnit(mountItem).getFlags() & LithoRenderUnit.LAYOUT_FLAG_MATCH_HOST_BOUNDS)
          != 0) {
        if (drawables == null) {
          drawables = new ArrayList<>();
        }
        drawables.add((Drawable) mountItem.getContent());
      }
    }
    return drawables;
  }

  private void updateChildDrawingOrderIfNeeded() {
    if (!mIsChildDrawingOrderDirty) {
      return;
    }

    final int childCount = getChildCount();
    if (mChildDrawingOrder.length < childCount) {
      mChildDrawingOrder = new int[childCount + 5];
    }

    int index = 0;
    final int viewMountItemCount = mViewMountItems.size();
    for (int i = 0; i < viewMountItemCount; i++) {
      final View child = (View) mViewMountItems.valueAt(i).getContent();
      mChildDrawingOrder[index++] = indexOfChild(child);
    }

    // Draw disappearing items on top of mounted views.
    for (int i = 0, size = mDisappearingItems == null ? 0 : mDisappearingItems.size();
        i < size;
        i++) {
      final Object child = mDisappearingItems.get(i).getContent();
      if (child instanceof View) {
        mChildDrawingOrder[index++] = indexOfChild((View) child);
      }
    }

    mIsChildDrawingOrderDirty = false;
  }

  private void ensureScrapViewMountItemsArray() {
    if (mScrapViewMountItemsArray == null) {
      mScrapViewMountItemsArray = new SparseArrayCompat<>(SCRAP_ARRAY_INITIAL_SIZE);
    }
  }

  private void ensureScrapMountItemsArray() {
    if (mScrapMountItemsArray == null) {
      mScrapMountItemsArray = new SparseArrayCompat<>(SCRAP_ARRAY_INITIAL_SIZE);
    }
  }

  private void releaseScrapDataStructuresIfNeeded() {
    if (CollectionsUtils.isEmpty(mScrapMountItemsArray)) {
      mScrapMountItemsArray = null;
    }

    if (CollectionsUtils.isEmpty(mScrapViewMountItemsArray)) {
      mScrapViewMountItemsArray = null;
    }
  }

  private void mountDrawable(int index, MountItem mountItem, Rect bounds) {
    assertMainThread();

    mDrawableMountItems.put(index, mountItem);
    final Drawable drawable = (Drawable) mountItem.getContent();

    final LithoRenderUnit renderUnit = getRenderUnit(mountItem);
    drawable.setVisible(getVisibility() == View.VISIBLE, false);
    drawable.setCallback(this);

    // If mount data is LithoMountData then Litho need to manually set drawable state.
    if (mountItem.getMountData() instanceof LithoMountData) {
      maybeSetDrawableState(this, drawable, renderUnit.getFlags());
    }
    invalidate(bounds);
  }

  private void unmountDrawable(Drawable drawable) {
    assertMainThread();

    drawable.setCallback(null);
    invalidate(drawable.getBounds());

    releaseScrapDataStructuresIfNeeded();
  }

  private void moveDrawableItem(MountItem item, int oldIndex, int newIndex) {
    assertMainThread();

    // When something is already present in newIndex position we need to keep track of it.
    if (mDrawableMountItems.get(newIndex) != null) {
      ensureScrapDrawableMountItemsArray();

      ComponentHostUtils.scrapItemAt(newIndex, mDrawableMountItems, mScrapDrawableMountItems);
    }

    // Move the MountItem in the new position.
    ComponentHostUtils.moveItem(oldIndex, newIndex, mDrawableMountItems, mScrapDrawableMountItems);

    // Drawing order changed, invalidate the whole view.
    this.invalidate();

    releaseScrapDataStructuresIfNeeded();
  }

  private void ensureScrapDrawableMountItemsArray() {
    if (mScrapDrawableMountItems == null) {
      mScrapDrawableMountItems = new SparseArrayCompat<>(SCRAP_ARRAY_INITIAL_SIZE);
    }
  }

  /**
   * Encapsulates the logic for drawing a set of views and drawables respecting their drawing order
   * withing the component host i.e. allow interleaved views and drawables to be drawn with the
   * correct z-index.
   */
  private class InterleavedDispatchDraw {

    private Canvas mCanvas;
    private int mDrawIndex;
    private int mItemsToDraw;

    private InterleavedDispatchDraw() {}

    private void start(Canvas canvas) {
      mCanvas = canvas;
      mDrawIndex = 0;
      mItemsToDraw = mMountItems == null ? 0 : mMountItems.size();
    }

    private boolean isRunning() {
      return (mCanvas != null && mDrawIndex < mItemsToDraw);
    }

    private void drawNext() {
      if (mCanvas == null) {
        return;
      }

      for (int i = mDrawIndex, size = (mMountItems == null) ? 0 : mMountItems.size();
          i < size;
          i++) {
        final MountItem mountItem = mMountItems.valueAt(i);
        final Object content = mountItem.getContent();

        // During a ViewGroup's dispatchDraw() call with children drawing order enabled,
        // getChildDrawingOrder() will be called before each child view is drawn. This
        // method will only draw the drawables "between" the child views and the let
        // the host draw its children as usual. This is why views are skipped here.
        if (content instanceof View) {
          mDrawIndex = i + 1;
          return;
        }

        if (!mountItem.isBound()) {
          continue;
        }

        final boolean isTracing = ComponentsSystrace.isTracing();
        if (isTracing) {
          ComponentsSystrace.beginSection("draw: " + getMountItemName(mountItem));
        }
        ((Drawable) content).draw(mCanvas);
        if (isTracing) {
          ComponentsSystrace.endSection();
        }
      }

      mDrawIndex = mItemsToDraw;
    }

    private void end() {
      mCanvas = null;
    }
  }

  private static String getMountItemName(MountItem mountItem) {
    return getRenderUnit(mountItem).getComponent().getSimpleName();
  }

  @Override
  public boolean performAccessibilityAction(int action, Bundle arguments) {
    // The view framework requires that a contentDescription be set for the
    // getIterableTextForAccessibility method to work.  If one isn't set, all text granularity
    // actions will be ignored.
    if (action == AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY
        || action == AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY) {
      CharSequence contentDesc = null;
      if (!TextUtils.isEmpty(getContentDescription())) {
        contentDesc = getContentDescription();
      } else if (!getContentDescriptions().isEmpty()) {
        contentDesc = TextUtils.join(", ", getContentDescriptions());
      } else {
        List<CharSequence> textContentText = getTextContentText();
        if (!textContentText.isEmpty()) {
          contentDesc = TextUtils.join(", ", textContentText);
        }
      }

      if (contentDesc == null) {
        return false;
      }

      mContentDescription = contentDesc;
      super.setContentDescription(mContentDescription);
    }

    return super.performAccessibilityAction(action, arguments);
  }

  @Override
  public boolean hasOverlappingRendering() {
    if (getWidth() <= 0 || getHeight() <= 0) {
      // Views with size zero can't possibly have overlapping rendering.
      // Returning false here prevents the rendering system from creating
      // zero-sized layers, which causes crashes.
      return false;
    } else if (getWidth() > ComponentsConfiguration.overlappingRenderingViewSizeLimit
        || getHeight() > ComponentsConfiguration.overlappingRenderingViewSizeLimit) {
      return false;
    } else {
      return super.hasOverlappingRendering();
    }
  }

  private void maybeEmitLayoutError(int width, int height) {
    final @Nullable String category = getLayoutErrorCategory(width, height);
    if (category == null) {
      return;
    }

    ComponentsReporter.emitMessage(
        ComponentsReporter.LogLevel.ERROR,
        category,
        "abnormally sized litho layout (" + width + ", " + height + ")",
        /* take default */ 0,
        getLayoutErrorMetadata(width, height));
  }

  protected Map<String, Object> getLayoutErrorMetadata(int width, int height) {
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("uptimeMs", SystemClock.uptimeMillis());
    metadata.put("identity", Integer.toHexString(System.identityHashCode(this)));
    metadata.put("width", width);
    metadata.put("height", height);
    metadata.put("layerType", layerTypeToString(getLayerType()));
    final Map<String, Object>[] mountItems = new Map[getMountItemCount()];
    for (int i = 0; i < getMountItemCount(); i++) {
      mountItems[i] = getMountInfo(getMountItemAt(i));
    }
    metadata.put("mountItems", mountItems);

    ViewParent parent = this;
    StringBuilder ancestorString = new StringBuilder();
    while (parent != null) {
      ancestorString.append(parent.getClass().getName());
      ancestorString.append(',');
      if (parent instanceof LithoView && !metadata.containsKey("lithoViewDimens")) {
        LithoView lithoView = (LithoView) parent;
        metadata.put(
            "lithoViewDimens", "(" + lithoView.getWidth() + ", " + lithoView.getHeight() + ")");
      }
      parent = parent.getParent();
    }
    metadata.put("ancestors", ancestorString.toString());

    return metadata;
  }

  private static String layerTypeToString(int layerType) {
    switch (layerType) {
      case LAYER_TYPE_NONE:
        return "none";
      case LAYER_TYPE_SOFTWARE:
        return "sw";
      case LAYER_TYPE_HARDWARE:
        return "hw";
      default:
        return "unknown";
    }
  }

  private @Nullable String getLayoutErrorCategory(int width, int height) {
    if (height <= 0 || width <= 0) {
      if (ComponentsConfiguration.emitMessageForZeroSizedTexture) {
        return TEXTURE_ZERO_DIM;
      }
    } else if (height >= ComponentsConfiguration.textureSizeWarningLimit
        || width >= ComponentsConfiguration.textureSizeWarningLimit) {
      return TEXTURE_TOO_BIG;
    }

    return null;
  }

  @SuppressLint({"BadMethodUse-java.lang.Class.getName", "ReflectionMethodUse"})
  private Map<String, Object> getMountInfo(MountItem mountItem) {
    final Object content = mountItem.getContent();
    final Rect bounds = mountItem.getRenderTreeNode().getBounds();

    final Map<String, Object> mountInfo = new HashMap<>();
    mountInfo.put("class", content.getClass().getName());
    mountInfo.put("identity", Integer.toHexString(System.identityHashCode(content)));
    if (content instanceof View) {
      final int layerType = ((View) content).getLayerType();
      mountInfo.put("layerType", layerTypeToString(layerType));
    }
    mountInfo.put("left", bounds.left);
    mountInfo.put("right", bounds.right);
    mountInfo.put("top", bounds.top);
    mountInfo.put("bottom", bounds.bottom);

    return mountInfo;
  }

  @Override
  public void setAlpha(float alpha) {
    if (alpha != 0 && alpha != 1) {
      if (getWidth() >= ComponentsConfiguration.partialAlphaWarningSizeThresold
          || getHeight() >= ComponentsConfiguration.partialAlphaWarningSizeThresold) {
        if (sHasWarnedAboutPartialAlpha) {
          // Only warn about partial alpha once per process lifetime to avoid spamming (this might
          // be called frequently from inside an animation)
          return;
        }

        sHasWarnedAboutPartialAlpha = true;

        ComponentsReporter.emitMessage(
            ComponentsReporter.LogLevel.ERROR,
            PARTIAL_ALPHA_TEXTURE_TOO_BIG,
            "Partial alpha ("
                + alpha
                + ") with large view ("
                + getWidth()
                + ", "
                + getHeight()
                + ")");
      }
    }
    super.setAlpha(alpha);
  }

  @Override
  public void setInLayout() {
    mInLayout = true;
  }

  @Override
  public void unsetInLayout() {
    mInLayout = false;
  }
}
