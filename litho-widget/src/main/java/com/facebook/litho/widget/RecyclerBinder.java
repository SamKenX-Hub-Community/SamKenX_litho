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

package com.facebook.litho.widget;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static androidx.recyclerview.widget.OrientationHelper.HORIZONTAL;
import static androidx.recyclerview.widget.OrientationHelper.VERTICAL;
import static com.facebook.infer.annotation.ThreadConfined.UI;
import static com.facebook.litho.FrameworkLogEvents.EVENT_INIT_RANGE;
import static com.facebook.litho.MeasureComparisonUtils.isMeasureSpecCompatible;
import static com.facebook.litho.widget.ComponentTreeHolder.RENDER_UNINITIALIZED;
import static com.facebook.litho.widget.RenderInfoViewCreatorController.DEFAULT_COMPONENT_VIEW_TYPE;

import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import androidx.annotation.AnyThread;
import androidx.annotation.IntDef;
import androidx.annotation.UiThread;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.LayoutManager;
import androidx.recyclerview.widget.RecyclerViewAccessibilityDelegate;
import com.facebook.infer.annotation.ThreadConfined;
import com.facebook.litho.CollectionsUtils;
import com.facebook.litho.Component;
import com.facebook.litho.ComponentContext;
import com.facebook.litho.ComponentLogParams;
import com.facebook.litho.ComponentTree;
import com.facebook.litho.ComponentTree.MeasureListener;
import com.facebook.litho.ComponentUtils;
import com.facebook.litho.ComponentsLogger;
import com.facebook.litho.ComponentsReporter;
import com.facebook.litho.ComponentsSystrace;
import com.facebook.litho.ErrorEventHandler;
import com.facebook.litho.EventHandler;
import com.facebook.litho.LithoLifecycleProvider;
import com.facebook.litho.LithoStartupLogger;
import com.facebook.litho.LithoView;
import com.facebook.litho.LithoView.LayoutManagerOverrideParams;
import com.facebook.litho.LogTreePopulator;
import com.facebook.litho.MeasureComparisonUtils;
import com.facebook.litho.MountHelper;
import com.facebook.litho.PerfEvent;
import com.facebook.litho.RenderCompleteEvent;
import com.facebook.litho.Size;
import com.facebook.litho.SizeSpec;
import com.facebook.litho.ThreadPoolLayoutHandler;
import com.facebook.litho.ThreadUtils;
import com.facebook.litho.choreographercompat.ChoreographerCompat;
import com.facebook.litho.choreographercompat.ChoreographerCompatImpl;
import com.facebook.litho.config.ComponentsConfiguration;
import com.facebook.litho.config.LayoutThreadPoolConfiguration;
import com.facebook.litho.viewcompat.ViewBinder;
import com.facebook.litho.viewcompat.ViewCreator;
import com.facebook.litho.widget.ComponentTreeHolder.ComponentTreeMeasureListenerFactory;
import com.facebook.litho.widget.ComponentTreeHolder.RenderState;
import com.facebook.litho.widget.ComponentWarmer.ComponentTreeHolderPreparer;
import com.facebook.rendercore.RunnableHandler;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * This binder class is used to asynchronously layout Components given a list of {@link Component}
 * and attaching them to a {@link RecyclerSpec}.
 */
@ThreadSafe
public class RecyclerBinder
    implements Binder<RecyclerView>, LayoutInfo.RenderInfoCollection, HasStickyHeader {

  private static final Size sDummySize = new Size();
  private static final Rect sDummyRect = new Rect();
  private static final Rect sEmptyRect = new Rect();
  private static final String TAG = RecyclerBinder.class.getSimpleName();
  private static final int POST_UPDATE_VIEWPORT_AND_COMPUTE_RANGE_MAX_ATTEMPTS = 3;
  private static final int DATA_RENDERED_CALLBACKS_QUEUE_MAX_SIZE = 20;
  private static final String DATA_RENDERED_NOT_TRIGGERED =
      "RecyclerBinder:DataRenderedNotTriggered";
  static final int UNSET = -1;
  static final int APPLY_READY_BATCHES_RETRY_LIMIT = 100;
  public static final String ID_CUSTOM_ATTR_KEY = "id";

  private static Field mViewHolderField;

  @GuardedBy("this")
  private final List<ComponentTreeHolder> mComponentTreeHolders = new ArrayList<>();

  @GuardedBy("this")
  private final List<ComponentTreeHolder> mAsyncComponentTreeHolders = new ArrayList<>();

  private final LayoutInfo mLayoutInfo;
  private final RecyclerView.Adapter mInternalAdapter;
  private final ComponentContext mComponentContext;
  @Nullable private final LayoutHandlerFactory mLayoutHandlerFactory;
  private final @Nullable LithoViewFactory mLithoViewFactory;
  private final ComponentTreeHolderFactory mComponentTreeHolderFactory;
  private final Handler mMainThreadHandler = new Handler(Looper.getMainLooper());
  private final float mRangeRatio;
  private final AtomicBoolean mIsMeasured = new AtomicBoolean(false);
  private final AtomicBoolean mRequiresRemeasure = new AtomicBoolean(false);
  private final boolean mEnableStableIds;
  private final @Nullable RunnableHandler mAsyncInsertHandler;
  private final boolean mVisibilityProcessingEnabled;
  private final boolean mAcquireStateHandlerOnRelease;
  private final @Nullable LithoLifecycleProvider mParentLifecycle;
  private final @Nullable List<ComponentLogParams> mInvalidStateLogParamsList;
  private final RecyclerRangeTraverser mRangeTraverser;
  private final boolean mHScrollAsyncMode;
  private final boolean mIncrementalMountEnabled;
  private final boolean mIsSubAdapter;
  private final boolean mHasManualEstimatedViewportCount;
  private final boolean mIsReconciliationEnabled;
  private final boolean mIsLayoutDiffingEnabled;
  private final boolean mRecyclerViewItemPrefetch;
  private final int mItemViewCacheSize;
  private final @RecyclingStrategy int mRecyclingStrategy;
  private final @Nullable ErrorEventHandler mErrorEventHandler;
  private final @Nullable ComponentsConfiguration mComponentsConfiguration;

  private final AtomicLong mCurrentChangeSetThreadId = new AtomicLong(-1);
  @VisibleForTesting final boolean mTraverseLayoutBackwards;

  @GuardedBy("this")
  private final Deque<AsyncBatch> mAsyncBatches = new ArrayDeque<>();

  private final AtomicBoolean mHasAsyncBatchesToCheck = new AtomicBoolean(false);
  private final AtomicBoolean mIsInMeasure = new AtomicBoolean(false);

  @ThreadConfined(ThreadConfined.UI)
  @VisibleForTesting
  final Deque<ChangeSetCompleteCallback> mDataRenderedCallbacks = new ArrayDeque<>();

  @VisibleForTesting
  final Runnable mRemeasureRunnable =
      new Runnable() {
        @Override
        public void run() {
          if (mReMeasureEventEventHandler != null) {
            mReMeasureEventEventHandler.dispatchEvent(new ReMeasureEvent());
          }
        }
      };

  private final PostDispatchDrawListener mPostDispatchDrawListener =
      new PostDispatchDrawListener() {
        @Override
        public void postDispatchDraw() {
          maybeDispatchDataRendered();
        }
      };

  private final ViewTreeObserver.OnPreDrawListener mOnPreDrawListener =
      new ViewTreeObserver.OnPreDrawListener() {
        @Override
        public boolean onPreDraw() {
          maybeDispatchDataRendered();
          return true;
        }
      };

  private final View.OnAttachStateChangeListener mOnAttachStateChangeListener =
      new View.OnAttachStateChangeListener() {

        @Override
        public void onViewAttachedToWindow(View v) {}

        @Override
        public void onViewDetachedFromWindow(View v) {
          unregisterDrawListener((RecyclerView) v);
          v.removeOnAttachStateChangeListener(this);
        }
      };

  private final Runnable mNotifyDatasetChangedRunnable =
      new Runnable() {
        @Override
        public void run() {
          mInternalAdapter.notifyDataSetChanged();
        }
      };

  private final @Nullable ComponentTreeMeasureListenerFactory mComponentTreeMeasureListenerFactory;
  private @Nullable ComponentWarmer mComponentWarmer;
  private final @Nullable RunnableHandler mPreallocateMountContentHandler;
  private final boolean mPreallocatePerMountSpec;

  private MeasureListener getMeasureListener(final ComponentTreeHolder holder) {
    return new MeasureListener() {
      @Override
      public void onSetRootAndSizeSpec(
          int layoutVersion, int width, int height, boolean stateUpdate) {
        if (holder.getMeasuredHeight() == height) {
          return;
        }

        holder.setMeasuredHeight(height);

        final int sizeForMeasure = RecyclerBinder.this.getSizeForMeasuring();

        if (sizeForMeasure != UNSET && holder.getMeasuredHeight() <= sizeForMeasure) {
          return;
        }

        synchronized (RecyclerBinder.this) {
          resetMeasuredSize(width);
          requestRemeasure();
        }
      }
    };
  }

  private final ComponentTree.NewLayoutStateReadyListener mAsyncLayoutReadyListener =
      new ComponentTree.NewLayoutStateReadyListener() {

        @UiThread
        @Override
        public void onNewLayoutStateReady(ComponentTree componentTree) {
          applyReadyBatches();
          componentTree.setNewLayoutStateReadyListener(null);
        }
      };

  private final ChoreographerCompat.FrameCallback mApplyReadyBatchesCallback =
      new ChoreographerCompat.FrameCallback() {

        @UiThread
        @Override
        public void doFrame(long frameTimeNanos) {
          applyReadyBatches();
        }
      };

  private final boolean mIsCircular;
  private final boolean mHasDynamicItemHeight;
  private final boolean mWrapContent;
  private boolean mCanMeasure;
  private int mLastWidthSpec = LayoutManagerOverrideParams.UNINITIALIZED;
  private int mLastHeightSpec = LayoutManagerOverrideParams.UNINITIALIZED;
  private Size mMeasuredSize;
  private @Nullable RecyclerView mMountedView;
  /**
   * Can be set for RecyclerBinder instances which do not have control over the RecyclerView which
   * the adapter sends operations to, and it does not mount or measure it. Only for subadapter mode.
   */
  private @Nullable RecyclerView mSubAdapterRecyclerView;

  @VisibleForTesting int mCurrentFirstVisiblePosition = RecyclerView.NO_POSITION;
  @VisibleForTesting int mCurrentLastVisiblePosition = RecyclerView.NO_POSITION;
  private int mCurrentOffset;
  private SmoothScrollAlignmentType mSmoothScrollAlignmentType;
  // The estimated number of items needed to fill the viewport.
  @VisibleForTesting int mEstimatedViewportCount = UNSET;
  // The size computed for the first Component to be used when we can't use the size specs passed to
  // measure.
  @VisibleForTesting @Nullable volatile Size mSizeForMeasure;

  @GuardedBy("this")
  private int mLowestRangeStartSinceDeletes = Integer.MAX_VALUE;

  @GuardedBy("this")
  private int mHighestRangeStartSinceDeletes = Integer.MIN_VALUE;

  private StickyHeaderController mStickyHeaderController;
  private final @Nullable StickyHeaderControllerFactory mStickyHeaderControllerFactory;
  private final @Nullable RunnableHandler mLayoutThreadPoolHandler;
  private final @Nullable RunnableHandler mResolveThreadPoolHandler;
  private final @Nullable LayoutThreadPoolConfiguration mThreadPoolConfig;
  private @Nullable EventHandler<ReMeasureEvent> mReMeasureEventEventHandler;
  private volatile boolean mHasAsyncOperations = false;
  private boolean mIsInitMounted = false; // Set to true when the first mount() is called.
  private @CommitPolicy int mCommitPolicy = CommitPolicy.IMMEDIATE;
  private boolean mHasFilledViewport = false;
  private final @Nullable LithoStartupLogger mStartupLogger;
  private String mStartupLoggerAttribution = "";
  private final boolean[] mFirstMountLogged = new boolean[1];
  private final boolean[] mLastMountLogged = new boolean[1];
  private final RecyclerBinderAdapterDelegate mRecyclerBinderAdapterDelegate;

  @GuardedBy("this")
  private @Nullable AsyncBatch mCurrentBatch = null;

  @VisibleForTesting final ViewportManager mViewportManager;
  private final ViewportChanged mViewportChangedListener =
      new ViewportChanged() {
        @Override
        public void viewportChanged(
            int firstVisibleIndex,
            int lastVisibleIndex,
            int firstFullyVisibleIndex,
            int lastFullyVisibleIndex,
            int state) {
          onNewVisibleRange(firstVisibleIndex, lastVisibleIndex);
          onNewWorkingRange(
              firstVisibleIndex, lastVisibleIndex, firstFullyVisibleIndex, lastFullyVisibleIndex);
        }
      };
  private int mPostUpdateViewportAttempts;

  @VisibleForTesting final RenderInfoViewCreatorController mRenderInfoViewCreatorController;

  private final Runnable mUpdateViewportRunnable =
      new Runnable() {
        @Override
        public void run() {
          if (mMountedView == null || !mMountedView.hasPendingAdapterUpdates()) {
            if (mViewportManager.shouldUpdate()) {
              mViewportManager.onViewportChanged(State.DATA_CHANGES);
            }
            mPostUpdateViewportAttempts = 0;
            return;
          }

          // If the view gets detached, we might still have pending updates.
          // If the view's visibility is GONE, layout won't happen until it becomes visible. We have
          // to exit here, otherwise we keep posting this runnable to the next frame until it
          // becomes visible.
          if (!mMountedView.isAttachedToWindow() || mMountedView.getVisibility() == View.GONE) {
            mPostUpdateViewportAttempts = 0;
            return;
          }

          if (mPostUpdateViewportAttempts >= POST_UPDATE_VIEWPORT_AND_COMPUTE_RANGE_MAX_ATTEMPTS) {
            mPostUpdateViewportAttempts = 0;
            if (mViewportManager.shouldUpdate()) {
              mViewportManager.onViewportChanged(State.DATA_CHANGES);
            }

            return;
          }

          // If we have pending updates, wait until the sync operations are finished and try again
          // in the next frame.
          mPostUpdateViewportAttempts++;
          ViewCompat.postOnAnimation(mMountedView, mUpdateViewportRunnable);
        }
      };

  static class RenderCompleteRunnable implements Runnable {
    private final EventHandler<RenderCompleteEvent> renderCompleteEventHandler;
    private final RenderCompleteEvent.RenderState renderState;
    private final long timestampMillis;

    RenderCompleteRunnable(
        EventHandler<RenderCompleteEvent> renderCompleteEventHandler,
        RenderCompleteEvent.RenderState renderState,
        long timestampMillis) {
      this.renderCompleteEventHandler = renderCompleteEventHandler;
      this.renderState = renderState;
      this.timestampMillis = timestampMillis;
    }

    @Override
    public void run() {
      dispatchRenderCompleteEvent(renderCompleteEventHandler, renderState, timestampMillis);
    }
  }

  interface ComponentTreeHolderFactory {
    ComponentTreeHolder create(
        RenderInfo renderInfo,
        @Nullable RunnableHandler resolveHandler,
        @Nullable RunnableHandler layoutHandler,
        ComponentTreeMeasureListenerFactory measureListenerFactory,
        @Nullable ComponentsConfiguration componentsConfiguration,
        boolean incrementalMountEnabled,
        boolean visibilityProcessingEnabled,
        boolean isReconciliationEnabled,
        boolean isLayoutDiffingEnabled,
        @Nullable RunnableHandler preallocateHandler,
        boolean preallocatePerMountSpec,
        @Nullable LithoLifecycleProvider lifecycleProvider,
        @Nullable ErrorEventHandler errorEventHandler);
  }

  static final ComponentTreeHolderFactory DEFAULT_COMPONENT_TREE_HOLDER_FACTORY =
      new ComponentTreeHolderFactory() {
        @Override
        public ComponentTreeHolder create(
            RenderInfo renderInfo,
            @Nullable RunnableHandler resolveHandler,
            @Nullable RunnableHandler layoutHandler,
            @Nullable ComponentTreeMeasureListenerFactory measureListenerFactory,
            @Nullable ComponentsConfiguration componentsConfiguration,
            boolean incrementalMountEnabled,
            boolean visibilityProcessingEnabled,
            boolean isReconciliationEnabled,
            boolean isLayoutDiffingEnabled,
            @Nullable RunnableHandler preallocateHandler,
            boolean preallocatePerMountSpec,
            @Nullable LithoLifecycleProvider lifecycleProvider,
            @Nullable ErrorEventHandler errorEventHandler) {
          return ComponentTreeHolder.create()
              .renderInfo(renderInfo)
              .resolveHandler(resolveHandler)
              .layoutHandler(layoutHandler)
              .componentTreeMeasureListenerFactory(measureListenerFactory)
              .componentsConfiguration(componentsConfiguration)
              .incrementalMount(incrementalMountEnabled)
              .visibilityProcessingEnabled(visibilityProcessingEnabled)
              .isReconciliationEnabled(isReconciliationEnabled)
              .isLayoutDiffingEnabled(isLayoutDiffingEnabled)
              .preallocateMountContentHandler(preallocateHandler)
              .shouldPreallocatePerMountSpec(preallocatePerMountSpec)
              .parentLifecycleProvider(lifecycleProvider)
              .errorEventHandler(errorEventHandler)
              .build();
        }
      };

  public static class Builder {
    public static final float DEFAULT_RANGE_RATIO = 2f;

    private float rangeRatio = DEFAULT_RANGE_RATIO;
    private LayoutInfo layoutInfo;
    private @Nullable ComponentsConfiguration componentsConfiguration;
    private @Nullable LayoutHandlerFactory layoutHandlerFactory;
    private ComponentTreeHolderFactory componentTreeHolderFactory =
        DEFAULT_COMPONENT_TREE_HOLDER_FACTORY;
    private ComponentContext componentContext;
    private @Nullable LithoViewFactory lithoViewFactory;
    private boolean isCircular;
    private boolean hasDynamicItemHeight;
    private boolean wrapContent;
    private int componentViewType = DEFAULT_COMPONENT_VIEW_TYPE;
    private @Nullable RecyclerView.Adapter overrideInternalAdapter;
    private boolean enableStableIds;
    private @Nullable List<ComponentLogParams> invalidStateLogParamsList;
    private RecyclerRangeTraverser recyclerRangeTraverser;
    private @Nullable LayoutThreadPoolConfiguration threadPoolConfig;
    private boolean canMeasure;
    private boolean hscrollAsyncMode = false;
    private boolean incrementalMount = true;
    private @Nullable StickyHeaderControllerFactory stickyHeaderControllerFactory;
    private boolean isSubAdapter;
    private int estimatedViewportCount = UNSET;
    private boolean isReconciliationEnabled = ComponentsConfiguration.isReconciliationEnabled;
    private boolean isLayoutDiffingEnabled = ComponentsConfiguration.isLayoutDiffingEnabled;
    private @Nullable RunnableHandler preallocateMountContentHandler;
    private boolean shouldPreallocatePerMountSpec;
    private @Nullable ComponentWarmer mComponentWarmer;
    private @Nullable LithoStartupLogger startupLogger;
    private RunnableHandler mAsyncInsertLayoutHandler;
    private boolean visibilityProcessing = true;
    private boolean acquireStateHandlerOnRelease = true;
    private boolean recyclerViewItemPrefetch = false;
    private int itemViewCacheSize = 0;
    private @RecyclingStrategy int recyclingStrategy =
        ComponentsConfiguration.recyclerBinderStrategy;
    private @Nullable LithoLifecycleProvider lifecycleProvider;
    private @Nullable ErrorEventHandler errorEventHandler;
    private @Nullable RecyclerBinderAdapterDelegate adapterDelegate = null;

    /**
     * @param rangeRatio specifies how big a range this binder should try to compute. The range is
     *     computed as number of items in the viewport (when the binder is measured) multiplied by
     *     the range ratio. The ratio is to be intended in both directions. For example a ratio of 1
     *     means that if there are currently N components on screen, the binder should try to
     *     compute the layout for the N components before the first component on screen and for the
     *     N components after the last component on screen. If not set, defaults to 4f.
     */
    public Builder rangeRatio(float rangeRatio) {
      this.rangeRatio = rangeRatio;
      return this;
    }

    /**
     * Defaults to true. If false, when a ComponentTreeHolder is released because it exists the
     * prepared range, the StateHandler of the ComponentTree will not be cached and restored when
     * re-entering the range, so previous state will be lost.
     */
    public Builder acquireStateHandlerOnRelease(boolean acquireStateHandlerOnRelease) {
      this.acquireStateHandlerOnRelease = acquireStateHandlerOnRelease;
      return this;
    }

    /**
     * @param layoutInfo an implementation of {@link LayoutInfo} that will expose information about
     *     the {@link LayoutManager} this RecyclerBinder will use. If not set, it will default to a
     *     vertical list.
     */
    public Builder layoutInfo(LayoutInfo layoutInfo) {
      this.layoutInfo = layoutInfo;
      return this;
    }

    public Builder componentsConfiguration(@Nullable ComponentsConfiguration config) {
      this.componentsConfiguration = config;
      return this;
    }

    /**
     * @param layoutHandlerFactory the RecyclerBinder will use this layoutHandlerFactory when
     *     creating {@link ComponentTree}s in order to specify on which thread layout calculation
     *     should happen.
     */
    public Builder layoutHandlerFactory(@Nullable LayoutHandlerFactory layoutHandlerFactory) {
      this.layoutHandlerFactory = layoutHandlerFactory;
      return this;
    }

    public Builder lithoViewFactory(@Nullable LithoViewFactory lithoViewFactory) {
      this.lithoViewFactory = lithoViewFactory;
      return this;
    }

    /**
     * Whether the underlying RecyclerBinder will have a circular behaviour. Defaults to false.
     * Note: circular lists DO NOT support any operation that changes the size of items like insert,
     * remove, insert range, remove range
     */
    public Builder isCircular(boolean isCircular) {
      this.isCircular = isCircular;
      return this;
    }

    /**
     * If true, the underlying RecyclerBinder will measure the parent height by the height of
     * children if the orientation is vertical, or measure the parent width by the width of children
     * if the orientation is horizontal.
     */
    public Builder wrapContent(boolean wrapContent) {
      this.wrapContent = wrapContent;
      return this;
    }

    /**
     * @param componentTreeHolderFactory Factory to acquire a new ComponentTreeHolder. Defaults to
     *     {@link #DEFAULT_COMPONENT_TREE_HOLDER_FACTORY}.
     */
    public Builder componentTreeHolderFactory(
        ComponentTreeHolderFactory componentTreeHolderFactory) {
      this.componentTreeHolderFactory = componentTreeHolderFactory;
      return this;
    }

    public Builder preallocateMountContentHandler(
        @Nullable RunnableHandler preallocateMountContentHandler) {
      this.preallocateMountContentHandler = preallocateMountContentHandler;
      return this;
    }

    public Builder shouldPreallocatePerMountSpec(boolean shouldPreallocatePerMountSpec) {
      this.shouldPreallocatePerMountSpec = shouldPreallocatePerMountSpec;
      return this;
    }

    /**
     * Set whether item prefetch should be enabled on the underlying RecyclerView.LayoutManager.
     * Defaults to false.
     *
     * <p>ItemPrefetching feature of RecyclerView clashes with RecyclerBinder's compute range
     * optimization and in certain scenarios (like sticky header) it might reset ComponentTree of
     * LithoView while it is still on screen making it render blank or zero height.
     */
    public Builder recyclerViewItemPrefetch(boolean recyclerViewItemPrefetch) {
      this.recyclerViewItemPrefetch = recyclerViewItemPrefetch;
      return this;
    }

    /**
     * Set the number of offscreen views to retain before adding them to the potentially shared
     * recycled view pool.
     *
     * <p>The offscreen view cache stays aware of changes in the attached adapter, allowing a
     * LayoutManager to reuse those views unmodified without needing to return to the adapter to
     * rebind them.
     *
     * @param size Number of views to cache offscreen before returning them to the general recycled
     *     view pool
     */
    public Builder setItemViewCacheSize(int size) {
      // We had some issues with `ItemViewCache` before and disabled it in the
      // [SectionsRecyclerView], but we need this feature to pre-bind offscreen items for
      // full-screen size surfaces.
      this.itemViewCacheSize = size;
      return this;
    }

    /**
     * Do not enable this. This is an experimental feature and your Section surface will take a perf
     * hit if you use it.
     *
     * <p>Whether the items of this RecyclerBinder can change height after the initial measure. Only
     * applicable to horizontally scrolling RecyclerBinders. If true, the children of this h-scroll
     * are all measured with unspecified height. When the ComponentTree of a child is remeasured,
     * this will cause the RecyclerBinder to remeasure in case the height of the child changed and
     * the RecyclerView needs to have a different height to account for it. This only supports
     * changing the height of the item that triggered the remeasuring, not the height of all items
     * in the h-scroll.
     */
    public Builder hasDynamicItemHeight(boolean hasDynamicItemHeight) {
      this.hasDynamicItemHeight = hasDynamicItemHeight;
      return this;
    }

    /**
     * Enable setting custom viewTypes on {@link ViewRenderInfo}s.
     *
     * <p>After this is set, all {@link ViewRenderInfo}s must be built with a custom viewType
     * through {@link ViewRenderInfo.Builder#customViewType(int)}, otherwise exception will be
     * thrown.
     *
     * @param componentViewType the viewType to be used for Component types, provided through {@link
     *     ComponentRenderInfo}. Set this to a value that won't conflict with your custom viewTypes.
     */
    public Builder enableCustomViewType(int componentViewType) {
      this.componentViewType = componentViewType;
      return this;
    }

    /**
     * If set, the RecyclerView adapter will have stableId support turned on. This is ideally what
     * we want to do always but for now we need this as a parameter to make sure stable Ids are not
     * breaking anything.
     */
    public Builder enableStableIds(boolean enableStableIds) {
      this.enableStableIds = enableStableIds;
      return this;
    }

    /**
     * Provide a list of {@link ComponentLogParams} that will be used to log invalid states in the
     * LithoView, such as height being 0 while non-0 value was expected.
     */
    public Builder invalidStateLogParamsList(@Nullable List<ComponentLogParams> logParamsList) {
      this.invalidStateLogParamsList = logParamsList;
      return this;
    }

    /**
     * @param config RecyclerBinder will use this {@link LayoutThreadPoolConfiguration} to create
     *     {@link ThreadPoolLayoutHandler} which will be used to calculate layout in pool of
     *     threads. However, this will create a new separate thread pool which might negatively
     *     affect the app's performance.
     *     <p>Note: if {@link #layoutHandlerFactory(LayoutHandlerFactory)} is provided, the handler
     *     created by the factory will be used instead of the one that would have been created by
     *     this config.
     */
    public Builder threadPoolConfig(@Nullable LayoutThreadPoolConfiguration config) {
      this.threadPoolConfig = config;
      return this;
    }

    /** Set a custom range traverser */
    public Builder recyclerRangeTraverser(RecyclerRangeTraverser traverser) {
      this.recyclerRangeTraverser = traverser;
      return this;
    }

    /**
     * Method for tests to allow mocking of the InternalAdapter to verify interaction with the
     * RecyclerView.
     */
    @VisibleForTesting
    Builder overrideInternalAdapter(RecyclerView.Adapter overrideInternalAdapter) {
      this.overrideInternalAdapter = overrideInternalAdapter;
      return this;
    }

    /** Set a delegation to customize the adapter behaviour. */
    public Builder setAdapterDelegate(@Nullable RecyclerBinderAdapterDelegate delegate) {
      adapterDelegate = delegate;
      return this;
    }

    /**
     * This is used in very specific cases on critical performance paths where measuring the first
     * item cannot be relied on to estimate the viewport count. It should not be used in the common
     * case, use with caution.
     */
    public Builder estimatedViewportCount(int estimatedViewportCount) {
      if (estimatedViewportCount <= 0) {
        throw new IllegalArgumentException(
            "Estimated viewport count must be > 0: " + estimatedViewportCount);
      }
      this.estimatedViewportCount = estimatedViewportCount;
      return this;
    }

    /**
     * Only for horizontally scrolling layouts! If true, the height of the RecyclerView is not known
     * when it's measured; the first item is measured and its height will determine the height of
     * the RecyclerView.
     */
    public Builder canMeasure(boolean canMeasure) {
      this.canMeasure = canMeasure;
      return this;
    }

    /**
     * Experimental. Configuration to change the behavior of HScroll's when they are nested within a
     * vertical scroll. With this mode, the hscroll will attempt to compute all layouts in the
     * background before mounting so that no layouts are computed on the main thread. All subsequent
     * insertions will be treated with LAYOUT_BEFORE_INSERT policy to ensure those layouts also do
     * not happen on the main thread.
     */
    public Builder hscrollAsyncMode(boolean hscrollAsyncMode) {
      this.hscrollAsyncMode = hscrollAsyncMode;
      return this;
    }

    /** Don't use this. If false, turns off incremental mount for all subviews of this Recycler. */
    public Builder incrementalMount(boolean incrementalMount) {
      this.incrementalMount = incrementalMount;
      return this;
    }

    /** Sets a factory to be used to create a custom controller for sticky section headers */
    public Builder stickyHeaderControllerFactory(
        @Nullable StickyHeaderControllerFactory stickyHeaderControllerFactory) {
      this.stickyHeaderControllerFactory = stickyHeaderControllerFactory;
      return this;
    }

    /**
     * Note: this is an advanced usage of RecyclerBinder that requires much more manual hand-holding
     * of the RecyclerBinder than normal usage.
     *
     * <p>In sub adapter mode, the RecyclerBinder doesn't control the entire RecyclerView, but
     * instead just a part of it. This means that the RecyclerBinder can't mount to a RecyclerView
     * and set its adapter, and it won't set a scroll listener on the RecyclerView.
     *
     * <p>Instead, the internal adapter will need to be used/observed and plugged into some sort of
     * multi-adapter that can multiplex the RecyclerView's requests between the different sub
     * adapters.
     *
     * <p>Additionally, since the RecyclerBinder will never mount to a RecyclerView, the owner of
     * this RecyclerBinder must manually dispatch {@link #updateSubAdapterVisibleRange} and {@link
     * #updateSubAdapterWorkingRange} events if this RecyclerBinder can contains more than a screens
     * worth of content.
     */
    public Builder isSubAdapter(boolean isSubAdapter) {
      this.isSubAdapter = isSubAdapter;
      return this;
    }

    public Builder isReconciliationEnabled(boolean isEnabled) {
      isReconciliationEnabled = isEnabled;
      return this;
    }

    public Builder isLayoutDiffingEnabled(boolean isEnabled) {
      isLayoutDiffingEnabled = isEnabled;
      return this;
    }

    public Builder recyclingStrategy(@RecyclingStrategy int recyclingStrategy) {
      this.recyclingStrategy = recyclingStrategy;
      return this;
    }

    public Builder componentWarmer(@Nullable ComponentWarmer componentWarmer) {
      mComponentWarmer = componentWarmer;
      return this;
    }

    public Builder startupLogger(@Nullable LithoStartupLogger logger) {
      startupLogger = logger;
      return this;
    }

    public Builder asyncInsertLayoutHandler(RunnableHandler handler) {
      mAsyncInsertLayoutHandler = handler;
      return this;
    }

    public Builder errorEventHandler(@Nullable ErrorEventHandler errorEventHandler) {
      this.errorEventHandler = errorEventHandler;
      return this;
    }

    public Builder lithoLifecycleProvider(LithoLifecycleProvider lithoLifecycleProvider) {
      this.lifecycleProvider = lithoLifecycleProvider;
      return this;
    }

    /** @param c The {@link ComponentContext} the RecyclerBinder will use. */
    public RecyclerBinder build(ComponentContext c) {
      componentContext = ComponentContext.makeCopyForNestedTree(c);
      if (lifecycleProvider == null) {
        lifecycleProvider = ComponentTree.getLifecycleProvider(c);
      }

      // Incremental mount will not work if this ComponentTree is nested in a parent with it turned
      // off, so always disable it in that case
      incrementalMount = incrementalMount && ComponentContext.isIncrementalMountEnabled(c);
      if (preallocateMountContentHandler == null
          && ComponentsConfiguration.enableNestedTreePreallocation) {
        preallocateMountContentHandler = ComponentContext.getMountContentPreallocationHandler(c);
      }
      visibilityProcessing =
          visibilityProcessing && ComponentContext.isVisibilityProcessingEnabled(c);

      if (layoutInfo == null) {
        layoutInfo = new LinearLayoutInfo(c.getAndroidContext(), VERTICAL, false);
      }

      // we cannot enable circular list and stable id at the same time
      enableStableIds = (!isCircular && enableStableIds);

      return new RecyclerBinder(this);
    }
  }

  @Override
  public boolean isWrapContent() {
    return mWrapContent;
  }

  @Override
  public boolean canMeasure() {
    return mCanMeasure;
  }

  @Override
  public void setCanMeasure(boolean canMeasure) {
    mCanMeasure = canMeasure;
  }

  @Override
  public void detach() {
    if (mParentLifecycle != null) {
      return;
    }

    // Since ComponentTree#release() can only be called on main thread, release the trees
    // immediately if we're on main thread, or post a runnable on main thread.
    if (ThreadUtils.isMainThread()) {
      releaseComponentTreeHolders(mComponentTreeHolders);
    } else {
      final List<ComponentTreeHolder> toRelease;
      synchronized (this) {
        toRelease = new ArrayList<>(mComponentTreeHolders);
      }
      postReleaseComponentTreeHolders(toRelease);
    }
  }

  @UiThread
  private static void releaseComponentTreeHolders(List<ComponentTreeHolder> holders) {
    for (int i = 0, size = holders.size(); i < size; i++) {
      holders.get(i).releaseTree();
    }
  }

  private void postReleaseComponentTreeHolders(final List<ComponentTreeHolder> holders) {
    mMainThreadHandler.post(
        new Runnable() {
          @Override
          public void run() {
            releaseComponentTreeHolders(holders);
          }
        });
  }

  @UiThread
  public void notifyItemRenderCompleteAt(int position, final long timestampMillis) {
    final ComponentTreeHolder holder = mComponentTreeHolders.get(position);
    final EventHandler<RenderCompleteEvent> renderCompleteEventHandler =
        holder.getRenderInfo().getRenderCompleteEventHandler();
    if (renderCompleteEventHandler == null) {
      return;
    }

    final @RenderState int state = holder.getRenderState();
    if (state != ComponentTreeHolder.RENDER_UNINITIALIZED) {
      return;
    }

    // Dispatch a RenderCompleteEvent asynchronously.
    ViewCompat.postOnAnimation(
        mMountedView,
        new RenderCompleteRunnable(
            renderCompleteEventHandler,
            RenderCompleteEvent.RenderState.RENDER_DRAWN,
            timestampMillis));

    // Update the state to prevent dispatch an event again for the same holder.
    holder.setRenderState(ComponentTreeHolder.RENDER_DRAWN);
  }

  @UiThread
  private static void dispatchRenderCompleteEvent(
      EventHandler<RenderCompleteEvent> renderCompleteEventHandler,
      RenderCompleteEvent.RenderState renderState,
      long timestampMillis) {
    ThreadUtils.assertMainThread();

    final RenderCompleteEvent event = new RenderCompleteEvent();
    event.renderState = renderState;
    event.timestampMillis = timestampMillis;
    renderCompleteEventHandler.dispatchEvent(event);
  }

  private RecyclerBinder(Builder builder) {
    mComponentContext = builder.componentContext;
    mParentLifecycle = builder.lifecycleProvider;

    mComponentTreeHolderFactory = builder.componentTreeHolderFactory;
    mEnableStableIds = builder.enableStableIds;
    mRecyclerBinderAdapterDelegate =
        builder.adapterDelegate != null
            ? builder.adapterDelegate
            : new DefaultRecyclerBinderAdapterDelegate();
    mInternalAdapter =
        builder.overrideInternalAdapter != null
            ? builder.overrideInternalAdapter
            : new InternalAdapter();

    mRangeRatio = builder.rangeRatio;
    mLayoutInfo = builder.layoutInfo;
    mLayoutHandlerFactory = builder.layoutHandlerFactory;
    mAsyncInsertHandler = builder.mAsyncInsertLayoutHandler;
    mLithoViewFactory = builder.lithoViewFactory;
    mAcquireStateHandlerOnRelease = builder.acquireStateHandlerOnRelease;
    mRecyclerViewItemPrefetch = builder.recyclerViewItemPrefetch;
    mItemViewCacheSize = builder.itemViewCacheSize;
    mComponentsConfiguration = builder.componentsConfiguration;

    if (mLayoutHandlerFactory == null) {

      /**
       * If a config is manually set, use it. If a global configuration is enabled, check if the
       * configuration enables using a single thread pool for all RecyclerBinders or creates one per
       * RecyclerBinder.
       */
      if (builder.threadPoolConfig != null) {
        mThreadPoolConfig = builder.threadPoolConfig;
        mLayoutThreadPoolHandler = ThreadPoolLayoutHandler.getNewInstance(mThreadPoolConfig);
        mResolveThreadPoolHandler =
            ComponentsConfiguration.useSeparateThreadHandlersForResolveAndLayout
                ? ThreadPoolLayoutHandler.getNewInstance(mThreadPoolConfig)
                : null;
      } else if (ComponentsConfiguration.threadPoolConfiguration != null) {
        mThreadPoolConfig = ComponentsConfiguration.threadPoolConfiguration;
        mLayoutThreadPoolHandler = ThreadPoolLayoutHandler.getNewInstance(mThreadPoolConfig);
        mResolveThreadPoolHandler =
            ComponentsConfiguration.useSeparateThreadHandlersForResolveAndLayout
                ? ThreadPoolLayoutHandler.getNewInstance(mThreadPoolConfig)
                : null;
      } else {
        mThreadPoolConfig = null;
        mLayoutThreadPoolHandler = null;
        mResolveThreadPoolHandler = null;
      }
    } else {
      mThreadPoolConfig = null;
      mLayoutThreadPoolHandler = null;
      mResolveThreadPoolHandler = null;
    }

    mRenderInfoViewCreatorController =
        new RenderInfoViewCreatorController(builder.componentViewType);

    mIsCircular = builder.isCircular;
    mHasDynamicItemHeight =
        mLayoutInfo.getScrollDirection() == HORIZONTAL ? builder.hasDynamicItemHeight : false;
    mComponentTreeMeasureListenerFactory =
        !mHasDynamicItemHeight
            ? null
            : new ComponentTreeMeasureListenerFactory() {
              @Override
              public @Nullable MeasureListener create(final ComponentTreeHolder holder) {
                return getMeasureListener(holder);
              }
            };

    mWrapContent = builder.wrapContent;
    mCanMeasure = builder.canMeasure;
    mTraverseLayoutBackwards = getStackFromEnd();

    if (builder.recyclerRangeTraverser != null) {
      mRangeTraverser = builder.recyclerRangeTraverser;
    } else if (mTraverseLayoutBackwards) { // layout from end
      mRangeTraverser = RecyclerRangeTraverser.BACKWARD_TRAVERSER;
    } else {
      mRangeTraverser = RecyclerRangeTraverser.FORWARD_TRAVERSER;
    }

    mViewportManager =
        new ViewportManager(
            mCurrentFirstVisiblePosition, mCurrentLastVisiblePosition, builder.layoutInfo);

    mInvalidStateLogParamsList = builder.invalidStateLogParamsList;

    if (builder.estimatedViewportCount != UNSET) {
      mEstimatedViewportCount = builder.estimatedViewportCount;
      mHasManualEstimatedViewportCount = true;
    } else {
      mHasManualEstimatedViewportCount = false;
    }

    mHScrollAsyncMode = builder.hscrollAsyncMode;
    mIncrementalMountEnabled = builder.incrementalMount;
    mVisibilityProcessingEnabled = builder.visibilityProcessing;
    mStickyHeaderControllerFactory = builder.stickyHeaderControllerFactory;
    mIsSubAdapter = builder.isSubAdapter;
    mIsReconciliationEnabled = builder.isReconciliationEnabled;
    mIsLayoutDiffingEnabled = builder.isLayoutDiffingEnabled;
    mPreallocateMountContentHandler = builder.preallocateMountContentHandler;
    mPreallocatePerMountSpec = builder.shouldPreallocatePerMountSpec;
    mComponentWarmer = builder.mComponentWarmer;
    mStartupLogger = builder.startupLogger;
    mErrorEventHandler = builder.errorEventHandler;
    mRecyclingStrategy = builder.recyclingStrategy;
  }

  /**
   * Update the item at index position. The {@link RecyclerView} will only be notified of the item
   * being updated after a layout calculation has been completed for the new {@link Component}.
   */
  public final void updateItemAtAsync(int position, RenderInfo renderInfo) {
    assertSingleThreadForChangeSet();

    if (SectionsDebug.ENABLED) {
      Log.d(SectionsDebug.TAG, "(" + hashCode() + ") updateItemAtAsync " + position);
    }

    // TODO(t34154921): Experiment with applying new RenderInfo for updates immediately when in
    // immediate mode
    synchronized (this) {
      addToCurrentBatch(new AsyncUpdateOperation(position, renderInfo));
    }
  }

  /**
   * Update the items starting from the given index position. The {@link RecyclerView} will only be
   * notified of the item being updated after a layout calculation has been completed for the new
   * {@link Component}.
   */
  public final void updateRangeAtAsync(int position, List<RenderInfo> renderInfos) {
    assertSingleThreadForChangeSet();

    if (SectionsDebug.ENABLED) {
      Log.d(
          SectionsDebug.TAG,
          "(" + hashCode() + ") updateRangeAtAsync " + position + ", count: " + renderInfos.size());
    }

    synchronized (this) {
      addToCurrentBatch(new AsyncUpdateRangeOperation(position, renderInfos));
    }
  }

  /**
   * Inserts an item at position. The {@link RecyclerView} will only be notified of the item being
   * inserted after a layout calculation has been completed for the new {@link Component}.
   */
  public final void insertItemAtAsync(int position, RenderInfo renderInfo) {
    assertSingleThreadForChangeSet();

    assertNoInsertOperationIfCircular();

    if (SectionsDebug.ENABLED) {
      Log.d(
          SectionsDebug.TAG,
          "(" + hashCode() + ") insertItemAtAsync " + position + ", name: " + renderInfo.getName());
    }

    assertNotNullRenderInfo(renderInfo);
    final AsyncInsertOperation operation = createAsyncInsertOperation(position, renderInfo);

    synchronized (this) {
      mHasAsyncOperations = true;

      mAsyncComponentTreeHolders.add(position, operation.mHolder);

      registerAsyncInsert(operation);
    }
  }

  /**
   * Inserts the new items starting from position. The {@link RecyclerView} will only be notified of
   * the items being inserted after a layout calculation has been completed for the new {@link
   * Component}s. There is not a guarantee that the {@link RecyclerView} will be notified about all
   * the items in the range at the same time.
   */
  public final void insertRangeAtAsync(int position, List<RenderInfo> renderInfos) {
    assertSingleThreadForChangeSet();

    assertNoInsertOperationIfCircular();

    if (SectionsDebug.ENABLED) {
      final String[] names = new String[renderInfos.size()];
      for (int i = 0; i < renderInfos.size(); i++) {
        names[i] = renderInfos.get(i).getName();
      }
      Log.d(
          SectionsDebug.TAG,
          "("
              + hashCode()
              + ") insertRangeAtAsync "
              + position
              + ", size: "
              + renderInfos.size()
              + ", names: "
              + Arrays.toString(names));
    }

    synchronized (this) {
      mHasAsyncOperations = true;

      for (int i = 0, size = renderInfos.size(); i < size; i++) {
        final RenderInfo renderInfo = renderInfos.get(i);
        assertNotNullRenderInfo(renderInfo);
        final AsyncInsertOperation operation = createAsyncInsertOperation(position + i, renderInfo);

        mAsyncComponentTreeHolders.add(position + i, operation.mHolder);

        registerAsyncInsert(operation);
      }
    }
  }

  private void ensureApplyReadyBatches() {
    if (ThreadUtils.isMainThread()) {
      applyReadyBatches();
    } else {
      ChoreographerCompatImpl.getInstance().postFrameCallback(mApplyReadyBatchesCallback);
    }
  }

  private boolean isRecyclerViewTargetComputingLayout() {
    if (mMountedView != null) {
      return mMountedView.isComputingLayout();
    }

    if (mSubAdapterRecyclerView != null) {
      return mSubAdapterRecyclerView.isComputingLayout();
    }

    return false;
  }

  public void setSubAdapterModeRecyclerView(RecyclerView recyclerView) {
    if (!mIsSubAdapter) {
      throw new IllegalStateException(
          "Cannot set a subadapter RecyclerView on a RecyclerBinder which is not in subadapter mode.");
    }

    registerDrawListener(recyclerView);
    mSubAdapterRecyclerView = recyclerView;
    mIsInitMounted = true;
  }

  public void removeSubAdapterModeRecyclerView(RecyclerView recyclerView) {
    if (!mIsSubAdapter) {
      throw new IllegalStateException(
          "Cannot remove a subadapter RecyclerView on a RecyclerBinder which is not in subadapter mode.");
    }

    unregisterDrawListener(recyclerView);
    maybeDispatchDataRendered();
    mSubAdapterRecyclerView = null;
  }

  @UiThread
  @VisibleForTesting
  void applyReadyBatches() {
    applyReadyBatchesWithRetry(0);
  }

  private String getState(RecyclerView recyclerView) {
    try {
      Field field = RecyclerView.class.getDeclaredField("mState");
      field.setAccessible(true);
      Object state = field.get(recyclerView);
      return state == null ? "null" : state.toString();
    } catch (Exception e) {
      return "Exception getting state: " + e.getMessage();
    }
  }

  @UiThread
  private void applyReadyBatchesWithRetry(final int retryCount) {
    ThreadUtils.assertMainThread();

    final boolean isTracing = ComponentsSystrace.isTracing();
    if (isTracing) {
      ComponentsSystrace.beginSection("applyReadyBatches");
    }
    try {
      // Fast check that doesn't acquire lock -- measure() is locking and will post a call to
      // applyReadyBatches when it completes.
      if (!mHasAsyncBatchesToCheck.get() || !mIsMeasured.get() || mIsInMeasure.get()) {
        return;
      }

      // If applyReadyBatches happens to be called from scroll of the RecyclerView (e.g. a scroll
      // event triggers a new sections root synchronously which adds a component and calls
      // applyReadyBatches), we need to postpone changing the adapter since RecyclerView asserts
      // that changes don't happen while it's in scroll/layout.
      if (isRecyclerViewTargetComputingLayout()) {
        // Sanity check that we don't get stuck in an infinite loop
        if (retryCount > APPLY_READY_BATCHES_RETRY_LIMIT) {
          final RecyclerView mountedView = mIsSubAdapter ? mSubAdapterRecyclerView : mMountedView;
          String exceptionMessage =
              "Too many retries -- RecyclerView is stuck in layout. Batch size: "
                  + mAsyncBatches.size()
                  + ", isSubAdapter: "
                  + mIsSubAdapter;
          if (mountedView == null) {
            exceptionMessage += ", mountedView: null";
          } else {
            exceptionMessage +=
                ", isAttachedToWindow: "
                    + mountedView.isAttachedToWindow()
                    + ", isAnimating: "
                    + mountedView.isAnimating()
                    + ", state: "
                    + getState(mountedView)
                    + ", mountedView: "
                    + mountedView;
          }
          throw ComponentUtils.wrapWithMetadata(
              mComponentContext, new RuntimeException(exceptionMessage));
        }

        // Making changes to the adapter here will crash us. Just post to the next frame boundary.
        ChoreographerCompatImpl.getInstance()
            .postFrameCallback(
                new ChoreographerCompat.FrameCallback() {
                  @Override
                  public void doFrame(long frameTimeNanos) {
                    applyReadyBatchesWithRetry(retryCount + 1);
                  }
                });
        return;
      }

      boolean appliedBatch = false;
      while (true) {
        final AsyncBatch batch;
        synchronized (this) {
          if (mAsyncBatches.isEmpty()) {
            mHasAsyncBatchesToCheck.set(false);
            break;
          }

          batch = mAsyncBatches.peekFirst();
          if (!isBatchReady(batch)) {
            break;
          }

          mAsyncBatches.pollFirst();
        }

        applyBatch(batch);
        appliedBatch |= batch.mIsDataChanged;
      }

      if (appliedBatch) {
        if (LithoStartupLogger.isEnabled(mStartupLogger)) {
          mStartupLoggerAttribution = mStartupLogger.getLatestDataAttribution();
        }

        maybeUpdateRangeOrRemeasureForMutation();
      }
    } finally {
      if (isTracing) {
        ComponentsSystrace.endSection();
      }
    }
  }

  private static boolean isBatchReady(AsyncBatch batch) {
    if (batch.mCommitPolicy == CommitPolicy.IMMEDIATE) {
      return true;
    }

    for (int i = 0, size = batch.mOperations.size(); i < size; i++) {
      final AsyncOperation operation = batch.mOperations.get(i);
      if (operation instanceof AsyncInsertOperation
          && !((AsyncInsertOperation) operation).mHolder.hasCompletedLatestLayout()) {
        return false;
      }
    }
    return true;
  }

  @UiThread
  private void applyBatch(AsyncBatch batch) {
    synchronized (this) {
      for (int i = 0, size = batch.mOperations.size(); i < size; i++) {
        final AsyncOperation operation = batch.mOperations.get(i);

        switch (operation.mOperation) {
          case Operation.INSERT:
            applyAsyncInsert((AsyncInsertOperation) operation);
            break;
          case Operation.UPDATE:
            final AsyncUpdateOperation updateOperation = (AsyncUpdateOperation) operation;
            updateItemAt(updateOperation.mPosition, updateOperation.mRenderInfo);
            break;
          case Operation.UPDATE_RANGE:
            final AsyncUpdateRangeOperation updateRangeOperation =
                (AsyncUpdateRangeOperation) operation;
            updateRangeAt(updateRangeOperation.mPosition, updateRangeOperation.mRenderInfos);
            break;
          case Operation.REMOVE:
            removeItemAt(((AsyncRemoveOperation) operation).mPosition);
            break;
          case Operation.REMOVE_RANGE:
            final AsyncRemoveRangeOperation removeRangeOperation =
                (AsyncRemoveRangeOperation) operation;
            removeRangeAt(removeRangeOperation.mPosition, removeRangeOperation.mCount);
            break;
          case Operation.MOVE:
            final AsyncMoveOperation moveOperation = (AsyncMoveOperation) operation;
            moveItem(moveOperation.mFromPosition, moveOperation.mToPosition);
            break;
          default:
            throw new RuntimeException("Unhandled operation type: " + operation.mOperation);
        }
      }
    }

    batch.mChangeSetCompleteCallback.onDataBound();
    mDataRenderedCallbacks.addLast(batch.mChangeSetCompleteCallback);
    maybeDispatchDataRendered();
  }

  @GuardedBy("this")
  @UiThread
  private void applyAsyncInsert(AsyncInsertOperation operation) {
    if (operation.mHolder.isInserted()) {
      return;
    }

    if (SectionsDebug.ENABLED) {
      Log.d(SectionsDebug.TAG, "(" + hashCode() + ") applyAsyncInsert " + operation.mPosition);
    }

    mRenderInfoViewCreatorController.maybeTrackViewCreator(operation.mHolder.getRenderInfo());
    mComponentTreeHolders.add(operation.mPosition, operation.mHolder);
    operation.mHolder.setInserted(true);
    mInternalAdapter.notifyItemInserted(operation.mPosition);
    final boolean shouldUpdate =
        mViewportManager.insertAffectsVisibleRange(operation.mPosition, 1, mEstimatedViewportCount);
    mViewportManager.setShouldUpdate(shouldUpdate);
  }

  @GuardedBy("this")
  private void registerAsyncInsert(AsyncInsertOperation operation) {
    addToCurrentBatch(operation);

    final ComponentTreeHolder holder = operation.mHolder;
    holder.setNewLayoutReadyListener(mAsyncLayoutReadyListener);
    // Otherwise, we'll kick off the layout at the end of measure
    if (isMeasured()) {
      computeLayoutAsync(holder);
    }
  }

  /**
   * Moves an item from fromPosition to toPostion. If there are other pending operations on this
   * binder this will only be executed when all the operations have been completed (to ensure index
   * consistency).
   */
  public final void moveItemAsync(int fromPosition, int toPosition) {
    assertSingleThreadForChangeSet();

    if (SectionsDebug.ENABLED) {
      Log.d(
          SectionsDebug.TAG,
          "(" + hashCode() + ") moveItemAsync " + fromPosition + " to " + toPosition);
    }

    final AsyncMoveOperation operation = new AsyncMoveOperation(fromPosition, toPosition);
    synchronized (this) {
      mHasAsyncOperations = true;

      mAsyncComponentTreeHolders.add(toPosition, mAsyncComponentTreeHolders.remove(fromPosition));

      // TODO(t28619782): When moving a CT into range, do an async prepare
      addToCurrentBatch(operation);
    }
  }

  /**
   * Removes an item from position. If there are other pending operations on this binder this will
   * only be executed when all the operations have been completed (to ensure index consistency).
   */
  public final void removeItemAtAsync(int position) {
    assertSingleThreadForChangeSet();

    if (SectionsDebug.ENABLED) {
      Log.d(SectionsDebug.TAG, "(" + hashCode() + ") removeItemAtAsync " + position);
    }

    final AsyncRemoveOperation asyncRemoveOperation = new AsyncRemoveOperation(position);
    synchronized (this) {
      mHasAsyncOperations = true;

      mAsyncComponentTreeHolders.remove(position);
      addToCurrentBatch(asyncRemoveOperation);
    }
  }

  /**
   * Removes count items starting from position. If there are other pending operations on this
   * binder this will only be executed when all the operations have been completed (to ensure index
   * consistency).
   */
  public final void removeRangeAtAsync(int position, int count) {
    assertSingleThreadForChangeSet();

    assertNoRemoveOperationIfCircular(count);

    if (SectionsDebug.ENABLED) {
      Log.d(
          SectionsDebug.TAG,
          "(" + hashCode() + ") removeRangeAtAsync " + position + ", size: " + count);
    }

    final AsyncRemoveRangeOperation operation = new AsyncRemoveRangeOperation(position, count);
    synchronized (this) {
      mHasAsyncOperations = true;

      for (int i = 0; i < count; i++) {
        // TODO(t28712163): Cancel pending layouts for async inserts
        mAsyncComponentTreeHolders.remove(position);
      }
      addToCurrentBatch(operation);
    }
  }

  /** Removes all items in this binder async. */
  public final void clearAsync() {
    assertSingleThreadForChangeSet();

    if (SectionsDebug.ENABLED) {
      Log.d(SectionsDebug.TAG, "(" + hashCode() + ") clear");
    }

    synchronized (this) {
      mHasAsyncOperations = true;

      final int count = mAsyncComponentTreeHolders.size();

      // TODO(t28712163): Cancel pending layouts for async inserts
      mAsyncComponentTreeHolders.clear();

      final AsyncRemoveRangeOperation operation = new AsyncRemoveRangeOperation(0, count);
      addToCurrentBatch(operation);
    }
  }

  @GuardedBy("this")
  private void addToCurrentBatch(AsyncOperation operation) {
    if (mCurrentBatch == null) {
      mCurrentBatch = new AsyncBatch(mCommitPolicy);
    }
    mCurrentBatch.mOperations.add(operation);
  }

  /** Replaces all items in the {@link RecyclerBinder} with the provided {@link RenderInfo}s. */
  @UiThread
  public final void replaceAll(List<RenderInfo> renderInfos) {
    final List<ComponentTreeHolder> toRelease;
    synchronized (this) {
      if (mHasAsyncOperations) {
        throw new RuntimeException(
            "Trying to do a sync replaceAll when using asynchronous mutations!");
      }
      toRelease = new ArrayList<>(mComponentTreeHolders);
      mComponentTreeHolders.clear();
      for (RenderInfo renderInfo : renderInfos) {
        mComponentTreeHolders.add(createComponentTreeHolder(renderInfo));
      }
    }
    mInternalAdapter.notifyDataSetChanged();
    mViewportManager.setShouldUpdate(true);

    // When items are removed, the corresponding views might want to disappear with animations,
    // therefore we post a runnable to release the ComponentTrees later.
    postReleaseComponentTreeHolders(toRelease);
  }

  /** See {@link RecyclerBinder#appendItem(RenderInfo)}. */
  @UiThread
  public final void appendItem(Component component) {
    insertItemAt(getItemCount(), component);
  }

  /**
   * Inserts a new item at tail. The {@link RecyclerView} gets notified immediately about the new
   * item being inserted. If the item's position falls within the currently visible range, the
   * layout is immediately computed on the] UiThread. The RenderInfo contains the component that
   * will be inserted in the Binder and extra info like isSticky or spanCount.
   */
  @UiThread
  public final void appendItem(RenderInfo renderInfo) {
    insertItemAt(getItemCount(), renderInfo);
  }

  /** See {@link RecyclerBinder#insertItemAt(int, RenderInfo)}. */
  @UiThread
  public final void insertItemAt(int position, Component component) {
    insertItemAt(position, ComponentRenderInfo.create().component(component).build());
  }

  /**
   * Inserts a new item at position. The {@link RecyclerView} gets notified immediately about the
   * new item being inserted. If the item's position falls within the currently visible range, the
   * layout is immediately computed on the] UiThread. The RenderInfo contains the component that
   * will be inserted in the Binder and extra info like isSticky or spanCount.
   */
  @UiThread
  public final void insertItemAt(int position, RenderInfo renderInfo) {
    ThreadUtils.assertMainThread();

    assertNoInsertOperationIfCircular();

    if (SectionsDebug.ENABLED) {
      Log.d(
          SectionsDebug.TAG,
          "(" + hashCode() + ") insertItemAt " + position + ", name: " + renderInfo.getName());
    }

    assertNotNullRenderInfo(renderInfo);
    final ComponentTreeHolder holder = createComponentTreeHolder(renderInfo);
    synchronized (this) {
      if (mHasAsyncOperations) {
        throw new RuntimeException("Trying to do a sync insert when using asynchronous mutations!");
      }
      mComponentTreeHolders.add(position, holder);
      mRenderInfoViewCreatorController.maybeTrackViewCreator(renderInfo);
    }

    mInternalAdapter.notifyItemInserted(position);

    mViewportManager.setShouldUpdate(
        mViewportManager.insertAffectsVisibleRange(position, 1, mEstimatedViewportCount));
  }

  private Size getInitialMeasuredSize(
      int parentWidthSpec, int parentHeightSpec, boolean canRemeasure) {
    final Size out = new Size();
    final int scrollDirection = mLayoutInfo.getScrollDirection();

    final int measuredWidth;
    final int measuredHeight;

    final boolean shouldMeasureItemForSize =
        shouldMeasureItemForSize(parentWidthSpec, parentHeightSpec, scrollDirection, canRemeasure);

    switch (scrollDirection) {
      case VERTICAL:
        measuredHeight = SizeSpec.getSize(parentHeightSpec);

        if (!shouldMeasureItemForSize) {
          measuredWidth = SizeSpec.getSize(parentWidthSpec);
        } else if (mSizeForMeasure != null) {
          measuredWidth = mSizeForMeasure.width;
        } else {
          measuredWidth = 0;
        }
        break;

      case HORIZONTAL:
      default:
        measuredWidth = SizeSpec.getSize(parentWidthSpec);

        if (!shouldMeasureItemForSize) {
          measuredHeight = SizeSpec.getSize(parentHeightSpec);
        } else if (mSizeForMeasure != null) {
          measuredHeight = mSizeForMeasure.height;
        } else {
          measuredHeight = 0;
        }
        break;
    }

    out.width = measuredWidth;
    out.height = measuredHeight;

    return out;
  }

  private void maybeRequestRemeasureIfBoundsChanged() {
    if (mMeasuredSize.width == 0 || mMeasuredSize.height == 0) {
      // It was measured before, but no data was bound in previous measurement,
      // therefore we need to remeasure.
      requestRemeasure();
      return;
    }

    // Even after data change we may not require triggering remeasure event if bounds of
    // RecyclerView did not change.
    final Size initialSize = getInitialMeasuredSize(mLastWidthSpec, mLastHeightSpec, true);

    final Size wrapSize = new Size();
    fillListViewport(initialSize.width, initialSize.height, wrapSize);

    if (wrapSize.width != mMeasuredSize.width || wrapSize.height != mMeasuredSize.height) {
      requestRemeasure();
    }
  }

  private void requestRemeasure() {
    if (SectionsDebug.ENABLED) {
      Log.d(SectionsDebug.TAG, "(" + hashCode() + ") requestRemeasure");
    }

    if (mMountedView != null) {
      mMainThreadHandler.removeCallbacks(mRemeasureRunnable);
      mMountedView.removeCallbacks(mRemeasureRunnable);
      ViewCompat.postOnAnimation(mMountedView, mRemeasureRunnable);
    } else {
      // We are not mounted but we still need to post this. Just post on the main thread.
      mMainThreadHandler.removeCallbacks(mRemeasureRunnable);
      mMainThreadHandler.post(mRemeasureRunnable);
    }
  }

  /**
   * Inserts the new items starting from position. The {@link RecyclerView} gets notified
   * immediately about the new item being inserted. The RenderInfo contains the component that will
   * be inserted in the Binder and extra info like isSticky or spanCount.
   */
  @UiThread
  public final void insertRangeAt(int position, List<RenderInfo> renderInfos) {
    ThreadUtils.assertMainThread();

    assertNoInsertOperationIfCircular();

    if (SectionsDebug.ENABLED) {
      final String[] names = new String[renderInfos.size()];
      for (int i = 0; i < renderInfos.size(); i++) {
        names[i] = renderInfos.get(i).getName();
      }
      Log.d(
          SectionsDebug.TAG,
          "("
              + hashCode()
              + ") insertRangeAt "
              + position
              + ", size: "
              + renderInfos.size()
              + ", names: "
              + Arrays.toString(names));
    }

    synchronized (this) {
      for (int i = 0, size = renderInfos.size(); i < size; i++) {
        final RenderInfo renderInfo = renderInfos.get(i);
        assertNotNullRenderInfo(renderInfo);

        final ComponentTreeHolder holder = createComponentTreeHolder(renderInfo);
        if (mHasAsyncOperations) {
          throw new RuntimeException(
              "Trying to do a sync insert when using asynchronous mutations!");
        }
        mComponentTreeHolders.add(position + i, holder);
        mRenderInfoViewCreatorController.maybeTrackViewCreator(renderInfo);
      }
    }

    mInternalAdapter.notifyItemRangeInserted(position, renderInfos.size());

    mViewportManager.setShouldUpdate(
        mViewportManager.insertAffectsVisibleRange(
            position, renderInfos.size(), mEstimatedViewportCount));
  }

  /** See {@link RecyclerBinder#updateItemAt(int, Component)}. */
  @UiThread
  public final void updateItemAt(int position, Component component) {
    updateItemAt(position, ComponentRenderInfo.create().component(component).build());
  }

  /**
   * Updates the item at position. The {@link RecyclerView} gets notified immediately about the item
   * being updated. If the item's position falls within the currently visible range, the layout is
   * immediately computed on the UiThread.
   */
  @UiThread
  public final void updateItemAt(int position, RenderInfo renderInfo) {
    ThreadUtils.assertMainThread();

    if (SectionsDebug.ENABLED) {
      Log.d(
          SectionsDebug.TAG,
          "(" + hashCode() + ") updateItemAt " + position + ", name: " + renderInfo.getName());
    }

    final ComponentTreeHolder holder;
    final boolean renderInfoWasView;
    synchronized (this) {
      if (mComponentTreeHolders.isEmpty()) {
        throw new RuntimeException(
            "Trying to update an item while the list of components is empty (index="
                + position
                + ", size="
                + mComponentTreeHolders.size()
                + "). This likely means data passed to the section had duplicates or a mutable data model. Component involved in the error whose backing data model may have duplicates: "
                + renderInfo.getName()
                + ". Read more here: https://fblitho.com/docs/sections/best-practices/#avoiding-indexoutofboundsexception");
      }

      holder = mComponentTreeHolders.get(position);
      renderInfoWasView = holder.getRenderInfo().rendersView();

      assertNotNullRenderInfo(renderInfo);
      mRenderInfoViewCreatorController.maybeTrackViewCreator(renderInfo);
      updateHolder(holder, renderInfo);
    }

    // If this item is rendered with a view (or was rendered with a view before now) we need to
    // notify the RecyclerView's adapter that something changed.
    if (renderInfoWasView || renderInfo.rendersView()) {
      mInternalAdapter.notifyItemChanged(position);
    }

    mViewportManager.setShouldUpdate(mViewportManager.updateAffectsVisibleRange(position, 1));
  }

  /**
   * Updates the range of items starting at position. The {@link RecyclerView} gets notified
   * immediately about the item being updated.
   */
  @UiThread
  public final void updateRangeAt(int position, List<RenderInfo> renderInfos) {
    ThreadUtils.assertMainThread();

    if (SectionsDebug.ENABLED) {
      final String[] names = new String[renderInfos.size()];
      for (int i = 0; i < renderInfos.size(); i++) {
        names[i] = renderInfos.get(i).getName();
      }
      Log.d(
          SectionsDebug.TAG,
          "("
              + hashCode()
              + ") updateRangeAt "
              + position
              + ", size: "
              + renderInfos.size()
              + ", names: "
              + Arrays.toString(names));
    }

    synchronized (this) {
      try {
        for (int i = 0, size = renderInfos.size(); i < size; i++) {
          final ComponentTreeHolder holder = mComponentTreeHolders.get(position + i);
          final RenderInfo newRenderInfo = renderInfos.get(i);

          assertNotNullRenderInfo(newRenderInfo);

          // If this item is rendered with a view (or was rendered with a view before now) we still
          // need to notify the RecyclerView's adapter that something changed.
          if (newRenderInfo.rendersView() || holder.getRenderInfo().rendersView()) {
            mInternalAdapter.notifyItemChanged(position + i);
          }

          mRenderInfoViewCreatorController.maybeTrackViewCreator(newRenderInfo);
          updateHolder(holder, newRenderInfo);
        }
      } catch (IndexOutOfBoundsException e) {
        final String[] names = new String[renderInfos.size()];
        for (int i = 0; i < renderInfos.size(); i++) {
          names[i] = renderInfos.get(i).getName();
        }
        String debugInfo =
            "("
                + hashCode()
                + ") updateRangeAt "
                + position
                + ", size: "
                + renderInfos.size()
                + ", names: "
                + Arrays.toString(names);
        throw new IndexOutOfBoundsException(debugInfo + e.getMessage());
      }
    }

    mViewportManager.setShouldUpdate(
        mViewportManager.updateAffectsVisibleRange(position, renderInfos.size()));
  }

  /**
   * Moves an item from fromPosition to toPosition. If the new position of the item is within the
   * currently visible range, a layout is calculated immediately on the UI Thread.
   */
  @UiThread
  public final void moveItem(int fromPosition, int toPosition) {
    ThreadUtils.assertMainThread();

    if (SectionsDebug.ENABLED) {
      Log.d(
          SectionsDebug.TAG, "(" + hashCode() + ") moveItem " + fromPosition + " to " + toPosition);
    }

    final ComponentTreeHolder holder;
    final boolean isNewPositionInRange;
    synchronized (this) {
      holder = mComponentTreeHolders.remove(fromPosition);
      mComponentTreeHolders.add(toPosition, holder);

      isNewPositionInRange =
          mEstimatedViewportCount != UNSET
              && toPosition
                  >= mCurrentFirstVisiblePosition - (mEstimatedViewportCount * mRangeRatio)
              && toPosition
                  <= mCurrentLastVisiblePosition + (mEstimatedViewportCount * mRangeRatio);
    }
    final boolean isTreeValid = holder.isTreeValid();

    if (isTreeValid && !isNewPositionInRange) {
      holder.acquireStateAndReleaseTree(mAcquireStateHandlerOnRelease);
    }
    mInternalAdapter.notifyItemMoved(fromPosition, toPosition);

    mViewportManager.setShouldUpdate(
        mViewportManager.moveAffectsVisibleRange(
            fromPosition,
            toPosition,
            (mCurrentLastVisiblePosition - mCurrentFirstVisiblePosition + 1)));
  }

  /** Removes an item from index position. */
  @UiThread
  public final void removeItemAt(int position) {
    ThreadUtils.assertMainThread();

    assertNoRemoveOperationIfCircular(1);

    if (SectionsDebug.ENABLED) {
      Log.d(SectionsDebug.TAG, "(" + hashCode() + ") removeItemAt " + position);
    }

    final ComponentTreeHolder holder;
    synchronized (this) {
      holder = mComponentTreeHolders.remove(position);
    }
    mInternalAdapter.notifyItemRemoved(position);

    mViewportManager.setShouldUpdate(mViewportManager.removeAffectsVisibleRange(position, 1));

    // When item is removed, the corresponding view might want to disappear with an animation,
    // therefore we post a runnable to release the ComponentTree later.
    mMainThreadHandler.post(
        new Runnable() {
          @Override
          public void run() {
            holder.releaseTree();
          }
        });
  }

  /** Removes count items starting from position. */
  @UiThread
  public final void removeRangeAt(int position, int count) {
    ThreadUtils.assertMainThread();

    assertNoRemoveOperationIfCircular(count);

    if (SectionsDebug.ENABLED) {
      Log.d(
          SectionsDebug.TAG, "(" + hashCode() + ") removeRangeAt " + position + ", size: " + count);
    }

    final List<ComponentTreeHolder> toRelease = new ArrayList<>();
    synchronized (this) {
      for (int i = 0; i < count; i++) {
        final ComponentTreeHolder holder = mComponentTreeHolders.remove(position);
        toRelease.add(holder);
      }
    }
    mInternalAdapter.notifyItemRangeRemoved(position, count);

    mViewportManager.setShouldUpdate(mViewportManager.removeAffectsVisibleRange(position, count));

    // When items are removed, the corresponding views might want to disappear with animations,
    // therefore we post a runnable to release the ComponentTrees later.
    postReleaseComponentTreeHolders(toRelease);
  }

  /**
   * Called after all the change set operations (inserts, removes, etc.) in a batch have completed.
   * Async variant, may be called off the main thread.
   */
  public void notifyChangeSetCompleteAsync(
      boolean isDataChanged, ChangeSetCompleteCallback changeSetCompleteCallback) {
    final boolean isTracing = ComponentsSystrace.isTracing();
    if (isTracing) {
      ComponentsSystrace.beginSection("notifyChangeSetCompleteAsync");
    }
    try {
      if (SectionsDebug.ENABLED) {
        Log.d(SectionsDebug.TAG, "(" + hashCode() + ") notifyChangeSetCompleteAsync");
      }

      mHasAsyncOperations = true;

      assertSingleThreadForChangeSet();
      closeCurrentBatch(isDataChanged, changeSetCompleteCallback);
      if (ThreadUtils.isMainThread()) {
        applyReadyBatches();
        if (isDataChanged) {
          if (LithoStartupLogger.isEnabled(mStartupLogger)) {
            mStartupLoggerAttribution = mStartupLogger.getLatestDataAttribution();
          }

          maybeUpdateRangeOrRemeasureForMutation();
        }
      } else {
        // measure() will post this for us
        if (mIsMeasured.get()) {
          ChoreographerCompatImpl.getInstance().postFrameCallback(mApplyReadyBatchesCallback);
        }
      }
      clearThreadForChangeSet();
    } finally {
      if (isTracing) {
        ComponentsSystrace.endSection();
      }
    }
  }

  /**
   * Called after all the change set operations (inserts, removes, etc.) in a batch have completed.
   */
  @UiThread
  public void notifyChangeSetComplete(
      boolean isDataChanged, @Nullable ChangeSetCompleteCallback changeSetCompleteCallback) {
    final boolean isTracing = ComponentsSystrace.isTracing();
    if (isTracing) {
      ComponentsSystrace.beginSection("notifyChangeSetComplete");
    }
    try {
      if (SectionsDebug.ENABLED) {
        Log.d(SectionsDebug.TAG, "(" + hashCode() + ") notifyChangeSetComplete");
      }

      ThreadUtils.assertMainThread();

      if (mHasAsyncOperations) {
        throw new RuntimeException(
            "Trying to do a sync notifyChangeSetComplete when using asynchronous mutations!");
      }

      if (changeSetCompleteCallback != null) {
        changeSetCompleteCallback.onDataBound();
        mDataRenderedCallbacks.addLast(changeSetCompleteCallback);
      }
      maybeDispatchDataRendered();

      if (isDataChanged) {
        if (LithoStartupLogger.isEnabled(mStartupLogger)) {
          mStartupLoggerAttribution = mStartupLogger.getLatestDataAttribution();
        }

        maybeUpdateRangeOrRemeasureForMutation();
      }
    } finally {
      if (isTracing) {
        ComponentsSystrace.endSection();
      }
    }
  }

  @GuardedBy("this")
  private void maybeFillHScrollViewport() {
    if (!mHScrollAsyncMode || mHasFilledViewport) {
      return;
    }

    // Now that we're filling, all new batches should be inserted async to not drop frames
    mCommitPolicy = CommitPolicy.LAYOUT_BEFORE_INSERT;

    if (ThreadUtils.isMainThread()) {
      applyReadyBatches();
    } else {
      if (!mComponentTreeHolders.isEmpty()) {
        fillListViewport(mMeasuredSize.width, mMeasuredSize.height, null);
      } else if (!mAsyncBatches.isEmpty()) {
        List<ComponentTreeHolder> insertsInFirstBatch = new ArrayList<>();
        for (AsyncOperation operation : mAsyncBatches.getFirst().mOperations) {
          if (operation instanceof AsyncInsertOperation) {
            insertsInFirstBatch.add(((AsyncInsertOperation) operation).mHolder);
          }
        }
        computeLayoutsToFillListViewport(
            insertsInFirstBatch, 0, mMeasuredSize.width, mMeasuredSize.height, null);
      }

      ChoreographerCompatImpl.getInstance().postFrameCallback(mApplyReadyBatchesCallback);
    }

    mHasFilledViewport = true;
  }

  @ThreadConfined(UI)
  private void maybeDispatchDataRendered() {
    ThreadUtils.assertMainThread();
    if (mDataRenderedCallbacks.isEmpty()) {
      // early return if no pending dataRendered callbacks.
      return;
    }

    if (!mIsInitMounted) {
      // The view isn't mounted yet, OnDataRendered callbacks are postponed until mount() is called,
      // and ViewGroup#dispatchDraw(Canvas) should take care triggering OnDataRendered callbacks.
      return;
    }

    final RecyclerView recyclerView = mIsSubAdapter ? mSubAdapterRecyclerView : mMountedView;

    // Execute onDataRendered callbacks immediately if the view has been unmounted, finishes
    // dispatchDraw (no pending updates), is detached, or is visible.
    if (recyclerView == null
        || !recyclerView.hasPendingAdapterUpdates()
        || !recyclerView.isAttachedToWindow()
        || !isVisibleToUser(recyclerView)) {
      final boolean isMounted = (recyclerView != null);
      final Deque<ChangeSetCompleteCallback> snapshotCallbacks =
          new ArrayDeque<>(mDataRenderedCallbacks);
      mDataRenderedCallbacks.clear();
      mMainThreadHandler.postAtFrontOfQueue(
          new Runnable() {
            @Override
            public void run() {
              final long uptimeMillis = SystemClock.uptimeMillis();
              while (!snapshotCallbacks.isEmpty()) {
                snapshotCallbacks.pollFirst().onDataRendered(isMounted, uptimeMillis);
              }
            }
          });
    } else {
      if (mDataRenderedCallbacks.size() > DATA_RENDERED_CALLBACKS_QUEUE_MAX_SIZE) {
        mDataRenderedCallbacks.clear();
        final StringBuilder messageBuilder = new StringBuilder();
        if (recyclerView == null) {
          messageBuilder.append("recyclerView == null");
        } else {
          messageBuilder
              .append("recyclerView: ")
              .append(recyclerView)
              .append(", hasPendingAdapterUpdates(): ")
              .append(recyclerView.hasPendingAdapterUpdates())
              .append(", isAttachedToWindow(): ")
              .append(recyclerView.isAttachedToWindow())
              .append(", getWindowVisibility(): ")
              .append(recyclerView.getWindowVisibility())
              .append(", vie visible hierarchy: ")
              .append(getVisibleHierarchy(recyclerView))
              .append(", getGlobalVisibleRect(): ")
              .append(recyclerView.getGlobalVisibleRect(sDummyRect))
              .append(", isComputingLayout(): ")
              .append(recyclerView.isComputingLayout())
              .append(", isSubAdapter: ")
              .append(mIsSubAdapter);
        }
        messageBuilder
            .append(", visible range: [")
            .append(mCurrentFirstVisiblePosition)
            .append(", ")
            .append(mCurrentLastVisiblePosition)
            .append("]");
        ComponentsReporter.emitMessage(
            ComponentsReporter.LogLevel.ERROR,
            DATA_RENDERED_NOT_TRIGGERED,
            "@OnDataRendered callbacks aren't triggered as expected: " + messageBuilder);
      }
    }

    // Otherwise we'll wait for ViewGroup#dispatchDraw(Canvas), which would call this method again
    // to execute onDataRendered callbacks.
  }

  private synchronized void closeCurrentBatch(
      boolean isDataChanged, ChangeSetCompleteCallback changeSetCompleteCallback) {
    if (mCurrentBatch == null) {
      // We create a batch here even if it doesn't have any operations: this is so we can still
      // invoke the OnDataBoundListener at the appropriate time (after all preceding batches
      // complete)
      mCurrentBatch = new AsyncBatch(mCommitPolicy);
    }

    mCurrentBatch.mIsDataChanged = isDataChanged;
    mCurrentBatch.mChangeSetCompleteCallback = changeSetCompleteCallback;
    mAsyncBatches.addLast(mCurrentBatch);
    mHasAsyncBatchesToCheck.set(true);
    mCurrentBatch = null;
  }

  private void maybeUpdateRangeOrRemeasureForMutation() {
    if (!mIsMeasured.get()) {
      return;
    }

    if (mRequiresRemeasure.get() || mWrapContent) {
      maybeRequestRemeasureIfBoundsChanged();
      if (!mWrapContent) {
        return;
      }
    }

    if (!hasComputedRange()) {
      final int initialComponentPosition =
          findInitialComponentPosition(mComponentTreeHolders, mTraverseLayoutBackwards);
      if (initialComponentPosition >= 0) {
        final ComponentTreeHolderRangeInfo holderRangeInfo =
            new ComponentTreeHolderRangeInfo(initialComponentPosition, mComponentTreeHolders);
        initRange(
            mMeasuredSize.width,
            mMeasuredSize.height,
            holderRangeInfo,
            mLayoutInfo.getScrollDirection());
      }
    }

    maybePostUpdateViewportAndComputeRange();
  }

  private void assertSingleThreadForChangeSet() {
    if (!ComponentsConfiguration.isDebugModeEnabled && !ComponentsConfiguration.isEndToEndTestRun) {
      return;
    }

    final long currentThreadId = Thread.currentThread().getId();
    final long previousThreadId = mCurrentChangeSetThreadId.getAndSet(currentThreadId);

    if (currentThreadId != previousThreadId && previousThreadId != -1) {
      throw new IllegalStateException(
          "Multiple threads applying change sets at once! ("
              + previousThreadId
              + " and "
              + currentThreadId
              + ")");
    }
  }

  private void clearThreadForChangeSet() {
    if (!ComponentsConfiguration.isDebugModeEnabled && !ComponentsConfiguration.isEndToEndTestRun) {
      return;
    }

    mCurrentChangeSetThreadId.set(-1);
  }

  /**
   * Returns the {@link ComponentTree} for the item at index position. TODO 16212132 remove
   * getComponentAt from binder
   */
  @Nullable
  @Override
  public final synchronized ComponentTree getComponentAt(int position) {
    return mComponentTreeHolders.get(position).getComponentTree();
  }

  @Override
  public final synchronized ComponentTree getComponentForStickyHeaderAt(int position) {
    final ComponentTreeHolder holder = mComponentTreeHolders.get(position);
    final int childrenWidthSpec = getActualChildrenWidthSpec(holder);
    final int childrenHeightSpec = getActualChildrenHeightSpec(holder);

    if (holder.isTreeValidForSizeSpecs(childrenWidthSpec, childrenHeightSpec)) {
      return holder.getComponentTree();
    }

    // This could happen when RecyclerView is populated with new data, and first position is not 0.
    // It is possible that sticky header is above the first visible position and also it is outside
    // calculated range and its layout has not been calculated yet.
    holder.computeLayoutSync(mComponentContext, childrenWidthSpec, childrenHeightSpec, null);

    return holder.getComponentTree();
  }

  /**
   * @return the RenderInfo at this position. Since this list is modified on the main thread, this
   *     function may only be called from the main thread.
   */
  @UiThread
  @Override
  public final synchronized RenderInfo getRenderInfoAt(int position) {
    ThreadUtils.assertMainThread();
    return mComponentTreeHolders.get(position).getRenderInfo();
  }

  /**
   * @return the ComponentTreeHolder at this position. Since this list is modified on the main
   *     thread, this function may only be called from the main thread.
   */
  @UiThread
  public final synchronized ComponentTreeHolder getComponentTreeHolderAt(int position) {
    ThreadUtils.assertMainThread();
    return mComponentTreeHolders.get(position);
  }

  @VisibleForTesting
  final synchronized List<ComponentTreeHolder> getComponentTreeHolders() {
    return mComponentTreeHolders;
  }

  private static void assertNotNullRenderInfo(RenderInfo renderInfo) {
    if (renderInfo == null) {
      throw new RuntimeException("Received null RenderInfo to insert/update!");
    }
  }

  @Override
  public void bind(RecyclerView view) {
    // Nothing to do here.
  }

  @Override
  public void unbind(RecyclerView view) {
    // Nothing to do here.
  }

  private static void validateMeasureSpecs(
      int widthSpec, int heightSpec, boolean canRemeasure, int scrollDirection) {
    switch (scrollDirection) {
      case HORIZONTAL:
        if (SizeSpec.getMode(widthSpec) == SizeSpec.UNSPECIFIED) {
          throw new IllegalStateException(
              "Width mode has to be EXACTLY OR AT MOST for an horizontal scrolling RecyclerView");
        }

        if (!canRemeasure && SizeSpec.getMode(heightSpec) == SizeSpec.UNSPECIFIED) {
          throw new IllegalStateException(
              "Can't use Unspecified height on an horizontal "
                  + "scrolling Recycler if dynamic measurement is not allowed");
        }

        break;

      case VERTICAL:
        if (SizeSpec.getMode(heightSpec) == SizeSpec.UNSPECIFIED) {
          throw new IllegalStateException(
              "Height mode has to be EXACTLY OR AT MOST for a vertical scrolling RecyclerView");
        }

        if (!canRemeasure && SizeSpec.getMode(widthSpec) == SizeSpec.UNSPECIFIED) {
          throw new IllegalStateException(
              "Can't use Unspecified width on a vertical scrolling "
                  + "Recycler if dynamic measurement is not allowed");
        }
        break;

      default:
        throw new UnsupportedOperationException(
            "The orientation defined by LayoutInfo should be"
                + " either OrientationHelper.HORIZONTAL or OrientationHelper.VERTICAL");
    }
  }

  /**
   * A component mounting a RecyclerView can use this method to determine its size. A Recycler that
   * scrolls horizontally will leave the width unconstrained and will measure its children with a
   * sizeSpec for the height matching the heightSpec passed to this method.
   *
   * <p>If padding is defined on the parent component it should be subtracted from the parent size
   * specs before passing them to this method.
   *
   * <p>Currently we can't support the equivalent of MATCH_PARENT on the scrollDirection (so for
   * example we don't support MATCH_PARENT on width in an horizontal RecyclerView). This is mainly
   * because we don't have the equivalent of LayoutParams in components. We can extend the api of
   * the binder in the future to provide some more layout hints in order to support this.
   *
   * @param outSize will be populated with the measured dimensions for this Binder.
   * @param widthSpec the widthSpec to be used to measure the RecyclerView.
   * @param heightSpec the heightSpec to be used to measure the RecyclerView.
   * @param reMeasureEventHandler the EventHandler to invoke in order to trigger a re-measure.
   */
  @Override
  public void measure(
      Size outSize,
      int widthSpec,
      int heightSpec,
      @Nullable EventHandler<ReMeasureEvent> reMeasureEventHandler) {
    // This is a hack to try to give a signal to applyReadyBatches whether it should even attempt
    // to acquire the lock or bail and let measure schedule it as a runnable. This can go away
    // once we break up the locking in measure.
    // TODO(t37195892): Do not hold lock throughout measure call in RecyclerBinder
    final boolean canRemeasure = reMeasureEventHandler != null;
    final int scrollDirection = mLayoutInfo.getScrollDirection();

    validateMeasureSpecs(widthSpec, heightSpec, canRemeasure, scrollDirection);

    final boolean shouldMeasureItemForSize =
        shouldMeasureItemForSize(widthSpec, heightSpec, scrollDirection, canRemeasure);
    if (mHasManualEstimatedViewportCount && shouldMeasureItemForSize) {
      throw new RuntimeException(
          "Cannot use manual estimated viewport count when the RecyclerBinder needs an item to determine its size!");
    }

    mIsInMeasure.set(true);

    try {
      synchronized (this) {
        if (mLastWidthSpec != LayoutManagerOverrideParams.UNINITIALIZED
            && !mRequiresRemeasure.get()
            && !mWrapContent) {
          switch (scrollDirection) {
            case VERTICAL:
              if (mMeasuredSize != null
                  && MeasureComparisonUtils.isMeasureSpecCompatible(
                      mLastWidthSpec, widthSpec, mMeasuredSize.width)) {
                outSize.width = mMeasuredSize.width;
                outSize.height = mWrapContent ? mMeasuredSize.height : SizeSpec.getSize(heightSpec);

                return;
              }
              break;
            default:
              if (mMeasuredSize != null
                  && MeasureComparisonUtils.isMeasureSpecCompatible(
                      mLastHeightSpec, heightSpec, mMeasuredSize.height)) {
                outSize.width = mWrapContent ? mMeasuredSize.width : SizeSpec.getSize(widthSpec);
                outSize.height = mMeasuredSize.height;

                return;
              }
          }

          mIsMeasured.set(false);
          invalidateLayoutData();
        }

        // We have never measured before or the measures are not valid so we need to measure now.
        mLastWidthSpec = widthSpec;
        mLastHeightSpec = heightSpec;

        if (!hasComputedRange()) {
          final ComponentTreeHolderRangeInfo holderForRangeInfo = getHolderForRangeInfo();
          if (holderForRangeInfo != null) {
            initRange(
                SizeSpec.getSize(widthSpec),
                SizeSpec.getSize(heightSpec),
                holderForRangeInfo,
                scrollDirection);
          }
        }

        final Size initialMeasuredSize =
            getInitialMeasuredSize(widthSpec, heightSpec, canRemeasure);

        // At this point we might still not have a range. In this situation we should return the
        // best size we can detect from the size spec and update it when the first item comes in.
        switch (scrollDirection) {
          case VERTICAL:
            if (!shouldMeasureItemForSize || mSizeForMeasure != null) {
              mReMeasureEventEventHandler = mWrapContent ? reMeasureEventHandler : null;
            } else {
              mReMeasureEventEventHandler = reMeasureEventHandler;
              mRequiresRemeasure.set(!mWrapContent);
            }
            break;

          case HORIZONTAL:
          default:
            if (!shouldMeasureItemForSize || mSizeForMeasure != null) {
              mReMeasureEventEventHandler =
                  (mHasDynamicItemHeight || mWrapContent) ? reMeasureEventHandler : null;
              mRequiresRemeasure.set(mHasDynamicItemHeight);
            } else {
              mReMeasureEventEventHandler = reMeasureEventHandler;
              mRequiresRemeasure.set(!mWrapContent);
            }
            break;
        }

        if (mWrapContent) {
          final Size wrapSize = new Size();
          fillListViewport(initialMeasuredSize.width, initialMeasuredSize.height, wrapSize);
          outSize.width = wrapSize.width;
          outSize.height = wrapSize.height;
        } else {
          outSize.width = initialMeasuredSize.width;
          outSize.height = initialMeasuredSize.height;
        }

        mMeasuredSize = new Size(outSize.width, outSize.height);
        mIsMeasured.set(true);

        if (mComponentWarmer != null) {
          mComponentWarmer.setComponentTreeHolderFactory(getComponentTreeHolderPreparer());
        }

        maybeFillHScrollViewport();
        updateAsyncInsertOperations();

        if (mEstimatedViewportCount != RecyclerView.NO_POSITION) {
          computeRange(mCurrentFirstVisiblePosition, mCurrentLastVisiblePosition);
        }
      }
    } finally {
      mIsInMeasure.set(false);
      if (mHasAsyncOperations) {
        ensureApplyReadyBatches();
      }
    }
  }

  /** @return true if the view is measured and doesn't need remeasuring. */
  private boolean isMeasured() {
    return mIsMeasured.get() && !mRequiresRemeasure.get();
  }

  /**
   * @return true if the measure specs we are trying to measure this with cannot be used and we need
   *     to measure an item to get a size.
   */
  static boolean shouldMeasureItemForSize(
      int widthSpec, int heightSpec, int scrollDirection, boolean canRemeasure) {
    final boolean canUseSizeSpec =
        scrollDirection == VERTICAL
            ? SizeSpec.getMode(widthSpec) == SizeSpec.EXACTLY
            : SizeSpec.getMode(heightSpec) == SizeSpec.EXACTLY;

    return !canUseSizeSpec && canRemeasure;
  }

  @GuardedBy("this")
  private void fillListViewport(int maxWidth, int maxHeight, @Nullable Size outSize) {
    final boolean isTracing = ComponentsSystrace.isTracing();
    if (isTracing) {
      ComponentsSystrace.beginSection("fillListViewport");
    }

    final int firstVisiblePosition = mWrapContent ? 0 : mLayoutInfo.findFirstVisibleItemPosition();

    // NB: This does not handle 1) partially visible items 2) item decorations
    final int startIndex =
        firstVisiblePosition != RecyclerView.NO_POSITION ? firstVisiblePosition : 0;

    computeLayoutsToFillListViewport(
        mComponentTreeHolders, startIndex, maxWidth, maxHeight, outSize);

    if (!hasComputedRange()) {
      final ComponentTreeHolderRangeInfo holderForRangeInfo = getHolderForRangeInfo();
      if (holderForRangeInfo != null) {
        initRange(maxWidth, maxHeight, holderForRangeInfo, mLayoutInfo.getScrollDirection());
      }
    }

    if (isTracing) {
      ComponentsSystrace.endSection();
    }
  }

  @VisibleForTesting
  @GuardedBy("this")
  int computeLayoutsToFillListViewport(
      List<ComponentTreeHolder> holders,
      int offset,
      int maxWidth,
      int maxHeight,
      @Nullable Size outputSize) {
    final LayoutInfo.ViewportFiller filler = mLayoutInfo.createViewportFiller(maxWidth, maxHeight);
    if (filler == null) {
      return 0;
    }

    final boolean isTracing = ComponentsSystrace.isTracing();
    if (isTracing) {
      ComponentsSystrace.beginSection("computeLayoutsToFillListViewport");
    }

    final int widthSpec = SizeSpec.makeSizeSpec(maxWidth, SizeSpec.EXACTLY);
    final int heightSpec = SizeSpec.makeSizeSpec(maxHeight, SizeSpec.EXACTLY);
    final Size outSize = new Size();

    int numInserted = 0;
    int index = offset;
    while (filler.wantsMore() && index < holders.size()) {
      final ComponentTreeHolder holder = holders.get(index);
      final RenderInfo renderInfo = holder.getRenderInfo();

      // Bail as soon as we see a View since we can't tell what height it is and don't want to
      // layout too much :(
      if (renderInfo.rendersView()) {
        break;
      }

      holder.computeLayoutSync(
          mComponentContext,
          mLayoutInfo.getChildWidthSpec(widthSpec, renderInfo),
          mLayoutInfo.getChildHeightSpec(heightSpec, renderInfo),
          outSize);

      filler.add(renderInfo, outSize.width, outSize.height);

      index++;
      numInserted++;
    }

    if (outputSize != null) {
      final int fill = filler.getFill();
      if (mLayoutInfo.getScrollDirection() == VERTICAL) {
        outputSize.width = maxWidth;
        outputSize.height = Math.min(fill, maxHeight);
      } else {
        outputSize.width = Math.min(fill, maxWidth);
        outputSize.height = maxHeight;
      }
    }

    if (isTracing) {
      ComponentsSystrace.endSection();
    }

    logFillViewportInserted(numInserted, holders.size());

    return numInserted;
  }

  private void logFillViewportInserted(int numInserted, int totalSize) {
    if (SectionsDebug.ENABLED) {
      Log.d(
          SectionsDebug.TAG,
          "("
              + hashCode()
              + ") filled viewport with "
              + numInserted
              + " items (holder.size() = "
              + totalSize
              + ")");
    }
  }

  @GuardedBy("this")
  private void updateAsyncInsertOperations() {
    for (AsyncBatch batch : mAsyncBatches) {
      updateBatch(batch);
    }
    if (mCurrentBatch != null) {
      updateBatch(mCurrentBatch);
    }
  }

  @GuardedBy("this")
  private void updateBatch(AsyncBatch batch) {
    for (AsyncOperation operation : batch.mOperations) {
      if (!(operation instanceof AsyncInsertOperation)) {
        continue;
      }

      final ComponentTreeHolder holder = ((AsyncInsertOperation) operation).mHolder;
      computeLayoutAsync(holder);
    }
  }

  @GuardedBy("this")
  private void computeLayoutAsync(final ComponentTreeHolder holder) {
    // If there's an existing async layout that's compatible, this is a no-op. Otherwise, that
    // computation will be canceled (if it hasn't started) and this new one will run.
    final int widthSpec = getActualChildrenWidthSpec(holder);
    final int heightSpec = getActualChildrenHeightSpec(holder);

    if (holder.isTreeValidForSizeSpecs(widthSpec, heightSpec)) {
      if (holder.hasCompletedLatestLayout()) {
        final ComponentTree componentTree = holder.getComponentTree();
        final ComponentTree.NewLayoutStateReadyListener listener =
            componentTree.getNewLayoutStateReadyListener();
        if (listener != null) {
          componentTree.setNewLayoutStateReadyListener(null);
        }
      }

      return;
    }

    if (mAsyncInsertHandler != null) {
      mAsyncInsertHandler.post(
          new Runnable() {
            @Override
            public void run() {
              holder.computeLayoutSync(mComponentContext, widthSpec, heightSpec, new Size());
            }
          },
          "AsyncInsertLayout");
    } else {
      holder.computeLayoutAsync(mComponentContext, widthSpec, heightSpec);
    }
  }

  static int findInitialComponentPosition(
      List<ComponentTreeHolder> holders, boolean traverseBackwards) {
    if (traverseBackwards) {
      for (int i = holders.size() - 1; i >= 0; i--) {
        if (holders.get(i).getRenderInfo().rendersComponent()) {
          return i;
        }
      }
    } else {
      for (int i = 0, size = holders.size(); i < size; i++) {
        if (holders.get(i).getRenderInfo().rendersComponent()) {
          return i;
        }
      }
    }
    return -1;
  }

  /**
   * Gets the number of items currently in the adapter attached to this binder (i.e. the number of
   * items the underlying RecyclerView knows about).
   */
  @Override
  public int getItemCount() {
    return mInternalAdapter.getItemCount();
  }

  /**
   * Insert operation is not supported in case of circular recycler unless it is initial insert
   * because the indexes universe gets messed.
   */
  private void assertNoInsertOperationIfCircular() {
    if (mIsCircular && !mComponentTreeHolders.isEmpty()) {
      // Initialization of a list happens using insertRangeAt() or insertAt() operations,
      // so skip this check when mComponentTreeHolders was not populated yet
      throw new UnsupportedOperationException("Circular lists do not support insert operation");
    }
  }

  /**
   * Remove operation is not supported in case of circular recycler unless it's a removal if all
   * items because indexes universe gets messed.
   */
  @GuardedBy("this")
  private void assertNoRemoveOperationIfCircular(int removeCount) {
    if (mIsCircular
        && !mComponentTreeHolders.isEmpty()
        && mComponentTreeHolders.size() != removeCount) {
      // Allow only removal of all elements in case on notifyDataSetChanged() call
      throw new UnsupportedOperationException("Circular lists do not support insert operation");
    }
  }

  @GuardedBy("this")
  private void invalidateLayoutData() {
    final boolean isTracing = ComponentsSystrace.isTracing();
    if (isTracing) {
      ComponentsSystrace.beginSection("invalidateLayoutData");
    }

    if (!mHasManualEstimatedViewportCount) {
      mEstimatedViewportCount = UNSET;
    }

    mSizeForMeasure = null;
    for (int i = 0, size = mComponentTreeHolders.size(); i < size; i++) {
      mComponentTreeHolders.get(i).invalidateTree();
    }

    // We need to call this as we want to make sure everything is re-bound since we need new sizes
    // on all rows.
    if (Looper.myLooper() == Looper.getMainLooper() && !isRecyclerViewTargetComputingLayout()) {
      mInternalAdapter.notifyDataSetChanged();
    } else {
      mMainThreadHandler.removeCallbacks(mNotifyDatasetChangedRunnable);
      mMainThreadHandler.post(mNotifyDatasetChangedRunnable);
    }

    if (isTracing) {
      ComponentsSystrace.endSection();
    }
  }

  @GuardedBy("this")
  private void maybeScheduleAsyncLayoutsDuringInitRange(
      final ComponentAsyncInitRangeIterator asyncRangeIterator) {
    if (CollectionsUtils.isNullOrEmpty(mComponentTreeHolders)) {
      // checked null for tests
      return;
    }

    int numItemsToSchedule = mThreadPoolConfig == null ? 1 : mThreadPoolConfig.getCorePoolSize();

    for (int i = 0; i < numItemsToSchedule; i++) {
      maybeScheduleOneAsyncLayoutDuringInitRange(asyncRangeIterator);
    }
  }

  private void maybeScheduleOneAsyncLayoutDuringInitRange(
      final ComponentAsyncInitRangeIterator asyncRangeIterator) {
    final ComponentTreeHolder nextHolder = asyncRangeIterator.next();

    if (CollectionsUtils.isNullOrEmpty(mComponentTreeHolders)
        || nextHolder == null
        || mEstimatedViewportCount != UNSET) {
      // checked null for tests
      return;
    }

    final int childWidthSpec = getActualChildrenWidthSpec(nextHolder);
    final int childHeightSpec = getActualChildrenHeightSpec(nextHolder);
    if (nextHolder.isTreeValidForSizeSpecs(childWidthSpec, childHeightSpec)) {
      return;
    }

    final MeasureListener measureListener =
        new ComponentTree.MeasureListener() {
          @Override
          public void onSetRootAndSizeSpec(int layoutVersion, int w, int h, boolean stateUpdate) {
            maybeScheduleOneAsyncLayoutDuringInitRange(asyncRangeIterator);
            nextHolder.clearMeasureListener(this);
          }
        };

    nextHolder.computeLayoutAsync(
        mComponentContext, childWidthSpec, childHeightSpec, measureListener);
  }

  @VisibleForTesting
  @GuardedBy("this")
  void initRange(
      int width, int height, ComponentTreeHolderRangeInfo holderRangeInfo, int scrollDirection) {
    if (mHasManualEstimatedViewportCount) {
      return;
    }
    final boolean isTracing = ComponentsSystrace.isTracing();
    final boolean loggingForStartup = LithoStartupLogger.isEnabled(mStartupLogger);

    // We can schedule a maximum of number of items minus one (which is being calculated
    // synchronously) to run at the same time as the sync layout.
    final ComponentAsyncInitRangeIterator asyncInitRangeIterator =
        new ComponentAsyncInitRangeIterator(
            holderRangeInfo.mHolders,
            holderRangeInfo.mPosition,
            mComponentTreeHolders.size() - 1,
            mTraverseLayoutBackwards);

    if (isTracing) {
      ComponentsSystrace.beginSection("maybeScheduleAsyncLayoutsDuringInitRange");
    }
    maybeScheduleAsyncLayoutsDuringInitRange(asyncInitRangeIterator);
    if (isTracing) {
      ComponentsSystrace.endSection();
    }

    final ComponentTreeHolder holder = holderRangeInfo.mHolders.get(holderRangeInfo.mPosition);
    final int childWidthSpec = getActualChildrenWidthSpec(holder);
    final int childHeightSpec = getActualChildrenHeightSpec(holder);

    if (loggingForStartup) {
      mStartupLogger.markPoint(
          LithoStartupLogger.FIRST_LAYOUT, LithoStartupLogger.START, mStartupLoggerAttribution);
    }
    if (isTracing) {
      ComponentsSystrace.beginSection("firstLayout");
    }
    final ComponentsLogger logger;
    final String logTag;
    if (mComponentContext.getLogger() != null) {
      logger = mComponentContext.getLogger();
      logTag = mComponentContext.getLogTag();
    } else {
      logger = holder.getRenderInfo().getComponentsLogger();
      logTag = holder.getRenderInfo().getLogTag();
    }
    final PerfEvent logInitRange =
        logger == null
            ? null
            : LogTreePopulator.populatePerfEventFromLogger(
                mComponentContext,
                logger,
                logTag,
                logger.newPerformanceEvent(mComponentContext, EVENT_INIT_RANGE));

    try {
      final Size size = new Size();
      holder.computeLayoutSync(mComponentContext, childWidthSpec, childHeightSpec, size);

      final int rangeSize =
          Math.max(mLayoutInfo.approximateRangeSize(size.width, size.height, width, height), 1);

      mSizeForMeasure = size;
      mEstimatedViewportCount = rangeSize;
    } finally {
      if (logInitRange != null) {
        logger.logPerfEvent(logInitRange);
      }
      if (isTracing) {
        ComponentsSystrace.endSection();
      }
      if (loggingForStartup) {
        mStartupLogger.markPoint(
            LithoStartupLogger.FIRST_LAYOUT, LithoStartupLogger.END, mStartupLoggerAttribution);
      }
    }
  }

  private void setRangeSize(int itemWidth, int itemHeight, int width, int height) {
    if (mHasManualEstimatedViewportCount) {
      throw new RuntimeException(
          "Cannot override range size when manual estimated viewport count is set");
    }
    mEstimatedViewportCount =
        Math.max(mLayoutInfo.approximateRangeSize(itemWidth, itemHeight, width, height), 1);
  }

  @GuardedBy("this")
  private void resetMeasuredSize(int width) {
    // we will set a range anyway if it's null, no need to do this now.
    if (mSizeForMeasure == null || mHasManualEstimatedViewportCount) {
      return;
    }
    int maxHeight = 0;

    for (int i = 0, size = mComponentTreeHolders.size(); i < size; i++) {
      final ComponentTreeHolder holder = mComponentTreeHolders.get(i);
      final int measuredItemHeight = holder.getMeasuredHeight();
      if (measuredItemHeight > maxHeight) {
        maxHeight = measuredItemHeight;
      }
    }

    if (maxHeight == mSizeForMeasure.height) {
      return;
    }

    final int rangeSize =
        Math.max(
            mLayoutInfo.approximateRangeSize(
                SizeSpec.getSize(mLastWidthSpec),
                SizeSpec.getSize(mLastHeightSpec),
                width,
                maxHeight),
            1);

    mSizeForMeasure.height = maxHeight;
    mEstimatedViewportCount = rangeSize;
  }

  /**
   * This should be called when the owner {@link Component}'s onBoundsDefined is called. It will
   * inform the binder of the final measured size. The binder might decide to re-compute its
   * children layouts if the measures provided here are not compatible with the ones receive in
   * onMeasure.
   */
  @Override
  public synchronized void setSize(int width, int height) {
    if (mLastWidthSpec == LayoutManagerOverrideParams.UNINITIALIZED
        || !isCompatibleSize(
            SizeSpec.makeSizeSpec(width, SizeSpec.EXACTLY),
            SizeSpec.makeSizeSpec(height, SizeSpec.EXACTLY))) {
      measure(
          sDummySize,
          SizeSpec.makeSizeSpec(width, SizeSpec.EXACTLY),
          SizeSpec.makeSizeSpec(height, SizeSpec.EXACTLY),
          mReMeasureEventEventHandler);
    }
  }

  /**
   * Call from the owning {@link Component}'s onMount. This is where the adapter is assigned to the
   * {@link RecyclerView}.
   *
   * @param view the {@link RecyclerView} being mounted.
   */
  @UiThread
  @Override
  public void mount(RecyclerView view) {
    ThreadUtils.assertMainThread();
    if (mIsSubAdapter) {
      throw new RuntimeException("Can't mount a RecyclerView in sub adapter mode");
    }

    if (mMountedView == view) {
      return;
    }

    if (mMountedView != null) {
      unmount(mMountedView);
    }

    // In cases where we are mounting H-Scrolls, it's possible that the parent RecyclerView blocked
    // on their layout/section computation via a LayoutState future, but the runnable to update
    // the adapter on the main thread hasn't run -- this gives a chance to update the adapter before
    // we attach to this RecyclerView.
    if (mHasAsyncOperations) {
      applyReadyBatches();
    }

    // The first time we mount with a circular collection, offset its mCurrentFirstVisiblePosition
    // by Integer.MAX_VALUE / 2. We only want to do this once if the position is invalid or hasn't
    // been mounted before. This is so that the value saved in unmount doesn't keep getting the
    // offset added to it.
    if (mIsCircular) {
      if (!mIsInitMounted || mCurrentFirstVisiblePosition < 0) {
        final int jumpToMiddle = Integer.MAX_VALUE / 2;
        final int offsetFirstItem =
            mComponentTreeHolders.isEmpty() ? 0 : jumpToMiddle % mComponentTreeHolders.size();
        mCurrentFirstVisiblePosition =
            Math.max(0, mCurrentFirstVisiblePosition) + jumpToMiddle - offsetFirstItem;
      }
    }

    mMountedView = view;
    mIsInitMounted = true;

    final LayoutManager layoutManager = mLayoutInfo.getLayoutManager();

    // ItemPrefetching feature of RecyclerView clashes with RecyclerBinder's compute range
    // optimization and in certain scenarios (like sticky header) it might reset ComponentTree of
    // LithoView while it is still on screen making it render blank or zero height.
    layoutManager.setItemPrefetchEnabled(mRecyclerViewItemPrefetch);
    if (mRecyclerViewItemPrefetch) {
      mMountedView.setItemViewCacheSize(mItemViewCacheSize);
    }

    // This will force padding to be resolved on the main thread before the LayoutManager finds out
    // about this view. This will keep padding from trying to be resolved later on from a bg thread.
    // See T41844038. Longer term, it isn't safe to ever get the padding from a bg thread and it
    // will need to be passed manually to the RecyclerBinder
    view.getPaddingLeft();

    view.setLayoutManager(layoutManager);
    view.setAdapter(mInternalAdapter);
    view.addOnScrollListener(mViewportManager.getScrollListener());

    if (layoutManager instanceof NeedsBgPaddingInfo) {
      ((NeedsBgPaddingInfo) layoutManager)
          .setBgPaddingInfo(
              new Rect(
                  view.getPaddingLeft(),
                  view.getPaddingTop(),
                  view.getPaddingRight(),
                  view.getPaddingBottom()));
    }

    registerDrawListener(view);

    mLayoutInfo.setRenderInfoCollection(this);

    mViewportManager.addViewportChangedListener(mViewportChangedListener);

    if (mCurrentFirstVisiblePosition != RecyclerView.NO_POSITION
        && mCurrentFirstVisiblePosition >= 0
        && !mIsCircular) {
      if (mSmoothScrollAlignmentType != null) {
        scrollSmoothToPosition(
            mCurrentFirstVisiblePosition, mCurrentOffset, mSmoothScrollAlignmentType);
      } else {
        mLayoutInfo.scrollToPositionWithOffset(mCurrentFirstVisiblePosition, mCurrentOffset);
      }
    } else if (mIsCircular) {
      // Initialize circular RecyclerView position
      view.scrollToPosition(mCurrentFirstVisiblePosition);
      // Circular RecyclerViews report their size as Integer.MAX_VALUE, which makes Talkback
      // actually announce "In List, 2147483674 items". This overrides the row/column count on the
      // AccessibilityNodeInfo to accurately reflect the real number of items in the list.
      view.setAccessibilityDelegateCompat(
          new RecyclerViewAccessibilityDelegate(view) {
            @Override
            public void onInitializeAccessibilityNodeInfo(
                View host, AccessibilityNodeInfoCompat info) {
              super.onInitializeAccessibilityNodeInfo(host, info);

              int itemCount = getComponentTreeHolders().size();
              int rowCount = layoutManager.canScrollVertically() ? itemCount : 1;
              int colCount = layoutManager.canScrollHorizontally() ? itemCount : 1;

              final AccessibilityNodeInfoCompat.CollectionInfoCompat collectionInfo =
                  AccessibilityNodeInfoCompat.CollectionInfoCompat.obtain(
                      rowCount,
                      colCount,
                      false,
                      AccessibilityNodeInfoCompat.CollectionInfoCompat.SELECTION_MODE_NONE);
              info.setCollectionInfo(collectionInfo);
            }
          });
    }

    enableStickyHeader(mMountedView);
  }

  private void enableStickyHeader(RecyclerView recyclerView) {
    if (mIsCircular) {
      Log.w(TAG, "Sticky header is not supported for circular RecyclerViews");
      return;
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
      // Sticky header needs view translation APIs which are not available in Gingerbread and below.
      Log.w(TAG, "Sticky header is supported only on ICS (API14) and above");
      return;
    }
    if (recyclerView == null) {
      return;
    }
    SectionsRecyclerView sectionsRecycler = SectionsRecyclerView.getParentRecycler(recyclerView);
    if (sectionsRecycler == null) {
      return;
    }

    if (mStickyHeaderControllerFactory == null) {
      mStickyHeaderController = new StickyHeaderControllerImpl((HasStickyHeader) this);
    } else {
      mStickyHeaderController =
          mStickyHeaderControllerFactory.getController((HasStickyHeader) this);
    }

    mStickyHeaderController.init(sectionsRecycler);
  }

  /**
   * Call from the owning {@link Component}'s onUnmount. This is where the adapter is removed from
   * the {@link RecyclerView}.
   *
   * @param view the {@link RecyclerView} being unmounted.
   */
  @UiThread
  @Override
  public void unmount(RecyclerView view) {
    ThreadUtils.assertMainThread();
    if (mIsSubAdapter) {
      throw new RuntimeException("Can't unmount a RecyclerView in sub adapter mode");
    }

    final LayoutManager layoutManager = mLayoutInfo.getLayoutManager();
    final View firstView = layoutManager.findViewByPosition(mCurrentFirstVisiblePosition);

    if (firstView != null) {
      final boolean reverseLayout = getReverseLayout();

      if (mLayoutInfo.getScrollDirection() == HORIZONTAL) {
        mCurrentOffset =
            reverseLayout
                ? view.getWidth()
                    - layoutManager.getPaddingRight()
                    - layoutManager.getDecoratedRight(firstView)
                : layoutManager.getDecoratedLeft(firstView) - layoutManager.getPaddingLeft();
      } else {
        mCurrentOffset =
            reverseLayout
                ? view.getHeight()
                    - layoutManager.getPaddingBottom()
                    - layoutManager.getDecoratedBottom(firstView)
                : layoutManager.getDecoratedTop(firstView) - layoutManager.getPaddingTop();
      }
    } else {
      mCurrentOffset = 0;
    }

    view.removeOnScrollListener(mViewportManager.getScrollListener());

    unregisterDrawListener(view);
    maybeDispatchDataRendered();

    view.setAdapter(null);
    view.setLayoutManager(null);

    mViewportManager.removeViewportChangedListener(mViewportChangedListener);

    // We might have already unmounted this view when calling mount with a different view. In this
    // case we can just return here.
    if (mMountedView != view) {
      return;
    }

    mMountedView = null;
    if (mStickyHeaderController != null) {
      mStickyHeaderController.reset();
    }

    mLayoutInfo.setRenderInfoCollection(null);
  }

  private void registerDrawListener(final RecyclerView view) {
    if (view instanceof HasPostDispatchDrawListener) {
      ((HasPostDispatchDrawListener) view)
          .registerPostDispatchDrawListener(mPostDispatchDrawListener);
    } else if (view.getViewTreeObserver() != null) {
      view.getViewTreeObserver().addOnPreDrawListener(mOnPreDrawListener);
      // To make sure we unregister the OnPreDrawListener before RecyclerView is detached.
      view.addOnAttachStateChangeListener(mOnAttachStateChangeListener);
    }
  }

  private void unregisterDrawListener(final RecyclerView view) {
    if (view instanceof HasPostDispatchDrawListener) {
      ((HasPostDispatchDrawListener) view)
          .unregisterPostDispatchDrawListener(mPostDispatchDrawListener);
    } else if (view.getViewTreeObserver() != null) {
      view.getViewTreeObserver().removeOnPreDrawListener(mOnPreDrawListener);
    }
  }

  @UiThread
  public void scrollToPosition(int position) {
    if (mMountedView == null) {
      mCurrentFirstVisiblePosition = position;
      return;
    }
    mMountedView.scrollToPosition(position);
  }

  @UiThread
  public void scrollSmoothToPosition(
      Object id, final int offset, final SmoothScrollAlignmentType type) {
    final int index = getPositionForId(id);
    scrollSmoothToPosition(index, offset, type);
  }

  @UiThread
  public void scrollSmoothToPosition(
      int position, final int offset, final SmoothScrollAlignmentType type) {
    if (mMountedView == null) {
      mCurrentFirstVisiblePosition = position;
      mCurrentOffset = offset;
      mSmoothScrollAlignmentType = type;
      return;
    }

    final RecyclerView.SmoothScroller smoothScroller =
        SnapUtil.getSmoothScrollerWithOffset(mComponentContext.getAndroidContext(), offset, type);
    smoothScroller.setTargetPosition(position);
    mMountedView.getLayoutManager().startSmoothScroll(smoothScroller);
  }

  @UiThread
  private synchronized int getPositionForId(final Object id) {
    if (id == null) {
      return -1;
    }
    for (int i = 0; i < mComponentTreeHolders.size(); i++) {
      final ComponentTreeHolder componentTreeHolder = mComponentTreeHolders.get(i);

      final RenderInfo renderInfo = componentTreeHolder.getRenderInfo();
      if (renderInfo == null) {
        continue;
      }
      @Nullable final Object childId = renderInfo.getCustomAttribute(ID_CUSTOM_ATTR_KEY);

      if (id.equals(childId)) {
        return i;
      }
    }
    return -1;
  }

  @UiThread
  public void scrollToPositionWithOffset(Object id, int offset) {
    final int index = getPositionForId(id);
    scrollToPositionWithOffset(index, offset);
  }

  @UiThread
  public void scrollToPositionWithOffset(int position, int offset) {
    if (mMountedView == null) {
      mCurrentFirstVisiblePosition = position;
      mCurrentOffset = offset;
      return;
    }

    mLayoutInfo.scrollToPositionWithOffset(position, offset);
  }

  @GuardedBy("this")
  private boolean isCompatibleSize(int widthSpec, int heightSpec) {
    if (mMeasuredSize == null) {
      return false;
    }

    final int scrollDirection = mLayoutInfo.getScrollDirection();

    if (mLastWidthSpec != LayoutManagerOverrideParams.UNINITIALIZED) {

      switch (scrollDirection) {
        case HORIZONTAL:
          return isMeasureSpecCompatible(mLastHeightSpec, heightSpec, mMeasuredSize.height);
        case VERTICAL:
          return isMeasureSpecCompatible(mLastWidthSpec, widthSpec, mMeasuredSize.width);
      }
    }

    return false;
  }

  @Override
  public int findFirstVisibleItemPosition() {
    return mLayoutInfo.findFirstVisibleItemPosition();
  }

  @Override
  public int findFirstFullyVisibleItemPosition() {
    return mLayoutInfo.findFirstFullyVisibleItemPosition();
  }

  @Override
  public int findLastVisibleItemPosition() {
    return mLayoutInfo.findLastVisibleItemPosition();
  }

  @Override
  public int findLastFullyVisibleItemPosition() {
    return mLayoutInfo.findLastFullyVisibleItemPosition();
  }

  @Override
  @UiThread
  @GuardedBy("this")
  public boolean isSticky(int position) {
    return isValidPosition(position)
        && mComponentTreeHolders.get(position).getRenderInfo().isSticky();
  }

  @Override
  @UiThread
  @GuardedBy("this")
  public boolean isValidPosition(int position) {
    return position >= 0 && position < mComponentTreeHolders.size();
  }

  /**
   * @return the internal RecyclerView.Adapter that is used to communicate to the RecyclerView. This
   *     should generally only be useful when operating in sub-adapter mode.
   */
  public RecyclerView.Adapter getInternalAdapter() {
    return mInternalAdapter;
  }

  private static class RangeCalculationResult {

    // The estimated number of items needed to fill the viewport.
    private int estimatedViewportCount;
    // The size computed for the first Component.
    private int measuredSize;
  }

  @Override
  @UiThread
  public void setViewportChangedListener(@Nullable ViewportChanged viewportChangedListener) {
    mViewportManager.addViewportChangedListener(viewportChangedListener);
  }

  @VisibleForTesting
  void onNewVisibleRange(int firstVisiblePosition, int lastVisiblePosition) {
    mCurrentFirstVisiblePosition = firstVisiblePosition;
    mCurrentLastVisiblePosition = lastVisiblePosition;
    mViewportManager.resetShouldUpdate();
    maybePostUpdateViewportAndComputeRange();
  }

  /**
   * Updates the visible range when in sub adapter mode. Do not call this otherwise. This method
   * exists because in sub adapter mode, the RecyclerBinder is never mounted to a RecyclerView and
   * needs outside signals from the multiplexing adapter to determine which of its indices are
   * visible.
   */
  public void updateSubAdapterVisibleRange(int firstVisiblePosition, int lastVisiblePosition) {
    if (!mIsSubAdapter) {
      throw new RuntimeException(
          "updateSubAdapterVisibleRange can only be called in sub adapter mode");
    }
    onNewVisibleRange(firstVisiblePosition, lastVisiblePosition);
  }

  /**
   * Updates the working range when in sub adapter mode. Do not call this otherwise. This method
   * exists because in sub adapter mode, the RecyclerBinder is never mounted to a RecyclerView and
   * needs outside signals from the multiplexing adapter to determine which of its indices are
   * visible.
   */
  public void updateSubAdapterWorkingRange(
      int firstVisibleIndex,
      int lastVisibleIndex,
      int firstFullyVisibleIndex,
      int lastFullyVisibleIndex) {
    if (!mIsSubAdapter) {
      throw new RuntimeException(
          "updateSubAdapterWorkingRange can only be called in sub adapter mode");
    }
    onNewWorkingRange(
        firstVisibleIndex, lastVisibleIndex, firstFullyVisibleIndex, lastFullyVisibleIndex);
  }

  @VisibleForTesting
  void onNewWorkingRange(
      int firstVisibleIndex,
      int lastVisibleIndex,
      int firstFullyVisibleIndex,
      int lastFullyVisibleIndex) {
    if (mEstimatedViewportCount == UNSET
        || firstVisibleIndex == RecyclerView.NO_POSITION
        || lastVisibleIndex == RecyclerView.NO_POSITION) {
      return;
    }

    final int rangeSize = Math.max(mEstimatedViewportCount, lastVisibleIndex - firstVisibleIndex);
    final int layoutRangeSize = (int) (rangeSize * mRangeRatio);
    final int rangeStart = Math.max(0, firstVisibleIndex - layoutRangeSize);
    final int rangeEnd =
        Math.min(firstVisibleIndex + rangeSize + layoutRangeSize, mComponentTreeHolders.size() - 1);

    for (int position = rangeStart; position <= rangeEnd; position++) {
      final ComponentTreeHolder holder = mComponentTreeHolders.get(position);
      holder.checkWorkingRangeAndDispatch(
          position,
          firstVisibleIndex,
          lastVisibleIndex,
          firstFullyVisibleIndex,
          lastFullyVisibleIndex);
    }
  }

  private void maybePostUpdateViewportAndComputeRange() {
    if (mMountedView != null && mViewportManager.shouldUpdate()) {
      mMountedView.removeCallbacks(mUpdateViewportRunnable);
      ViewCompat.postOnAnimation(mMountedView, mUpdateViewportRunnable);
    }
    computeRange(mCurrentFirstVisiblePosition, mCurrentLastVisiblePosition);
  }

  private void computeRange(int firstVisible, int lastVisible) {
    computeRange(firstVisible, lastVisible, mRangeTraverser);
  }

  private void computeRange(int firstVisible, int lastVisible, RecyclerRangeTraverser traverser) {
    final int rangeSize;
    final int rangeStart;
    final int rangeEnd;
    final int treeHoldersSize;
    final boolean didRangeExtremitiesChange;

    synchronized (this) {
      if (!isMeasured() || mEstimatedViewportCount == UNSET) {
        return;
      }

      if (firstVisible == RecyclerView.NO_POSITION || lastVisible == RecyclerView.NO_POSITION) {
        firstVisible = lastVisible = 0;
      }
      rangeSize = Math.max(mEstimatedViewportCount, lastVisible - firstVisible);
      treeHoldersSize = mComponentTreeHolders.size();
      if (mIsCircular) {
        rangeStart = 0;
        rangeEnd = treeHoldersSize;
      } else {
        rangeStart = firstVisible - (int) (rangeSize * mRangeRatio);
        rangeEnd = firstVisible + rangeSize + (int) (rangeSize * mRangeRatio);
      }

      if (rangeStart < mLowestRangeStartSinceDeletes || rangeEnd > mHighestRangeStartSinceDeletes) {
        didRangeExtremitiesChange = true;
        mLowestRangeStartSinceDeletes = rangeStart;
        mHighestRangeStartSinceDeletes = rangeEnd;
      } else {
        didRangeExtremitiesChange = false;
      }
    }

    RecyclerRangeTraverser.Processor processor;
    switch (mRecyclingStrategy) {
      case RecyclingStrategy.RETAIN_MAXIMUM_RANGE:
        processor =
            new RecyclerRangeTraverser.Processor() {
              @Override
              public boolean process(int index) {
                return computeRangeLayoutWithRetainMaximumRange(
                    index, rangeStart, rangeEnd, treeHoldersSize, didRangeExtremitiesChange);
              }
            };
        break;

      case RecyclingStrategy.DEFAULT:
      default:
        processor =
            new RecyclerRangeTraverser.Processor() {
              @Override
              public boolean process(int index) {
                return computeRangeLayoutAt(index, rangeStart, rangeEnd, treeHoldersSize);
              }
            };
    }

    traverser.traverse(0, treeHoldersSize, firstVisible, lastVisible, processor);
  }

  /** @return Whether or not to continue layout computation for current range */
  private boolean computeRangeLayoutAt(
      int index, int rangeStart, int rangeEnd, int treeHoldersSize) {

    final ComponentTreeHolder holder;
    final int childrenWidthSpec, childrenHeightSpec;

    synchronized (this) {
      // Someone modified the ComponentsTreeHolders while we were computing this range. We
      // can just bail as another range will be computed.
      if (treeHoldersSize != mComponentTreeHolders.size()) {
        return false;
      }

      holder = mComponentTreeHolders.get(index);

      if (holder.getRenderInfo().rendersView()) {
        return true;
      }

      childrenWidthSpec = getActualChildrenWidthSpec(holder);
      childrenHeightSpec = getActualChildrenHeightSpec(holder);
    }

    if ((index >= rangeStart || holder.getRenderInfo().isSticky()) && index <= rangeEnd) {
      if (!holder.isTreeValidForSizeSpecs(childrenWidthSpec, childrenHeightSpec)) {
        holder.computeLayoutAsync(mComponentContext, childrenWidthSpec, childrenHeightSpec);
      }
    } else {
      maybeReleaseOutOfRangeTree(holder);
    }

    return true;
  }

  /** @return Whether or not to continue layout computation for current range */
  private boolean computeRangeLayoutWithRetainMaximumRange(
      int index, int rangeStart, int rangeEnd, int treeHoldersSize, boolean allowDeletions) {

    final ComponentTreeHolder holder;
    int childrenWidthSpec = 0, childrenHeightSpec = 0;
    final boolean shouldTryComputeLayout;

    synchronized (this) {
      // Someone modified the ComponentsTreeHolders while we were computing this range. We
      // can just bail as another range will be computed.
      if (treeHoldersSize != mComponentTreeHolders.size()) {
        return false;
      }

      holder = mComponentTreeHolders.get(index);

      if (holder.getRenderInfo().rendersView()) {
        return true;
      }

      shouldTryComputeLayout =
          (index >= rangeStart || holder.getRenderInfo().isSticky()) && index <= rangeEnd;

      if (shouldTryComputeLayout) {
        childrenWidthSpec = getActualChildrenWidthSpec(holder);
        childrenHeightSpec = getActualChildrenHeightSpec(holder);
      }
    }

    if (shouldTryComputeLayout) {
      if (!holder.isTreeValidForSizeSpecs(childrenWidthSpec, childrenHeightSpec)) {
        holder.computeLayoutAsync(mComponentContext, childrenWidthSpec, childrenHeightSpec);
      }
    } else if (allowDeletions && canReleaseTree(holder)) {
      maybeReleaseOutOfRangeTree(holder);
    }

    return true;
  }

  private void maybeReleaseOutOfRangeTree(final ComponentTreeHolder holder) {
    if (ThreadUtils.isMainThread()) {
      maybeAcquireStateAndReleaseTree(holder, mAcquireStateHandlerOnRelease);
    } else {
      mMainThreadHandler.post(getMaybeAcquireStateAndReleaseTreeRunnable(holder));
    }
  }

  private Runnable getMaybeAcquireStateAndReleaseTreeRunnable(final ComponentTreeHolder holder) {
    return new Runnable() {
      @Override
      public void run() {
        maybeAcquireStateAndReleaseTree(holder, mAcquireStateHandlerOnRelease);
      }
    };
  }

  @UiThread
  private static void maybeAcquireStateAndReleaseTree(
      ComponentTreeHolder holder, boolean acquireStateAndReleaseTree) {
    if (canReleaseTree(holder)
        && (holder.getComponentTree() != null
            && holder.getComponentTree().getLithoView() == null)) {
      holder.acquireStateAndReleaseTree(acquireStateAndReleaseTree);
    }
  }

  private static boolean canReleaseTree(ComponentTreeHolder holder) {
    return holder.isTreeValid()
        && !holder.shouldPreventRelease()
        && !holder.getRenderInfo().isSticky()
        && holder.getComponentTree() != null;
  }

  private boolean getReverseLayout() {
    final LayoutManager layoutManager = mLayoutInfo.getLayoutManager();
    if (layoutManager instanceof LinearLayoutManager) {
      return ((LinearLayoutManager) layoutManager).getReverseLayout();
    } else {
      return false;
    }
  }

  private boolean getStackFromEnd() {
    final LayoutManager layoutManager = mLayoutInfo.getLayoutManager();
    if (layoutManager instanceof LinearLayoutManager) {
      return ((LinearLayoutManager) layoutManager).getStackFromEnd();
    } else {
      return false;
    }
  }

  @VisibleForTesting
  @Nullable
  // todo T40814333 change tests so this isn't needed.
  RangeCalculationResult getRangeCalculationResult() {
    if (mSizeForMeasure == null && mEstimatedViewportCount == UNSET) {
      return null;
    }

    final RangeCalculationResult range = new RangeCalculationResult();
    range.measuredSize = getSizeForMeasuring();
    range.estimatedViewportCount = mEstimatedViewportCount;

    return range;
  }

  private boolean hasComputedRange() {
    return (mSizeForMeasure != null && mEstimatedViewportCount != UNSET)
        || mHasManualEstimatedViewportCount;
  }

  /**
   * If measure is called with measure specs that cannot be used to measure the recyclerview, the
   * size of one of an item will be used to determine how to measure instead.
   *
   * @return a size value that can be used to measure the dimension of the recycler that has unknown
   *     size, which is width for vertical scrolling recyclers or height for horizontal scrolling
   *     recyclers.
   */
  private int getSizeForMeasuring() {
    if (mSizeForMeasure == null) {
      return UNSET;
    }

    return mLayoutInfo.getScrollDirection() == HORIZONTAL
        ? mSizeForMeasure.height
        : mSizeForMeasure.width;
  }

  @GuardedBy("this")
  private int getActualChildrenWidthSpec(final ComponentTreeHolder treeHolder) {
    if (isMeasured()) {
      return mLayoutInfo.getChildWidthSpec(
          SizeSpec.makeSizeSpec(mMeasuredSize.width, SizeSpec.EXACTLY), treeHolder.getRenderInfo());
    }

    return mLayoutInfo.getChildWidthSpec(mLastWidthSpec, treeHolder.getRenderInfo());
  }

  @GuardedBy("this")
  private int getActualChildrenHeightSpec(final ComponentTreeHolder treeHolder) {
    if (mHasDynamicItemHeight) {
      return SizeSpec.UNSPECIFIED;
    }

    if (isMeasured()) {
      return mLayoutInfo.getChildHeightSpec(
          SizeSpec.makeSizeSpec(mMeasuredSize.height, SizeSpec.EXACTLY),
          treeHolder.getRenderInfo());
    }

    return mLayoutInfo.getChildHeightSpec(mLastHeightSpec, treeHolder.getRenderInfo());
  }

  @AnyThread
  public void setCommitPolicy(@CommitPolicy int commitPolicy) {
    mCommitPolicy = commitPolicy;
  }

  private AsyncInsertOperation createAsyncInsertOperation(int position, RenderInfo renderInfo) {
    final ComponentTreeHolder holder = createComponentTreeHolder(renderInfo);
    holder.setInserted(false);
    return new AsyncInsertOperation(position, holder);
  }

  /** Async operation types. */
  @IntDef({
    Operation.INSERT,
    Operation.UPDATE,
    Operation.UPDATE_RANGE,
    Operation.REMOVE,
    Operation.REMOVE_RANGE,
    Operation.MOVE
  })
  @Retention(RetentionPolicy.SOURCE)
  private @interface Operation {
    int INSERT = 0;
    int UPDATE = 1;
    int UPDATE_RANGE = 2;
    int REMOVE = 3;
    int REMOVE_RANGE = 4;
    int MOVE = 5;
  }

  /**
   * Defines when a batch should be committed: - IMMEDIATE: Commit batches to the RecyclerView as
   * soon as possible. - LAYOUT_BEFORE_INSERT: Commit batches to the RecyclerView only after the
   * layouts for all insert operations have been completed.
   */
  @IntDef({CommitPolicy.IMMEDIATE, CommitPolicy.LAYOUT_BEFORE_INSERT})
  @Retention(RetentionPolicy.SOURCE)
  public @interface CommitPolicy {
    int IMMEDIATE = 0;
    int LAYOUT_BEFORE_INSERT = 1;
  }

  /** Strategies for recycling layouts of items in binder */
  @IntDef({RecyclingStrategy.DEFAULT, RecyclingStrategy.RETAIN_MAXIMUM_RANGE})
  public @interface RecyclingStrategy {
    int DEFAULT = 0;
    int RETAIN_MAXIMUM_RANGE = 1;
  }

  /** An operation received from one of the *Async methods, pending execution. */
  private abstract static class AsyncOperation {

    private final int mOperation;

    public AsyncOperation(int operation) {
      mOperation = operation;
    }
  }

  private static final class AsyncInsertOperation extends AsyncOperation {

    private final int mPosition;
    private final ComponentTreeHolder mHolder;

    public AsyncInsertOperation(int position, ComponentTreeHolder holder) {
      super(Operation.INSERT);
      mPosition = position;
      mHolder = holder;
    }
  }

  private static final class AsyncUpdateOperation extends AsyncOperation {

    private final int mPosition;
    private final RenderInfo mRenderInfo;

    public AsyncUpdateOperation(int position, RenderInfo renderInfo) {
      super(Operation.UPDATE);
      mPosition = position;
      mRenderInfo = renderInfo;
    }
  }

  private static final class AsyncUpdateRangeOperation extends AsyncOperation {

    private final int mPosition;
    private final List<RenderInfo> mRenderInfos;

    public AsyncUpdateRangeOperation(int position, List<RenderInfo> renderInfos) {
      super(Operation.UPDATE_RANGE);
      mPosition = position;
      mRenderInfos = renderInfos;
    }
  }

  private static final class AsyncRemoveOperation extends AsyncOperation {

    private final int mPosition;

    public AsyncRemoveOperation(int position) {
      super(Operation.REMOVE);
      mPosition = position;
    }
  }

  private static final class AsyncRemoveRangeOperation extends AsyncOperation {

    private final int mPosition;
    private final int mCount;

    public AsyncRemoveRangeOperation(int position, int count) {
      super(Operation.REMOVE_RANGE);
      mPosition = position;
      mCount = count;
    }
  }

  private static final class AsyncMoveOperation extends AsyncOperation {

    private final int mFromPosition;
    private final int mToPosition;

    public AsyncMoveOperation(int fromPosition, int toPosition) {
      super(Operation.MOVE);
      mFromPosition = fromPosition;
      mToPosition = toPosition;
    }
  }

  /**
   * A batch of {@link AsyncOperation}s that should be applied all at once. The OnDataBoundListener
   * should be called once all these operations are applied.
   */
  private static final class AsyncBatch {

    private final ArrayList<AsyncOperation> mOperations = new ArrayList<>();
    private boolean mIsDataChanged;
    private ChangeSetCompleteCallback mChangeSetCompleteCallback;
    private @CommitPolicy int mCommitPolicy;

    public AsyncBatch(@CommitPolicy int commitPolicy) {
      mCommitPolicy = commitPolicy;
    }
  }

  /** Default implementation of RecyclerBinderViewHolder */
  private static class BaseViewHolder extends RecyclerBinderViewHolder {

    private final boolean isLithoViewType;
    private @Nullable ViewBinder viewBinder;

    public BaseViewHolder(View itemView, boolean isLithoViewType) {
      super(itemView);
      this.isLithoViewType = isLithoViewType;
    }

    @Override
    @Nullable
    public LithoView getLithoView() {
      if (isLithoViewType) {
        return (LithoView) itemView;
      }
      return null;
    }

    @Override
    public void setLithoViewLayoutParams(
        LithoView lithoView,
        int width,
        int height,
        int widthSpec,
        int heightSpec,
        boolean isFullSpan) {
      final RecyclerViewLayoutManagerOverrideParams layoutParams =
          new RecyclerViewLayoutManagerOverrideParams(
              width, height, widthSpec, heightSpec, isFullSpan);
      lithoView.setLayoutParams(layoutParams);
    }
  }

  private class DefaultRecyclerBinderAdapterDelegate
      implements RecyclerBinderAdapterDelegate<BaseViewHolder> {

    @Override
    public BaseViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
      final LithoView lithoView =
          mLithoViewFactory == null
              ? new LithoView(mComponentContext, null)
              : mLithoViewFactory.createLithoView(mComponentContext);

      return new BaseViewHolder(lithoView, true);
    }

    @Override
    public void onBindViewHolder(
        BaseViewHolder viewHolder,
        int position,
        @Nullable ComponentTree componentTree,
        RenderInfo renderInfo) {}

    @Override
    public void onViewRecycled(BaseViewHolder viewHolder) {}

    @Override
    public boolean hasStableIds() {
      return mEnableStableIds;
    }

    @Override
    public long getItemId(int position) {
      return mComponentTreeHolders.get(position).getId();
    }
  }

  private class InternalAdapter extends RecyclerView.Adapter<RecyclerBinderViewHolder>
      implements RecyclerBinderAdapter {

    InternalAdapter() {
      setHasStableIds(mRecyclerBinderAdapterDelegate.hasStableIds());
    }

    @Override
    public RecyclerBinderViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
      final ViewCreator viewCreator = mRenderInfoViewCreatorController.getViewCreator(viewType);
      if (viewCreator != null) {
        final View view = viewCreator.createView(mComponentContext.getAndroidContext(), parent);
        try {
          return new BaseViewHolder(view, false);
        } catch (IllegalArgumentException ex) {
          throw new IllegalArgumentException(
              "createView() may not return null from :"
                  + getClassNameForDebug(viewCreator.getClass()),
              ex);
        }
      }
      return mRecyclerBinderAdapterDelegate.onCreateViewHolder(parent, viewType);
    }

    @Override
    @GuardedBy("RecyclerBinder.this")
    public void onBindViewHolder(RecyclerBinderViewHolder holder, int position) {
      final boolean loggingForStartup =
          LithoStartupLogger.isEnabled(mStartupLogger) && !mStartupLoggerAttribution.isEmpty();
      final int normalizedPosition = getNormalizedPosition(position);

      // We can ignore the synchronization here. We'll only add to this from the UiThread.
      // This read only happens on the UiThread as well and we are never writing this here.
      final ComponentTreeHolder componentTreeHolder = mComponentTreeHolders.get(normalizedPosition);

      final RenderInfo renderInfo = componentTreeHolder.getRenderInfo();
      if (renderInfo.rendersComponent()) {
        final LithoView lithoView = holder.getLithoView();
        lithoView.setInvalidStateLogParamsList(mInvalidStateLogParamsList);
        final int childrenWidthSpec = getActualChildrenWidthSpec(componentTreeHolder);
        final int childrenHeightSpec = getActualChildrenHeightSpec(componentTreeHolder);
        if (!componentTreeHolder.isTreeValidForSizeSpecs(childrenWidthSpec, childrenHeightSpec)) {
          if (ComponentsConfiguration.computeRangeOnSyncLayout) {
            // Since synchronous layout is about to happen, and the ScrollListener that updates the
            // visible and working ranges will not fire until after the full frame is rendered,
            // we want to kick off background layout for the estimated visible range in the
            // scrolling direction in an attempt to take advantage of more parallel layout.
            if (mCurrentFirstVisiblePosition != RecyclerView.NO_POSITION
                && mCurrentLastVisiblePosition != RecyclerView.NO_POSITION) {
              // Get the last known visible range if available.
              final int range = mCurrentLastVisiblePosition - mCurrentFirstVisiblePosition;
              if (position > mCurrentLastVisiblePosition) {
                // Scrolling down
                computeRange(position, position + range, RecyclerRangeTraverser.FORWARD_TRAVERSER);
              } else if (position < mCurrentFirstVisiblePosition) {
                // Scrolling up
                computeRange(position - range, position, RecyclerRangeTraverser.BACKWARD_TRAVERSER);
              }
            }
          }

          final Size size = new Size();
          componentTreeHolder.computeLayoutSync(
              mComponentContext, childrenWidthSpec, childrenHeightSpec, size);
        }
        final boolean isOrientationVertical = mLayoutInfo.getScrollDirection() == VERTICAL;

        final int width;
        final int height;
        if (SizeSpec.getMode(childrenWidthSpec) == SizeSpec.EXACTLY) {
          width = SizeSpec.getSize(childrenWidthSpec);
        } else if (isOrientationVertical) {
          width = MATCH_PARENT;
        } else {
          width = WRAP_CONTENT;
        }

        if (SizeSpec.getMode(childrenHeightSpec) == SizeSpec.EXACTLY) {
          height = SizeSpec.getSize(childrenHeightSpec);
        } else if (isOrientationVertical) {
          height = WRAP_CONTENT;
        } else {
          height = MATCH_PARENT;
        }

        holder.setLithoViewLayoutParams(
            lithoView,
            width,
            height,
            childrenWidthSpec,
            childrenHeightSpec,
            renderInfo.isFullSpan());
        lithoView.setComponentTree(componentTreeHolder.getComponentTree());

        if (componentTreeHolder.getRenderInfo().getRenderCompleteEventHandler() != null
            && componentTreeHolder.getRenderState() == RENDER_UNINITIALIZED) {
          lithoView.setOnPostDrawListener(
              new LithoView.OnPostDrawListener() {
                @Override
                public void onPostDraw() {
                  final int position = mMountedView.getChildAdapterPosition(holder.itemView);
                  if (position != RecyclerView.NO_POSITION) {
                    notifyItemRenderCompleteAt(position, SystemClock.uptimeMillis());
                    lithoView.setOnPostDrawListener(null);
                  }
                }
              });
        }
        if (loggingForStartup) {
          lithoView.setMountStartupLoggingInfo(
              mStartupLogger,
              mStartupLoggerAttribution,
              mFirstMountLogged,
              mLastMountLogged,
              position == getItemCount(),
              isOrientationVertical);
        } else {
          lithoView.resetMountStartupLoggingInfo();
        }
        mRecyclerBinderAdapterDelegate.onBindViewHolder(
            holder, normalizedPosition, componentTreeHolder.getComponentTree(), renderInfo);

        if (mComponentsConfiguration.requestMountForPrefetchedItems) {
          // Try to pre-mount components marked as excludeFromIncrementalMount.
          MountHelper.requestMount(componentTreeHolder.getComponentTree(), sEmptyRect, false);
        }
      } else if (holder instanceof BaseViewHolder) {
        BaseViewHolder baseViewHolder = (BaseViewHolder) holder;
        if (!baseViewHolder.isLithoViewType) {
          final ViewBinder viewBinder = renderInfo.getViewBinder();
          baseViewHolder.viewBinder = viewBinder;
          viewBinder.bind(baseViewHolder.itemView);
        }
      }

      if (ComponentsConfiguration.isRenderInfoDebuggingEnabled()) {
        RenderInfoDebugInfoRegistry.setRenderInfoToViewMapping(
            holder.itemView,
            renderInfo.getDebugInfo(RenderInfoDebugInfoRegistry.SONAR_SECTIONS_DEBUG_INFO_TAG));
      }
    }

    private @Nullable String getClassNameForDebug(Class c) {
      Class<?> enclosingClass = c.getEnclosingClass();
      if (enclosingClass == null) {
        return c.getCanonicalName();
      }
      return enclosingClass.getCanonicalName();
    }

    @Override
    @GuardedBy("RecyclerBinder.this")
    public int getItemViewType(int position) {
      final RenderInfo renderInfo = getRenderInfoAt(position);
      if (renderInfo.rendersComponent()) {
        // Special value for LithoViews
        return mRenderInfoViewCreatorController.getComponentViewType();
      } else {
        return renderInfo.getViewType();
      }
    }

    @Override
    @GuardedBy("RecyclerBinder.this")
    public int getItemCount() {
      // We can ignore the synchronization here. We'll only add to this from the UiThread.
      // This read only happens on the UiThread as well and we are never writing this here.

      // If the recycler is circular, we have to simulate having an infinite number of items in the
      // adapter by returning Integer.MAX_VALUE.
      int size = mComponentTreeHolders.size();
      return (mIsCircular && size > 0) ? Integer.MAX_VALUE : size;
    }

    @Override
    public void onViewRecycled(RecyclerBinderViewHolder holder) {
      final LithoView lithoView = (LithoView) holder.getLithoView();
      if (lithoView != null) {
        mRecyclerBinderAdapterDelegate.onViewRecycled(holder);
        lithoView.unmountAllItems();
        lithoView.setComponentTree(null);
        lithoView.setInvalidStateLogParamsList(null);
        lithoView.resetMountStartupLoggingInfo();
      } else if (holder instanceof BaseViewHolder) {
        BaseViewHolder baseViewHolder = (BaseViewHolder) holder;
        if (!baseViewHolder.isLithoViewType) {
          if (baseViewHolder.viewBinder != null) {
            baseViewHolder.viewBinder.unbind(baseViewHolder.itemView);
            baseViewHolder.viewBinder = null;
          }
        }
      }
    }

    @Override
    public long getItemId(int position) {
      return mEnableStableIds
          ? mRecyclerBinderAdapterDelegate.getItemId(position)
          : super.getItemId(position);
    }

    @Override
    public int findFirstVisibleItemPosition() {
      return mLayoutInfo.findFirstVisibleItemPosition();
    }

    @Override
    public int findLastVisibleItemPosition() {
      return mLayoutInfo.findLastVisibleItemPosition();
    }

    @Override
    public RenderInfo getRenderInfoAt(int position) {
      return mComponentTreeHolders.get(getNormalizedPosition(position)).getRenderInfo();
    }
  }

  /**
   * If the recycler is circular, returns the position of the {@link ComponentTreeHolder} that is
   * used to render the item at given position. Otherwise, it returns the position passed as
   * parameter, which is the same as the index of the {@link ComponentTreeHolder}.
   */
  @GuardedBy("this")
  private int getNormalizedPosition(int position) {
    return mIsCircular ? position % mComponentTreeHolders.size() : position;
  }

  public static class RecyclerViewLayoutManagerOverrideParams extends RecyclerView.LayoutParams
      implements LithoView.LayoutManagerOverrideParams {
    private final int mWidthMeasureSpec;
    private final int mHeightMeasureSpec;
    private final boolean mIsFullSpan;

    private RecyclerViewLayoutManagerOverrideParams(
        int width,
        int height,
        int overrideWidthMeasureSpec,
        int overrideHeightMeasureSpec,
        boolean isFullSpan) {
      super(width, height);
      mWidthMeasureSpec = overrideWidthMeasureSpec;
      mHeightMeasureSpec = overrideHeightMeasureSpec;
      mIsFullSpan = isFullSpan;
    }

    @Override
    public int getWidthMeasureSpec() {
      return mWidthMeasureSpec;
    }

    @Override
    public int getHeightMeasureSpec() {
      return mHeightMeasureSpec;
    }

    public boolean isFullSpan() {
      return mIsFullSpan;
    }

    @Override
    public boolean hasValidAdapterPosition() {
      final RecyclerView.ViewHolder viewHolder = getViewHolderFromLayoutParam(this);
      // If adapter position is invalid it means that this item is being removed in pre-layout
      // phase of RecyclerView layout when predictive animation is turned on.
      return viewHolder != null && viewHolder.getAdapterPosition() == RecyclerView.NO_POSITION;
    }
  }

  private static @Nullable RecyclerView.ViewHolder getViewHolderFromLayoutParam(
      RecyclerView.LayoutParams layoutParams) {
    try {
      if (mViewHolderField == null) {
        mViewHolderField = RecyclerView.LayoutParams.class.getDeclaredField("mViewHolder");
        mViewHolderField.setAccessible(true);
      }

      final RecyclerView.ViewHolder viewHolder =
          (RecyclerView.ViewHolder) mViewHolderField.get(layoutParams);

      return viewHolder;
    } catch (Exception ignore) {
    }
    return null;
  }

  private ComponentTreeHolder createComponentTreeHolder(RenderInfo renderInfo) {
    if (mComponentWarmer != null) {
      final Object tag = renderInfo.getCustomAttribute(ComponentWarmer.COMPONENT_WARMER_TAG);
      if (tag instanceof String) {
        final ComponentTreeHolder holder = mComponentWarmer.consume((String) tag);
        if (holder != null) {
          if (SectionsDebug.ENABLED) {
            Log.d(SectionsDebug.TAG, "Got ComponentTreeHolder from ComponentWarner for key " + tag);
          }
          final Object preventRelease =
              renderInfo.getCustomAttribute(ComponentTreeHolder.PREVENT_RELEASE_TAG);
          if (preventRelease != null) {
            holder
                .getRenderInfo()
                .addCustomAttribute(ComponentTreeHolder.PREVENT_RELEASE_TAG, preventRelease);
          }
          return holder;
        }
      }
    }

    final RunnableHandler layoutHandler;
    final RunnableHandler resolveHandler;
    if (mLayoutHandlerFactory != null) {
      layoutHandler = mLayoutHandlerFactory.createLayoutCalculationHandler(renderInfo);
      resolveHandler =
          ComponentsConfiguration.useSeparateThreadHandlersForResolveAndLayout
              ? mLayoutHandlerFactory.createLayoutCalculationHandler(renderInfo)
              : null;
    } else if (mLayoutThreadPoolHandler != null) {
      layoutHandler = mLayoutThreadPoolHandler;
      resolveHandler = mResolveThreadPoolHandler;
    } else {
      layoutHandler = null;
      resolveHandler = null;
    }
    return mComponentTreeHolderFactory.create(
        renderInfo,
        resolveHandler,
        layoutHandler,
        mComponentTreeMeasureListenerFactory,
        mComponentsConfiguration,
        mIncrementalMountEnabled,
        mVisibilityProcessingEnabled,
        mIsReconciliationEnabled,
        mIsLayoutDiffingEnabled,
        mPreallocateMountContentHandler,
        mPreallocatePerMountSpec,
        mParentLifecycle,
        mErrorEventHandler);
  }

  ComponentTreeHolderPreparer getComponentTreeHolderPreparer() {
    return new ComponentTreeHolderPreparer() {
      @Override
      public ComponentTreeHolder create(ComponentRenderInfo renderInfo) {
        return createComponentTreeHolder(renderInfo);
      }

      @Override
      public void prepareSync(ComponentTreeHolder holder, @Nullable Size size) {
        final int childrenWidthSpec = getActualChildrenWidthSpec(holder);
        final int childrenHeightSpec = getActualChildrenHeightSpec(holder);

        if (size != null && holder.isTreeValidForSizeSpecs(childrenWidthSpec, childrenHeightSpec)) {
          size.width = SizeSpec.getSize(childrenWidthSpec);
          size.height = SizeSpec.getSize(childrenHeightSpec);

          return;
        }

        holder.computeLayoutSync(mComponentContext, childrenWidthSpec, childrenHeightSpec, size);
      }

      @Override
      public void prepareAsync(ComponentTreeHolder holder) {
        final int childrenWidthSpec = getActualChildrenWidthSpec(holder);
        final int childrenHeightSpec = getActualChildrenHeightSpec(holder);

        if (holder.isTreeValidForSizeSpecs(childrenWidthSpec, childrenHeightSpec)) {
          return;
        }

        holder.computeLayoutAsync(mComponentContext, childrenWidthSpec, childrenHeightSpec);
      }
    };
  }

  void setComponentWarmer(ComponentWarmer componentWarmer) {
    mComponentWarmer = componentWarmer;
  }

  @VisibleForTesting
  @Nullable
  ComponentWarmer getComponentWarmer() {
    return mComponentWarmer;
  }

  @UiThread
  private void updateHolder(ComponentTreeHolder holder, RenderInfo renderInfo) {
    final RenderInfo previousRenderInfo = holder.getRenderInfo();
    holder.setRenderInfo(renderInfo);
    if (mLayoutHandlerFactory != null
        && mLayoutHandlerFactory.shouldUpdateLayoutHandler(previousRenderInfo, renderInfo)) {
      holder.updateLayoutHandler(mLayoutHandlerFactory.createLayoutCalculationHandler(renderInfo));
      holder.updateResolveHandler(mLayoutHandlerFactory.createLayoutCalculationHandler(renderInfo));
    }
  }

  private @Nullable ComponentTreeHolderRangeInfo getHolderForRangeInfo() {
    ComponentTreeHolderRangeInfo holderForRangeInfo = null;

    if (!mComponentTreeHolders.isEmpty()) {
      final int positionToComputeLayout =
          findInitialComponentPosition(mComponentTreeHolders, mTraverseLayoutBackwards);
      if (mCurrentFirstVisiblePosition < mComponentTreeHolders.size()
          && positionToComputeLayout >= 0) {
        holderForRangeInfo =
            new ComponentTreeHolderRangeInfo(positionToComputeLayout, mComponentTreeHolders);
      }
    } else if (!mAsyncComponentTreeHolders.isEmpty()) {
      final int positionToComputeLayout =
          findInitialComponentPosition(mAsyncComponentTreeHolders, mTraverseLayoutBackwards);
      if (positionToComputeLayout >= 0) {
        holderForRangeInfo =
            new ComponentTreeHolderRangeInfo(positionToComputeLayout, mAsyncComponentTreeHolders);
      }
    }

    return holderForRangeInfo;
  }

  /**
   * @return true if the given view is visible to user, false otherwise. The logic is leveraged from
   *     {@link View#isVisibleToUser()}.
   */
  private static boolean isVisibleToUser(View view) {
    if (view.getWindowVisibility() != View.VISIBLE) {
      return false;
    }

    Object current = view;
    while (current instanceof View) {
      final View currentView = (View) current;
      if (currentView.getAlpha() <= 0 || currentView.getVisibility() != View.VISIBLE) {
        return false;
      }
      current = currentView.getParent();
    }

    return view.getGlobalVisibleRect(sDummyRect);
  }

  /** @return a list of view's visibility, iterating from given view to its ancestor views. */
  private static List<String> getVisibleHierarchy(View view) {
    final List<String> hierarchy = new ArrayList<>();
    Object current = view;
    while (current instanceof View) {
      final View currentView = (View) current;
      hierarchy.add(
          "view="
              + currentView.getClass().getSimpleName()
              + ", alpha="
              + currentView.getAlpha()
              + ", visibility="
              + currentView.getVisibility());
      if (currentView.getAlpha() <= 0 || currentView.getVisibility() != View.VISIBLE) {
        break;
      }
      current = currentView.getParent();
    }
    return hierarchy;
  }

  @VisibleForTesting
  static class ComponentTreeHolderRangeInfo {
    private final int mPosition;
    private final List<ComponentTreeHolder> mHolders;

    @VisibleForTesting
    ComponentTreeHolderRangeInfo(int position, List<ComponentTreeHolder> holders) {
      mPosition = position;
      mHolders = holders;
    }
  }

  @VisibleForTesting
  /** Used for finding components to calculate layout during async init range */
  static class ComponentAsyncInitRangeIterator implements Iterator<ComponentTreeHolder> {

    private final boolean mTraverseLayoutBackwards;
    private final List<ComponentTreeHolder> mHolders;

    private int mCurrentPosition;
    private int mNumberOfItemsToProcess;

    ComponentAsyncInitRangeIterator(
        List<ComponentTreeHolder> holders,
        int initialPosition,
        int numberOfItemsToProcess,
        boolean traverseLayoutBackwards) {
      mHolders = new ArrayList<>(holders);
      mCurrentPosition = traverseLayoutBackwards ? initialPosition - 1 : initialPosition + 1;
      mNumberOfItemsToProcess = numberOfItemsToProcess;
      mTraverseLayoutBackwards = traverseLayoutBackwards;
    }

    @Override
    public boolean hasNext() {
      while (mNumberOfItemsToProcess > 0 && isValidPosition(mCurrentPosition)) {
        final ComponentTreeHolder holder = mHolders.get(mCurrentPosition);
        if (holder.getRenderInfo().rendersComponent() && !holder.isTreeValid()) {
          return true;
        } else {
          shiftToNextPosition();
        }
      }
      return false;
    }

    boolean isValidPosition(int position) {
      return position >= 0 && position < mHolders.size();
    }

    @Override
    public synchronized @Nullable ComponentTreeHolder next() {
      if (!hasNext()) {
        return null;
      }

      final ComponentTreeHolder holder = mHolders.get(mCurrentPosition);
      shiftToNextPosition();
      mNumberOfItemsToProcess--;
      return holder;
    }

    private void shiftToNextPosition() {
      if (mTraverseLayoutBackwards) {
        mCurrentPosition--;
      } else {
        mCurrentPosition++;
      }
    }

    @Override
    public void remove() {}
  }
}
