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

package com.facebook.rendercore;

/** Config class to enable or disable specific features. */
public class RenderCoreConfig {

  /**
   * Defaults to the presence of an
   *
   * <pre>IS_TESTING</pre>
   *
   * system property at startup but can be overridden at runtime.
   */
  public static boolean isEndToEndTestRun = System.getProperty("IS_TESTING") != null;

  /**
   * Enabling this config will log mounting errors instead of throwing exceptions. In addition it
   * will safely clean Hosts so they can be released back to the pool.
   */
  public static boolean shouldIgnoreMountingErrors = false;

  /**
   * Enabling this config will set the root host view in layout before unmounting all items from it
   * so that layouts are not request when removing content from the hierarchy.
   */
  public static boolean shouldSetInLayoutDuringUnmountAll = false;
}
