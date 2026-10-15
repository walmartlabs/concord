import _invoke from 'lodash-es/invoke'
import { Ref } from '@fluentui/react-component-ref'

import React, { useEffect, useRef } from 'react'
import { createPortal } from 'react-dom'
import { isBrowser } from '../../lib'

export interface PortalInnerProps extends StrictPortalInnerProps {}

export interface StrictPortalInnerProps {
  /** Primary content. */
  children: React.ReactNode

  /** Called with a ref to the inner node. */
  innerRef?: React.Ref<HTMLElement>

  /** The node where the portal should mount. */
  mountNode?: Element | DocumentFragment

  /**
   * Called when the PortalInner is mounted on the DOM.
   *
   * @param {null}
   * @param {object} data - All props.
   */
  onMount?: (nothing: null, data: PortalInnerProps) => void

  /**
   * Called when the PortalInner is unmounted from the DOM.
   *
   * @param {null}
   * @param {object} data - All props.
   */
  onUnmount?: (nothing: null, data: PortalInnerProps) => void
}

/**
 * An inner component that allows you to render children outside their parent.
 */
function PortalInner(props: PortalInnerProps): React.ReactPortal | null {
  // `onMount`/`onUnmount` fire from an effect that runs only once, the ref keeps the
  // latest props so that "componentDidMount()"/"componentWillUnmount()" semantics hold.
  const propsRef = useRef(props)
  propsRef.current = props

  useEffect(() => {
    _invoke(propsRef.current, 'onMount', null, propsRef.current)

    return (): void => {
      _invoke(propsRef.current, 'onUnmount', null, propsRef.current)
    }
  }, [])

  if (!isBrowser()) return null

  const { children, innerRef, mountNode = document.body } = props

  // `Ref` resolves the DOM node of its single child, portal children are often composite
  // components ("ModalDimmer", "Popper", "DimmerInner") that a native ref can not reach,
  // while `Portal` relies on that DOM node for its document and mouse events.
  return createPortal(
    <Ref innerRef={innerRef}>{children as React.ReactElement}</Ref>,
    mountNode,
  )
}

PortalInner.handledProps = ['children', 'innerRef', 'mountNode', 'onMount', 'onUnmount']



export default PortalInner
