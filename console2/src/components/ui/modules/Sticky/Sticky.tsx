import _invoke from 'lodash-es/invoke'
import { isRefObject } from '@fluentui/react-component-ref'
import cx from 'clsx'

import React, { useEffect, useRef, useState } from 'react'
import {
  eventStack,
  getElementType,
  getUnhandledProps,
  isBrowser,
} from '../../lib'

export interface StickyProps extends StrictStickyProps, React.HTMLAttributes<HTMLDivElement> {}

export interface StrictStickyProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string

  /** Context which sticky element should stick to. */
  context?: Document | Window | HTMLElement | React.RefObject<HTMLElement>

  /**
   * Callback when element is fixed to page.
   *
   * @param {SyntheticEvent} event - React's original SyntheticEvent.
   * @param {object} data - All props.
   */
  onStick?: (event: React.MouseEvent<HTMLElement>, data: StickyProps) => void

  /**
   * Callback when element is unfixed from page.
   *
   * @param {SyntheticEvent} event - React's original SyntheticEvent.
   * @param {object} data - All props.
   */
  onUnstick?: (event: React.MouseEvent<HTMLElement>, data: StickyProps) => void
}

interface StickyState {
  bottom?: number | null
  bound?: boolean
  sticky: boolean
  top?: number | null
}

/**
 * Sticky content stays fixed to the browser viewport while another column of content is visible on the page.
 */
function Sticky(props: StickyProps): React.ReactElement {
  const { children, className, context } = props

  const stickyRef = useRef<HTMLDivElement>(null)
  const triggerRef = useRef<HTMLDivElement>(null)

  const [state, setState] = useState<StickyState>({ sticky: false })

  const tickingRef = useRef(false)
  /** Identifier of the pending animation frame, as returned by `requestAnimationFrame`. */
  const frameIdRef = useRef(0)

  const triggerRectRef = useRef<DOMRect | null>(null)
  const contextRectRef = useRef<DOMRect | null>(null)
  const stickyRectRef = useRef<DOMRect | null>(null)

  /**
   * The listeners are registered on `window` once on mount, but `update()` always reads the
   * current props (similarly to `this.props` in the class implementation), so the registered
   * handler has to call the implementation of the latest render.
   */
  const updateRef = useRef<(e?: Event) => void>(() => {})

  const setStickyState = (partial: Partial<StickyState>): void => {
    setState((prevState) => ({ ...prevState, ...partial }))
  }

  // ----------------------------------------
  // Events
  // ----------------------------------------

  const handleUpdate = (e?: Event): void => {
    if (!tickingRef.current) {
      tickingRef.current = true
      frameIdRef.current = requestAnimationFrame(() => updateRef.current(e))
    }
  }

  const assignRects = (): void => {
    const contextNode = isRefObject(context) ? context.current : context || document.body

    triggerRectRef.current = triggerRef.current.getBoundingClientRect()
    // The public contract also allows a `Document`/`Window` context, but the
    // runtime measures it as an element, exactly like the original implementation.
    contextRectRef.current = (contextNode as HTMLElement).getBoundingClientRect()
    stickyRectRef.current = stickyRef.current.getBoundingClientRect()
  }

  const didReachContextBottom = (): boolean =>
    stickyRectRef.current.height >= contextRectRef.current.bottom

  const didTouchScreenTop = (): boolean => triggerRectRef.current.top < 0

  const isOversized = (): boolean => stickyRectRef.current.height > window.innerHeight

  const stick = (e: Event | undefined, bound: boolean): void => {
    setStickyState({ bound, sticky: true })
    _invoke(props, 'onStick', e, props)
  }

  const unstick = (e: Event | undefined, bound: boolean): void => {
    setStickyState({ bound, sticky: false })
    _invoke(props, 'onUnstick', e, props)
  }

  const stickToContextBottom = (e?: Event): void => {
    stick(e, true)
  }

  const stickToContextTop = (e?: Event): void => {
    unstick(e, false)
  }

  const stickToScreenTop = (e?: Event): void => {
    stick(e, false)
    setStickyState({ top: 0, bottom: null })
  }

  const update = (e?: Event): void => {
    tickingRef.current = false
    assignRects()

    if (isOversized()) {
      if (contextRectRef.current.top > 0) return stickToContextTop(e)
      if (contextRectRef.current.bottom < window.innerHeight) return stickToContextBottom(e)
    }

    if (didTouchScreenTop()) {
      if (didReachContextBottom()) return stickToContextBottom(e)
      return stickToScreenTop(e)
    }

    return stickToContextTop(e)
  }

  updateRef.current = update

  const addListeners = (scrollContext: StrictStickyProps['context']): void => {
    const scrollContextNode = isRefObject(scrollContext) ? scrollContext.current : scrollContext

    if (scrollContextNode) {
      eventStack.sub('resize', handleUpdate, { target: scrollContextNode })
      eventStack.sub('scroll', handleUpdate, { target: scrollContextNode })
    }
  }

  const removeListeners = (scrollContext: StrictStickyProps['context']): void => {
    const scrollContextNode = isRefObject(scrollContext) ? scrollContext.current : scrollContext

    if (scrollContextNode) {
      eventStack.unsub('resize', handleUpdate, { target: scrollContextNode })
      eventStack.unsub('scroll', handleUpdate, { target: scrollContextNode })
    }
  }

  useEffect(() => {
    if (!isBrowser()) return undefined

    handleUpdate()
    addListeners(window)

    return () => {
      if (!isBrowser()) return

      removeListeners(window)
      cancelAnimationFrame(frameIdRef.current)
      // Re-arm the frame gate so a remount (e.g. StrictMode effect replay) can
      // schedule a new animation frame instead of skipping on a stale flag.
      tickingRef.current = false
    }
  }, [])

  // ----------------------------------------
  // Render
  // ----------------------------------------

  const computeStyle = (): React.CSSProperties | undefined => {
    const { bottom, bound, sticky, top } = state

    if (!sticky) return

    return {
      bottom: bound ? 0 : bottom,
      top: bound ? undefined : top,
      width: triggerRectRef.current.width,
    }
  }

  const { bottom, bound, sticky } = state

  const rest = getUnhandledProps(Sticky, props)
  const ElementType = getElementType(Sticky, props)
  const containerClasses = cx(
    sticky && 'ui',
    sticky && 'stuck-container',
    sticky && (bound ? 'bound-container' : 'fixed-container'),
    className,
  )
  const elementClasses = cx(
    'ui',
    sticky && (bound ? 'bound bottom' : 'fixed'),
    sticky && !bound && (bottom === null ? 'top' : 'bottom'),
    'sticky',
  )
  const triggerStyles =
    sticky && stickyRectRef.current ? { height: stickyRectRef.current.height } : {}

  return React.createElement(
    ElementType,
    { ...rest, className: containerClasses },
    React.createElement('div', { ref: triggerRef, style: triggerStyles }),
    React.createElement(
      'div',
      { className: elementClasses, ref: stickyRef, style: computeStyle() },
      children,
    ),
  )
}

Sticky.handledProps = ['as', 'children', 'className', 'context', 'onStick', 'onUnstick']



export default Sticky
