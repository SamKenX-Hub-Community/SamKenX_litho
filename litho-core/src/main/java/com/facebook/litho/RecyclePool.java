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

import androidx.annotation.Nullable;
import androidx.core.util.Pools;
import com.facebook.infer.annotation.Nullsafe;
import com.facebook.infer.annotation.OkToExtend;
import com.facebook.infer.annotation.ThreadSafe;

/**
 * Used to recycle objects in Litho. Can be configured to be either syncronized or not. A {@link
 * RecyclePool} will keep track of its own size so that it can be queried to debug pool sizes.
 */
@Nullsafe(Nullsafe.Mode.LOCAL)
@ThreadSafe(enableChecks = false)
@OkToExtend
public class RecyclePool<T> implements PoolWithDebugInfo {
  private final String mName;
  private final int mMaxSize;
  private final boolean mIsSync;
  private Pools.Pool<T> mPool;
  private int mCurrentSize = 0;

  public RecyclePool(String name, int maxSize, boolean sync) {
    mIsSync = sync;
    mName = name;
    mMaxSize = maxSize;
    mPool = sync ? new Pools.SynchronizedPool<T>(maxSize) : new Pools.SimplePool<T>(maxSize);
  }

  @Nullable
  public T acquire() {
    T item;
    if (mIsSync) {
      synchronized (this) {
        item = mPool.acquire();
        mCurrentSize = Math.max(0, mCurrentSize - 1);
      }
    } else {
      item = mPool.acquire();
      mCurrentSize = Math.max(0, mCurrentSize - 1);
    }
    return item;
  }

  public boolean release(T item) {
    if (mIsSync) {
      synchronized (this) {
        mCurrentSize = Math.min(mMaxSize, mCurrentSize + 1);
        return mPool.release(item);
      }
    } else {
      mCurrentSize = Math.min(mMaxSize, mCurrentSize + 1);
      return mPool.release(item);
    }
  }

  @Override
  public String getName() {
    return mName;
  }

  @Override
  public int getMaxSize() {
    return mMaxSize;
  }

  @Override
  public int getCurrentSize() {
    return mCurrentSize;
  }

  public boolean isFull() {
    return mCurrentSize >= mMaxSize;
  }

  public void clear() {
    if (mIsSync) {
      synchronized (this) {
        while (acquire() != null) {
          // no-op.
        }
      }
    } else {
      while (acquire() != null) {
        // no-op.
      }
    }
  }
}
