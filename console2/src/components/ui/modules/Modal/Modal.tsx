import _pick from 'lodash-es/pick'
import _includes from 'lodash-es/includes'
import _reduce from 'lodash-es/reduce'
import _isEmpty from 'lodash-es/isEmpty'
import _invoke from 'lodash-es/invoke'
import { Ref } from '@fluentui/react-component-ref'
import cx from 'clsx'

import React, { isValidElement, useCallback, useEffect, useRef } from 'react'
import {
  doesNodeContainClick,
  eventStack,
  getElementType,
  getUnhandledProps,
  isBrowser,
  useAutoControlledState,
  useKeyOnly,
} from '../../lib'
import Portal, { StrictPortalProps } from '../../addons/Portal'
import ModalActions from './ModalActions'
import ModalContent from './ModalContent'
import ModalDimmer from './ModalDimmer'
import ModalHeader from './ModalHeader'
import { canFit } from './utils'

export interface ModalProps
  extends StrictModalProps,
    Omit<React.HTMLAttributes<HTMLDivElement>, 'onClose'> {}

export interface StrictModalProps
  extends Omit<
    StrictPortalProps,
    'closeOnDocumentClick' | 'defaultOpen' | 'eventPool' | 'mountNode'
  > {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** A Modal can reduce its complexity */
  basic?: boolean

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string

  /** A modal can appear in a dimmer. */
  dimmer?: boolean | 'inverted'

  /**
   * Called when a close event happens.
   *
   * @param {SyntheticEvent} event - React's original SyntheticEvent.
   * @param {object} data - All props.
   */
  onClose?: (event: React.MouseEvent<HTMLElement>, data: ModalProps) => void

  /**
   * Called when the portal is mounted on the DOM.
   *
   * @param {null}
   * @param {object} data - All props.
   */
  onMount?: (nothing: null, data: ModalProps) => void

  /**
   * Called when an open event happens.
   *
   * @param {SyntheticEvent} event - React's original SyntheticEvent.
   * @param {object} data - All props.
   */
  onOpen?: (event: React.MouseEvent<HTMLElement>, data: ModalProps) => void

  /**
   * Called when the portal is unmounted from the DOM.
   *
   * @param {null}
   * @param {object} data - All props.
   */
  onUnmount?: (nothing: null, data: ModalProps) => void

  /** Controls whether or not the Modal is displayed. */
  open?: boolean

  /** A modal can vary in size. */
  size?: 'mini' | 'tiny' | 'small' | 'large' | 'fullscreen'

  /** Custom styles. */
  style?: React.CSSProperties

  /** Element to be rendered in-place where the portal is defined. */
  trigger?: React.ReactNode
}

interface ModalState {
  open?: boolean
  scrolling?: boolean
}

/**
 * Portal's runtime statics are not declared in its published `.d.ts`: this is the single
 * typed boundary where the handled props list is read.
 */
const PortalStatics = Portal as unknown as { handledProps: string[] }

/**
 * A modal displays content that temporarily blocks interactions with the main view of a site.
 * @see Confirm
 * @see Portal
 */
function Modal(props: ModalProps): React.ReactElement | null {
  const ref = useRef<HTMLElement>(null)
  const dimmerRef = useRef<HTMLElement>(null)
  const subscribedDimmerRef = useRef<HTMLElement | null>(null)
  const latestDocumentMouseDownEvent = useRef<MouseEvent | null>(null)
  const animationRequestId = useRef<number | null>(null)

  const [state, setState] = useAutoControlledState<ModalProps, ModalState>(props, {
    autoControlledProps: ['open'],
  })

  const { open, scrolling } = state

  /**
   * The document handlers are subscribed once and the animation frame loop reschedules itself,
   * so they have to read the props and the state of the latest render, exactly like the
   * "this.props" / "this.state" reads of the class implementation.
   */
  const propsRef = useRef(props)
  const stateRef = useRef(state)

  propsRef.current = props
  stateRef.current = state

  const setPositionAndClassNames = (): void => {
    let scrolling: boolean
    const newState: Partial<ModalState> = {}

    if (ref.current) {
      const rect = ref.current.getBoundingClientRect()
      const isFitted = canFit(rect)
      scrolling = !isFitted

      if (stateRef.current.scrolling !== scrolling) {
        newState.scrolling = scrolling
      }
    }

    if (!_isEmpty(newState)) setState(newState)

    animationRequestId.current = requestAnimationFrame(setPositionAndClassNames)
  }

  const handleClose = (e: React.MouseEvent<HTMLElement>): void => {
    setState({ open: false })
    _invoke(props, 'onClose', e, { ...props, open: false })
  }

  // Heads Up! "eventStack" removes handlers by reference, so the callbacks subscribed to the
  // "Modal" pool have to keep a stable identity, exactly like the class fields they replace.
  const handleDocumentMouseDown = useCallback((e: MouseEvent): void => {
    latestDocumentMouseDownEvent.current = e
  }, [])

  const handleDocumentClick = useCallback(
    (e: MouseEvent): void => {
      const currentDocumentMouseDownEvent = latestDocumentMouseDownEvent.current
      latestDocumentMouseDownEvent.current = null

      if (
        doesNodeContainClick(ref.current, currentDocumentMouseDownEvent) ||
        doesNodeContainClick(ref.current, e)
      ) {
        return
      }

      setState({ open: false })
      _invoke(propsRef.current, 'onClose', e, { ...propsRef.current, open: false })
    },
    [setState],
  )

  const handleOpen = (e: React.MouseEvent<HTMLElement>): void => {
    _invoke(props, 'onOpen', e, { ...props, open: true })
    setState({ open: true })
  }

  const handlePortalMount = (e: null): void => {
    const eventPool = 'Modal'
    const target = dimmerRef.current

    setState({ scrolling: false })
    setPositionAndClassNames()

    if (target) {
      subscribedDimmerRef.current = target
      eventStack.sub('mousedown', handleDocumentMouseDown, { pool: eventPool, target })
      eventStack.sub('click', handleDocumentClick, { pool: eventPool, target })
    }

    _invoke(propsRef.current, 'onMount', e, propsRef.current)
  }

  const unsubscribePortalEvents = (): void => {
    const target = subscribedDimmerRef.current
    if (!target) return

    subscribedDimmerRef.current = null
    const eventPool = 'Modal'
    eventStack.unsub('mousedown', handleDocumentMouseDown, { pool: eventPool, target })
    eventStack.unsub('click', handleDocumentClick, { pool: eventPool, target })
  }

  const handlePortalUnmount = (e?: null): void => {
    cancelAnimationFrame(animationRequestId.current)
    unsubscribePortalEvents()
    _invoke(propsRef.current, 'onUnmount', e, propsRef.current)
  }

  // Replaces "componentWillUnmount()".
  useEffect(() => {
    return () => {
      cancelAnimationFrame(animationRequestId.current)
      unsubscribePortalEvents()
    }
  }, [])

  const renderContent = (rest: Record<string, unknown>): React.ReactElement => {
    const { basic, children, className, size, style } = props

    const classes = cx(
      'ui',
      size,
      useKeyOnly(basic, 'basic'),
      useKeyOnly(scrolling, 'scrolling'),
      'modal transition visible active',
      className,
    )
    const ElementType = getElementType(Modal, props)

    return React.createElement(Ref, {
      innerRef: ref,
      children: React.createElement(ElementType, { ...rest, className: classes, style }, children),
    })
  }


  const { dimmer, trigger } = props
  const mountNode = isBrowser() ? document.body : null

  // Short circuit when server side rendering
  if (!isBrowser()) {
    return isValidElement(trigger) ? trigger : null
  }

  const portalPropNames = PortalStatics.handledProps
  const unhandled = getUnhandledProps(Modal, props)

  const rest = _reduce<Partial<ModalProps>, Record<string, unknown>>(
    unhandled,
    (acc, val, key) => {
      if (!_includes(portalPropNames, key)) acc[key] = val
      return acc
    },
    {},
  )

  const portalProps = _pick(unhandled, portalPropNames)

  return React.createElement(
    Portal,
    {
      ...portalProps,
      closeOnDocumentClick: false,
      trigger,
      eventPool: 'Modal',
      mountNode,
      open,
      onClose: handleClose,
      onMount: handlePortalMount,
      onOpen: handleOpen,
      onUnmount: handlePortalUnmount,
    },
    // Heads Up! "ModalDimmer" is a function component, its DOM node is resolved by "Ref" and it
    // is the target of the document events subscribed above.
    dimmer
      ? React.createElement(Ref, {
          innerRef: dimmerRef,
          children: React.createElement(ModalDimmer, {
            children: renderContent(rest),
            inverted: dimmer === 'inverted',
            mountNode,
            scrolling,
          }),
        })
      : renderContent(rest),
  )
}

Modal.handledProps = [
  'as',
  'basic',
  'children',
  'className',
  'dimmer',
  'onClose',
  'onMount',
  'onOpen',
  'onUnmount',
  'open',
  'size',
  'style',
  'trigger',
]



Modal.defaultProps = { dimmer: true } satisfies Partial<ModalProps>

Modal.Actions = ModalActions
Modal.Content = ModalContent
Modal.Header = ModalHeader

export default Modal
