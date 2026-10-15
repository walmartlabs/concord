import _memoize from 'lodash-es/memoize'
import _invoke from 'lodash-es/invoke'
import { isRefObject } from '@fluentui/react-component-ref'
import React from 'react'

/**
 * The reference the popup is bound to: an existing document, window or element,
 * or a ref object resolving to one of them.
 */
type ReferenceProxyInput = Document | Window | HTMLElement | React.RefObject<HTMLElement>

class ReferenceProxy {
  readonly ref: { current: ReferenceProxyInput | null | undefined }

  constructor(refObject: { current: ReferenceProxyInput | null | undefined }) {
    this.ref = refObject
  }

  getBoundingClientRect(): DOMRect | Partial<DOMRect> {
    // `_invoke()` yields `undefined` while the ref is not attached yet, the proxy
    // then intentionally reports an empty rect instead of measured geometry.
    return _invoke(this.ref.current, 'getBoundingClientRect') || {}
  }

  get clientWidth(): number | undefined {
    return this.getBoundingClientRect().width
  }

  get clientHeight(): number | undefined {
    return this.getBoundingClientRect().height
  }

  get parentNode(): ParentNode | null | undefined {
    const { current } = this.ref
    if (!current) return undefined

    // `Window` does not expose `parentNode`
    return (current as unknown as ParentNode).parentNode
  }
}

/**
 * Popper.js does not support ref objects from `createRef()` as referenceElement. If we will pass
 * directly `ref`, `ref.current` will be `null` at the render process. We use memoize to keep the
 * same reference between renders.
 *
 * @see https://popper.js.org/popper-documentation.html#referenceObject
 */
const createReferenceProxy = _memoize(
  (reference: ReferenceProxyInput): ReferenceProxy =>
    new ReferenceProxy(isRefObject(reference) ? reference : { current: reference }),
)

export default createReferenceProxy
