import _includes from 'lodash-es/includes'
import _invoke from 'lodash-es/invoke'
import _isArray from 'lodash-es/isArray'
import _pick from 'lodash-es/pick'
import _reduce from 'lodash-es/reduce'
import EventStack from '@/lib/event-stack'
import { VirtualElement } from '@popperjs/core'
import cx from 'clsx'

import React, { useCallback, useEffect, useRef, useState } from 'react'
import { Modifier, Popper, PopperChildrenProps } from 'react-popper'
import { SemanticShorthandItem } from '../../generic'
import {
  childrenUtils,
  createHTMLDivision,
  eventStack,
  getElementType,
  getUnhandledProps,
  useKeyOnly,
  useKeyOrValueAndKey,
} from '../../lib'
import Portal, { PortalProps, StrictPortalProps } from '../../addons/Portal'
import createReferenceProxy from './lib/createReferenceProxy'
import { placementMapping, positions, positionsMapping } from './lib/positions'
import PopupContent, { PopupContentProps } from './PopupContent'
import PopupHeader, { PopupHeaderProps } from './PopupHeader'

export interface PopupProps
  extends StrictPopupProps,
    Omit<React.HTMLAttributes<HTMLDivElement>, 'onClose' | 'content'> {}

export interface StrictPopupProps extends StrictPortalProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string

  /** Simple text content for the popover. */
  content?: SemanticShorthandItem<PopupContentProps>

  /** Existing element the pop-up should be bound to. */
  context?: Document | Window | HTMLElement | React.RefObject<HTMLElement>

  /** A flowing Popup has no maximum width and continues to flow to fit its content. */
  flowing?: boolean

  /** Header displayed above the content in bold. */
  header?: SemanticShorthandItem<PopupHeaderProps>

  /** Hide the Popup when scrolling the window. */
  hideOnScroll?: boolean

  /** Whether the popup should not close on hover. */
  hoverable?: boolean

  /** Invert the colors of the popup */
  inverted?: boolean

  /** Legacy attribute forwarded unchanged by the pinned upstream implementation. */
  keepInViewPort?: boolean

  /** Events triggering the popup. */
  on?: 'hover' | 'click' | 'focus' | ('hover' | 'click' | 'focus')[]

  /**
   * Called when a close event happens.
   *
   * @param {SyntheticEvent} event - React's original SyntheticEvent.
   * @param {object} data - All props.
   */
  onClose?: (event: React.MouseEvent<HTMLElement>, data: PopupProps) => void

  /**
   * Called when the portal is mounted on the DOM.
   *
   * @param {null}
   * @param {object} data - All props.
   */
  onMount?: (nothing: null, data: PopupProps) => void

  /**
   * Called when an open event happens.
   *
   * @param {SyntheticEvent} event - React's original SyntheticEvent.
   * @param {object} data - All props.
   */
  onOpen?: (event: React.MouseEvent<HTMLElement>, data: PopupProps) => void

  /**
   * Called when the portal is unmounted from the DOM.
   *
   * @param {null}
   * @param {object} data - All props.
   */
  onUnmount?: (nothing: null, data: PopupProps) => void

  /** Position for the popover. */
  position?:
    | 'top left'
    | 'top right'
    | 'bottom right'
    | 'bottom left'
    | 'right center'
    | 'left center'
    | 'top center'
    | 'bottom center'

  /** Popup size. */
  size?: 'mini' | 'tiny' | 'small' | 'large' | 'huge'

  /** Custom Popup style. */
  style?: React.CSSProperties

  /** Element to be rendered in-place where the popup is defined. */
  trigger?: React.ReactNode

  /** Popup width. */
  wide?: boolean | 'very'
}

/** The subset of `Popup` props that the wrapping `Portal` handles itself. */
type PopupPortalProps = Partial<PortalProps>

interface PopupState {
  /** Set while the popup is hidden on scroll. */
  closed?: boolean

  /** Unhandled props that are rendered on the popup content element. */
  contentRestProps?: Record<string, unknown>

  /** Unhandled props that are forwarded to the wrapping `Portal`. */
  portalRestProps?: PopupPortalProps
}

/**
 * The modifiers applied to the popper instance created by `react-popper`.
 */
type PopupModifier = Modifier<
  'arrow' | 'eventListeners' | 'flip' | 'preventOverflow' | 'syncZIndex'
>

/**
 * A Popup displays additional information on top of a page.
 */
function Popup(props: PopupProps): React.ReactElement {
  const triggerRef = useRef<HTMLElement>(null)
  const timeoutId = useRef<number>()
  const zIndexWasSyncedRef = useRef(false)

  // Heads up! The handlers below are stable between renders like the instance methods they replace
  // and read the props of the latest render through this ref.
  const propsRef = useRef(props)
  propsRef.current = props

  const [state, setState] = useState<PopupState>({})
  const { closed } = state

  const handleClose = useCallback((e: React.MouseEvent<HTMLElement> | Event): void => {
    _invoke(propsRef.current, 'onClose', e, { ...propsRef.current, open: false })
  }, [])

  const handleOpen = useCallback((e: React.MouseEvent<HTMLElement>): void => {
    _invoke(propsRef.current, 'onOpen', e, { ...propsRef.current, open: true })
  }, [])

  const handlePortalMount = useCallback((e: null): void => {
    _invoke(propsRef.current, 'onMount', e, propsRef.current)
  }, [])

  const handlePortalUnmount = useCallback((e: null): void => {
    _invoke(propsRef.current, 'onUnmount', e, propsRef.current)
  }, [])

  const hideOnScroll: (e: Event) => void = useCallback(
    (e: Event): void => {
      setState({ closed: true })

      eventStack.unsub('scroll', hideOnScroll, { target: window })
      timeoutId.current = window.setTimeout(() => {
        setState({ closed: false })
      }, 50)

      handleClose(e)
    },
    [handleClose],
  )

  useEffect(
    () => () => {
      clearTimeout(timeoutId.current)
    },
    [],
  )

  const { context, position, trigger } = props

  if (closed) return trigger as React.ReactElement

  // The former `getDerivedStateFromProps()` skipped this partition while the popup was hidden on
  // scroll, the early return above covers that case as the values would not be used.
  const unhandledProps = getUnhandledProps(Popup, props)
  const contentRestProps = _reduce<Partial<PopupProps>, Record<string, unknown>>(
    unhandledProps,
    (acc, val, key) => {
      if (!_includes(Portal.handledProps, key)) acc[key] = val
      return acc
    },
    {},
  )
  // `Portal.handledProps` is a runtime prop list, the picked props are a `Portal` contract while
  // `Popup` narrows `content` to a shorthand of its own.
  const portalRestProps = _pick(unhandledProps, Portal.handledProps) as unknown as PopupPortalProps

  const getPortalProps = (): PopupPortalProps => {
    const portalProps: PopupPortalProps = {}
    const { on, hoverable } = props
    const normalizedOn = _isArray(on) ? on : [on]

    if (hoverable) {
      portalProps.closeOnPortalMouseLeave = true
      portalProps.mouseLeaveDelay = 300
    }

    if (_includes(normalizedOn, 'hover')) {
      portalProps.openOnTriggerClick = false
      portalProps.closeOnTriggerClick = false
      portalProps.openOnTriggerMouseEnter = true
      portalProps.closeOnTriggerMouseLeave = true

      // Taken from SUI: https://git.io/vPmCm
      portalProps.mouseLeaveDelay = 70
      portalProps.mouseEnterDelay = 50
    }

    if (_includes(normalizedOn, 'click')) {
      portalProps.openOnTriggerClick = true
      portalProps.closeOnTriggerClick = true
      portalProps.closeOnDocumentClick = true
    }

    if (_includes(normalizedOn, 'focus')) {
      portalProps.openOnTriggerFocus = true
      portalProps.closeOnTriggerBlur = true
    }

    return portalProps
  }

  const renderContent = ({
    placement: popperPlacement,
    ref: popperRef,
    style: popperStyle,
  }: PopperChildrenProps): React.ReactNode => {
    const {
      children,
      className,
      content,
      hideOnScroll: hideOnScrollProp,
      flowing,
      header,
      inverted,
      size,
      style,
      wide,
    } = props

    const classes = cx(
      'ui',
      placementMapping[popperPlacement],
      size,
      useKeyOrValueAndKey(wide, 'wide'),
      useKeyOnly(flowing, 'flowing'),
      useKeyOnly(inverted, 'inverted'),
      'popup transition visible',
      className,
    )
    const ElementType = getElementType(Popup, props)

    const styles: React.CSSProperties = {
      // Heads up! We need default styles to get working correctly `flowing`
      left: 'auto',
      right: 'auto',
      // This is required to be properly positioned inside wrapping `div`
      position: 'initial',
      ...style,
    }

    const innerElement = React.createElement(
      ElementType,
      { ...contentRestProps, className: classes, style: styles },
      childrenUtils.isNil(children) ? (
        <>
          {PopupHeader.create(header, { autoGenerateKey: false })}
          {PopupContent.create(content, { autoGenerateKey: false })}
        </>
      ) : (
        children
      ),
      hideOnScrollProp && <EventStack on={hideOnScroll} name="scroll" target="window" />,
    )

    // https://github.com/popperjs/popper-core/blob/f1f9d1ab75b6b0e962f90a5b2a50f6cfd307d794/src/createPopper.js#L136-L137
    // Heads up!
    // A wrapping `div` there is a pure magic, it's required as Popper warns on margins that are
    // defined by SUI CSS. It also means that this `div` will be positioned instead of `content`.
    return createHTMLDivision(
      {},
      {
        overrideProps: {
          children: innerElement,
          ref: popperRef,
          style: {
            // Fixes layout for floated elements
            // https://github.com/Semantic-Org/Semantic-UI-React/issues/4092
            display: 'flex',
            ...popperStyle,
          },
        },
      },
    )
  }

  const modifiers: PopupModifier[] = [
    { name: 'arrow', enabled: false },
    { name: 'eventListeners', options: { scroll: true, resize: true } },
    { name: 'flip', enabled: true },
    { name: 'preventOverflow', enabled: false },
    {
      name: 'syncZIndex',
      enabled: true,
      phase: 'beforeRead',
      fn: ({ state: popperState }) => {
        if (zIndexWasSyncedRef.current) return

        // The positioned wrapper always renders the popup content as its first child.
        popperState.elements.popper.style.zIndex = window.getComputedStyle(
          popperState.elements.popper.firstChild as Element,
        ).zIndex
        zIndexWasSyncedRef.current = true
      },
      effect: () => () => {
        zIndexWasSyncedRef.current = false
      },
    },
  ]
  const referenceElement = createReferenceProxy(context == null ? triggerRef : context)
  const mergedPortalProps = { ...getPortalProps(), ...portalRestProps }

  return (
    <Portal
      {...mergedPortalProps}
      onClose={handleClose}
      onMount={handlePortalMount}
      onOpen={handleOpen}
      onUnmount={handlePortalUnmount}
      trigger={trigger}
      triggerRef={triggerRef}
    >
      <Popper
        modifiers={modifiers}
        placement={positionsMapping[position]}
        strategy={null}
        referenceElement={referenceElement as unknown as VirtualElement}
      >
        {renderContent}
      </Popper>
    </Portal>
  )
}

Popup.Content = PopupContent
Popup.Header = PopupHeader

Popup.handledProps = [
  'as',
  'children',
  'className',
  'content',
  'context',
  'flowing',
  'header',
  'hideOnScroll',
  'hoverable',
  'inverted',
  'on',
  'onClose',
  'onMount',
  'onOpen',
  'onUnmount',
  'position',
  'size',
  'style',
  'trigger',
  'wide',
]



Popup.defaultProps = {
  on: ['click', 'hover'],
  position: 'top left',
} satisfies Partial<PopupProps>

export default Popup
