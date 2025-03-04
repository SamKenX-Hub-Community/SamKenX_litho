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

package com.facebook.litho

import com.facebook.rendercore.ContentAllocator
import com.facebook.rendercore.primitives.MountBehavior as PrimitiveMountBehavior
import com.facebook.rendercore.primitives.MountConfigurationScope
import com.facebook.rendercore.primitives.Primitive
import kotlin.reflect.KFunction2
import kotlin.reflect.KMutableProperty1

/** The implicit receiver for [PrimitiveComponent.render] call. */
class PrimitiveComponentScope
internal constructor(context: ComponentContext, resolveStateContext: ResolveStateContext) :
    ComponentScope(context, resolveStateContext) {

  /**
   * Indicates whether the component skips Incremental Mount. If this is true then the Component
   * will not be involved in Incremental Mount.
   */
  var shouldExcludeFromIncrementalMount: Boolean = false

  /**
   * Generates an unique id and creates a [com.facebook.rendercore.primitives.MountBehavior] with
   * it.
   */
  @Suppress("FunctionName", "NOTHING_TO_INLINE")
  inline fun <ContentType : Any> MountBehavior(
      contentAllocator: ContentAllocator<ContentType>,
      noinline mountConfigurationCall: MountConfigurationScope<ContentType>.() -> Unit
  ): PrimitiveMountBehavior<ContentType> {
    return PrimitiveMountBehavior(id = createId(), contentAllocator, mountConfigurationCall)
  }

  /**
   * Creates a binding between the dynamic value, and the content’s property.
   *
   * @param defaultValue value that will be set to the Content after unbind
   * @param bindCall function or function reference that will set the dynamic value on the content
   */
  fun <ContentType : Any, T> MountConfigurationScope<ContentType>.bindDynamic(
      dynamicValue: DynamicValue<T>,
      bindCall: BindDynamicScope.(ContentType, T) -> UnbindDynamicFunc
  ) {
    val bindDynamicScope = BindDynamicScope()
    addBinder(
        dynamicValue,
        { content, value -> bindDynamicScope.bindCall(content, value) },
        { bindDynamicScope.unbindDynamicFunc?.onUnbindDynamic() })
  }

  /**
   * Creates a binding between the dynamic value, and the content’s property.
   *
   * @param defaultValue value that will be set to the Content after unbind
   * @param applier function reference that will set the dynamic value on the content
   */
  fun <ContentType : Any, T> MountConfigurationScope<ContentType>.bindDynamic(
      dynamicValue: DynamicValue<T>,
      applier: KFunction2<ContentType, T, Any?>,
      default: T
  ) {
    addBinder(
        dynamicValue,
        { content, value -> applier(content, value) },
        { content -> applier(content, default) })
  }

  /**
   * Creates a binding between the dynamic value, and the content’s property.
   *
   * @param defaultValue value that will be set to the Content after unbind
   * @param applier property reference that will set the dynamic value on the content
   */
  fun <ContentType : Any, T> MountConfigurationScope<ContentType>.bindDynamic(
      dynamicValue: DynamicValue<T>,
      applier: KMutableProperty1<ContentType, T>,
      default: T
  ) {
    addBinder(
        dynamicValue,
        { content, value -> applier.set(content, value) },
        { content -> applier.set(content, default) })
  }

  /**
   * Creates a binding between the dynamic value, and the content’s property. The default value of
   * the property is assumed to be null, so after unbind, null value will be set to the Content.
   *
   * @param applier function reference that will set the dynamic value on the content
   */
  inline fun <ContentType : Any, T> MountConfigurationScope<ContentType>.bindDynamic(
      dynamicValue: DynamicValue<T?>,
      applier: KFunction2<ContentType, T?, Any?>,
  ) = bindDynamic(dynamicValue, applier, null)

  /**
   * Creates a binding between the dynamic value, and the content’s property. The default value of
   * the property is assumed to be null, so after unbind, null value will be set to the Content.
   *
   * @param applier property reference that will set the dynamic value on the content
   */
  inline fun <ContentType : Any, T> MountConfigurationScope<ContentType>.bindDynamic(
      dynamicValue: DynamicValue<T?>,
      applier: KMutableProperty1<ContentType, T?>,
  ) = bindDynamic(dynamicValue, applier, null)

  /**
   * Adds a binder for a [DynamicValue] using [MountConfigurationScope.bind] and passing Any() as
   * deps in order to make sure that the binder will always update. This is equivalent to returning
   * true from shouldUpdate().
   */
  private inline fun <ContentType : Any, T> MountConfigurationScope<ContentType>.addBinder(
      dynamicValue: DynamicValue<T>,
      crossinline bindCall: (ContentType, T) -> Unit,
      crossinline unbindCall: (ContentType) -> Unit
  ) {
    var listener: DynamicValue.OnValueChangeListener<T>? = null

    bind(Any()) { content ->
      if (listener == null) {
        listener =
            DynamicValue.OnValueChangeListener<T> {
              ThreadUtils.assertMainThread()
              bindCall(content, dynamicValue.get())
            }
      }
      dynamicValue.attachListener(listener!!)
      bindCall(content, dynamicValue.get())
      onUnbind {
        unbindCall(content)
        listener?.let { dynamicValue.detach(it) }
        listener = null
      }
    }
  }

  /** Creates an unique ID for a given component. */
  @PublishedApi
  internal fun createId(): Long {
    // TODO(zielinskim): calculateLayoutOutputId is mutated during resolve/layout and it may race.
    // Ideally, we'd like to replace this hacky solution with something else.
    return context.renderUnitIdGenerator?.calculateLayoutOutputId(
        context.globalKey, OutputUnitType.CONTENT)
        ?: throw IllegalStateException("Attempt to use a released RenderStateContext")
  }
}

/**
 * Interface for the [onUnbindDynamic] function: use [onUnbindDynamic] to define the cleanup
 * function for your dynamic values.
 */
fun interface UnbindDynamicFunc {
  fun onUnbindDynamic()
}

class BindDynamicScope {

  // Cache the [UnbindDynamicFunc] to avoid creating a new instance on each [DynamicValue] update
  internal var unbindDynamicFunc: UnbindDynamicFunc? = null

  /**
   * Defines an unbind function to be invoked when the content needs to be updated or a [Primitive]
   * is detached.
   */
  fun onUnbindDynamic(unbindDynamic: () -> Unit): UnbindDynamicFunc {
    val unbindDynamicFunc = this.unbindDynamicFunc ?: UnbindDynamicFunc { unbindDynamic() }
    if (this.unbindDynamicFunc == null) {
      this.unbindDynamicFunc = unbindDynamicFunc
    }
    return unbindDynamicFunc
  }
}
