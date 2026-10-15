import _invoke from 'lodash-es/invoke'
import { handleRef, Ref } from '@fluentui/react-component-ref'
import EventStack from '@/lib/event-stack'
import keyboardKey from 'keyboard-key'

import React, { useCallback, useEffect, useRef } from 'react'
import { doesNodeContainClick, useAutoControlledState } from '../../lib'
import PortalInner from './PortalInner'
import validateTrigger from './utils/validateTrigger'

export interface PortalProps
  extends StrictPortalProps,
    Omit<React.HTMLAttributes<HTMLElement>, 'onClose'> {}

export interface StrictPortalProps {
  /** Primary content. */
  children?: React.ReactNode

  /** Controls whether or not the portal should close on a click outside. */
  closeOnDocumentClick?: boolean

  /** Controls whether or not the portal should close when escape is pressed is displayed. */
  closeOnEscape?: boolean

  /**
   * Controls whether or not the portal should close when mousing out of the portal.
   * NOTE: This will prevent `closeOnTriggerMouseLeave` when mousing over the
   * gap from the trigger to the portal.
   */
  closeOnPortalMouseLeave?: boolean

  /** Controls whether or not the portal should close on blur of the trigger. */
  closeOnTriggerBlur?: boolean

  /** Controls whether or not the portal should close on click of the trigger. */
  closeOnTriggerClick?: boolean

  /** Controls whether or not the portal should close when mousing out of the trigger. */
  closeOnTriggerMouseLeave?: boolean

  /** Initial value of open. */
  defaultOpen?: boolean

  /** Event pool namespace that is used to handle component events. */
  eventPool?: string

  /** The node where the portal should mount. */
  mountNode?: Element | DocumentFragment

  /** Milliseconds to wait before opening on mouse over */
  mouseEnterDelay?: number

  /** Milliseconds to wait before closing on mouse leave */
  mouseLeaveDelay?: number

  /**
   * Called when a close event happens
   *
   * @param {SyntheticEvent} event - React's original SyntheticEvent.
   * @param {object} data - All props.
   */
  onClose?: (event: React.MouseEvent<HTMLElement>, data: PortalProps) => void

  /**
   * Called when the portal is mounted on the DOM
   *
   * @param {null}
   * @param {object} data - All props.
   */
  onMount?: (nothing: null, data: PortalProps) => void

  /**
   * Called when an open event happens
   *
   * @param {SyntheticEvent} event - React's original SyntheticEvent.
   * @param {object} data - All props.
   */
  onOpen?: (event: React.MouseEvent<HTMLElement>, data: PortalProps) => void

  /**
   * Called when the portal is unmounted from the DOM
   *
   * @param {null}
   * @param {object} data - All props.
   */
  onUnmount?: (nothing: null, data: PortalProps) => void

  /** Controls whether or not the portal is displayed. */
  open?: boolean

  /** Controls whether or not the portal should open when the trigger is clicked. */
  openOnTriggerClick?: boolean

  /** Controls whether or not the portal should open on focus of the trigger. */
  openOnTriggerFocus?: boolean

  /** Controls whether or not the portal should open when mousing over the trigger. */
  openOnTriggerMouseEnter?: boolean

  /** Element to be rendered in-place where the portal is defined. */
  trigger?: React.ReactNode

  /** Called with a ref to the trigger node. */
  triggerRef?: React.Ref<HTMLElement>
}

interface PortalState {
  open: boolean
}

/**
 * Events that reach the component handlers: the document and portal handlers are
 * subscribed through the event stack and receive native events, while the trigger
 * handlers are called by React with synthetic ones.
 */
type PortalEvent = React.SyntheticEvent<HTMLElement> | MouseEvent | KeyboardEvent

/**
 * A component that allows you to render children outside their parent.
 * @see Modal
 * @see Popup
 * @see Dimmer
 * @see Confirm
 */
function Portal(props: PortalProps): React.ReactElement {
  const contentRef = useRef<HTMLElement | null>(null)
  const triggerRef = useRef<HTMLElement | null>(null)

  const latestDocumentMouseDownEventRef = useRef<MouseEvent | null>(null)
  const mouseEnterTimerRef = useRef<number | undefined>(undefined)
  const mouseLeaveTimerRef = useRef<number | undefined>(undefined)

  const [state, setState] = useAutoControlledState<PortalProps, PortalState>(props, {
    autoControlledProps: ['open'],
  })

  const propsRef = useRef(props)
  const stateRef = useRef(state)
  propsRef.current = props
  stateRef.current = state

  const close = useCallback((e: PortalEvent): void => {
    setState({ open: false })
    _invoke(propsRef.current, 'onClose', e, { ...propsRef.current, open: false })
  }, [setState])

  const openPortal = (e: React.SyntheticEvent<HTMLElement>): void => {
    _invoke(propsRef.current, 'onOpen', e, { ...propsRef.current, open: true })

    setState({ open: true })
  }

  const openWithTimeout = (e: React.SyntheticEvent<HTMLElement>, delay: number | undefined) => {
    const eventClone = { ...e }
    return window.setTimeout(() => openPortal(eventClone), delay || 0)
  }

  const closeWithTimeout = (e: PortalEvent, delay: number | undefined) => {
    const eventClone = { ...e }
    return window.setTimeout(() => close(eventClone), delay || 0)
  }

  const handleDocumentMouseDown = useCallback((e: MouseEvent): void => {
    latestDocumentMouseDownEventRef.current = e
  }, [])

  const handleDocumentClick = useCallback((e: MouseEvent): void => {
    const { closeOnDocumentClick } = propsRef.current
    const currentMouseDownEvent = latestDocumentMouseDownEventRef.current
    latestDocumentMouseDownEventRef.current = null

    if (
      !contentRef.current ||
      doesNodeContainClick(triggerRef.current, e) ||
      (currentMouseDownEvent && doesNodeContainClick(contentRef.current, currentMouseDownEvent)) ||
      doesNodeContainClick(contentRef.current, e)
    ) {
      return
    }

    if (closeOnDocumentClick) close(e)
  }, [close])

  const handleEscape = useCallback((e: KeyboardEvent): void => {
    if (!propsRef.current.closeOnEscape) return
    if (keyboardKey.getCode(e) !== keyboardKey.Escape) return
    close(e)
  }, [close])

  const handlePortalMouseLeave = useCallback((e: MouseEvent): void => {
    const { closeOnPortalMouseLeave, mouseLeaveDelay } = propsRef.current
    if (!closeOnPortalMouseLeave || e.target !== contentRef.current) return
    mouseLeaveTimerRef.current = closeWithTimeout(e, mouseLeaveDelay)
  }, [])

  const handlePortalMouseEnter = useCallback((): void => {
    if (!propsRef.current.closeOnPortalMouseLeave) return
    clearTimeout(mouseLeaveTimerRef.current)
  }, [])

  const open = stateRef.current.open

  // Clean up timers, replaces "componentWillUnmount()"
  useEffect(
    () => (): void => {
      clearTimeout(mouseEnterTimerRef.current)
      clearTimeout(mouseLeaveTimerRef.current)
    },
    [],
  )


  const handleTriggerBlur = (e: React.FocusEvent<HTMLElement>, ...rest: unknown[]): void => {
    const { trigger, closeOnTriggerBlur } = props

    // Call original event handler
    _invoke(trigger, 'props.onBlur', e, ...rest)

    // IE 11 doesn't work with relatedTarget in blur events
    const target = e.relatedTarget || document.activeElement
    // do not close if focus is given to the portal
    const didFocusPortal = _invoke(contentRef.current, 'contains', target)

    if (!closeOnTriggerBlur || didFocusPortal) return

    close(e)
  }

  const handleTriggerClick = (e: React.MouseEvent<HTMLElement>, ...rest: unknown[]): void => {
    const { trigger, closeOnTriggerClick, openOnTriggerClick } = props

    // Call original event handler
    _invoke(trigger, 'props.onClick', e, ...rest)

    if (open && closeOnTriggerClick) {
      close(e)
    } else if (!open && openOnTriggerClick) {
      openPortal(e)
    }
  }

  const handleTriggerFocus = (e: React.FocusEvent<HTMLElement>, ...rest: unknown[]): void => {
    const { trigger, openOnTriggerFocus } = props

    // Call original event handler
    _invoke(trigger, 'props.onFocus', e, ...rest)

    if (!openOnTriggerFocus) return

    openPortal(e)
  }

  const handleTriggerMouseLeave = (e: React.MouseEvent<HTMLElement>, ...rest: unknown[]): void => {
    clearTimeout(mouseEnterTimerRef.current)

    const { trigger, closeOnTriggerMouseLeave, mouseLeaveDelay } = props

    // Call original event handler
    _invoke(trigger, 'props.onMouseLeave', e, ...rest)

    if (!closeOnTriggerMouseLeave) return
    mouseLeaveTimerRef.current = closeWithTimeout(e, mouseLeaveDelay)
  }

  const handleTriggerMouseEnter = (e: React.MouseEvent<HTMLElement>, ...rest: unknown[]): void => {
    clearTimeout(mouseLeaveTimerRef.current)

    const { trigger, mouseEnterDelay, openOnTriggerMouseEnter } = props

    // Call original event handler
    _invoke(trigger, 'props.onMouseEnter', e, ...rest)

    if (!openOnTriggerMouseEnter) return
    mouseEnterTimerRef.current = openWithTimeout(e, mouseEnterDelay)
  }

  const handleMount = (): void => {
    _invoke(props, 'onMount', null, props)
  }

  const handleUnmount = (): void => {
    _invoke(props, 'onUnmount', null, props)
  }

  const handleTriggerRef = useCallback(
    (c: HTMLElement | null): void => {
      triggerRef.current = c
      handleRef(props.triggerRef, c)
    },
    [props.triggerRef],
  )

  const { children, eventPool, mountNode, trigger } = props

  /* istanbul ignore else */
  if (process.env.NODE_ENV !== 'production') {
    validateTrigger(trigger)
  }

  return (
    <>
      {open && (
        <>
          <PortalInner
            innerRef={contentRef}
            mountNode={mountNode}
            onMount={handleMount}
            onUnmount={handleUnmount}
          >
            {children}
          </PortalInner>

          <EventStack
            name="mouseleave"
            on={handlePortalMouseLeave}
            pool={eventPool}
            target={contentRef}
          />
          <EventStack
            name="mouseenter"
            on={handlePortalMouseEnter}
            pool={eventPool}
            target={contentRef}
          />
          <EventStack name="mousedown" on={handleDocumentMouseDown} pool={eventPool} />
          <EventStack name="click" on={handleDocumentClick} pool={eventPool} />
          <EventStack name="keydown" on={handleEscape} pool={eventPool} />
        </>
      )}

      {trigger && (
        // `validateTrigger()` asserts in development that the trigger is a single cloneable element.
        // `Ref` resolves the DOM node of that element, a trigger is often a composite component
        // ("Button", "Icon") whose DOM node a native ref can not reach, while `Portal` uses the
        // node for document click containment and forwards it as `triggerRef`.
        <Ref innerRef={handleTriggerRef}>
          {React.cloneElement(trigger as React.ReactElement, {
            onBlur: handleTriggerBlur,
            onClick: handleTriggerClick,
            onFocus: handleTriggerFocus,
            onMouseLeave: handleTriggerMouseLeave,
            onMouseEnter: handleTriggerMouseEnter,
          })}
        </Ref>
      )}
    </>
  )
}

Portal.Inner = PortalInner

Portal.handledProps = [
  'children',
  'closeOnDocumentClick',
  'closeOnEscape',
  'closeOnPortalMouseLeave',
  'closeOnTriggerBlur',
  'closeOnTriggerClick',
  'closeOnTriggerMouseLeave',
  'defaultOpen',
  'eventPool',
  'mountNode',
  'mouseEnterDelay',
  'mouseLeaveDelay',
  'onClose',
  'onMount',
  'onOpen',
  'onUnmount',
  'open',
  'openOnTriggerClick',
  'openOnTriggerFocus',
  'openOnTriggerMouseEnter',
  'trigger',
  'triggerRef',
]



Portal.defaultProps = {
  closeOnDocumentClick: true,
  closeOnEscape: true,
  eventPool: 'default',
  openOnTriggerClick: true,
} satisfies Partial<PortalProps>

export default Portal
