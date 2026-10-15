import _get from 'lodash-es/get'
import _includes from 'lodash-es/includes'
import _invoke from 'lodash-es/invoke'
import cx from 'clsx'

import React, { cloneElement, useEffect, useRef, useState } from 'react'
import shallowEqual from 'shallowequal'
import { normalizeTransitionDuration, SUI, useKeyOnly } from '../../lib'
import {
  computeStatuses,
  TRANSITION_STATUS_ENTERED,
  TRANSITION_STATUS_ENTERING,
  TRANSITION_STATUS_EXITED,
  TRANSITION_STATUS_EXITING,
  TRANSITION_STATUS_INITIAL,
  TRANSITION_STATUS_UNMOUNTED,
  TransitionDerivedState,
  TransitionStatus,
} from './utils/computeStatuses'

export type TRANSITION_STATUSES = 'ENTERED' | 'ENTERING' | 'EXITED' | 'EXITING' | 'UNMOUNTED'

export interface TransitionProps
  extends StrictTransitionProps,
    React.HTMLAttributes<HTMLElement> {}

export interface StrictTransitionProps {
  /** Named animation event to used. Must be defined in CSS. */
  animation?: 'fade' | 'fade up' | 'scale' | 'none'

  /** Primary content. */
  children?: React.ReactNode

  /** Duration of the CSS transition animation in milliseconds. */
  duration?: number | string | TransitionPropDuration

  /** Show the component; triggers the enter or exit animation. */
  visible?: boolean

  /** Wait until the first "enter" transition to mount the component (add it to the DOM). */
  mountOnShow?: boolean

  /**
   * Callback on each transition that changes visibility to shown.
   *
   * @param {null}
   * @param {object} data - All props with status.
   */
  onComplete?: (nothing: null, data: TransitionEventData) => void

  /**
   * Callback on animation start.
   *
   * @param {null}
   * @param {object} data - All props with status.
   */
  onStart?: (nothing: null, data: TransitionEventData) => void

  /** Run the enter animation when the component mounts, if it is initially shown. */
  transitionOnMount?: boolean

  /** Unmount the component (remove it from the DOM) when it is not shown. */
  unmountOnHide?: boolean
}

export interface TransitionEventData extends TransitionProps {
  status: TRANSITION_STATUSES
}

export interface TransitionPropDuration {
  hide: number
  show: number
}

/** Transition type a callback status is reported with. */
const TRANSITION_CALLBACK_TYPE: Partial<Record<TransitionStatus, 'hide' | 'show'>> = {
  [TRANSITION_STATUS_ENTERED]: 'show',
  [TRANSITION_STATUS_EXITED]: 'hide',
}

/** Transition type a status animates with. */
const TRANSITION_STYLE_TYPE: Partial<Record<TransitionStatus, 'hide' | 'show'>> = {
  [TRANSITION_STATUS_ENTERING]: 'show',
  [TRANSITION_STATUS_EXITING]: 'hide',
}

interface TransitionState extends TransitionDerivedState {
  status: TransitionStatus
}

/**
 * A transition is an animation usually used to move content in or out of view.
 */
function Transition(props: TransitionProps): React.ReactElement | null {
  const [state, setState] = useState<TransitionState>(() => ({
    status: TRANSITION_STATUS_INITIAL,
  }))

  /** Identifier of the pending timeout that advances the transition status. */
  const timeoutIdRef = useRef<NodeJS.Timeout | undefined>(undefined)

  /** State of the previously committed render, empty while the component mounts. */
  const prevStateRef = useRef<Partial<TransitionState>>({})

  // ----------------------------------------
  // Derived state
  // ----------------------------------------

  // `getDerivedStateFromProps` equivalent: the statuses are derived from the props and the current
  // status on every render and persisted with a render phase update, so that the next derivation
  // starts from the status this one produced. Every derivation settles on itself, hence a single
  // extra render pass is enough.
  const derivedState = computeStatuses({
    mountOnShow: props.mountOnShow,
    status: state.status,
    transitionOnMount: props.transitionOnMount,
    visible: props.visible,
    unmountOnHide: props.unmountOnHide,
  })
  const nextState: TransitionState = { ...state, ...derivedState }

  if (!shallowEqual(nextState, state)) {
    setState(nextState)
  }

  // ----------------------------------------
  // Callback handling
  // ----------------------------------------

  const handleStart = (nextStatus: TransitionStatus): void => {
    const { duration } = props
    const durationType = TRANSITION_CALLBACK_TYPE[nextStatus]
    const durationValue = normalizeTransitionDuration(duration, durationType)

    // The duration keeps its CSS string form, and the timer API coerces it into a
    // delay exactly like the original implementation relied on.
    timeoutIdRef.current = setTimeout(() => {
      setState((prevState) => ({ ...prevState, status: nextStatus }))
    }, durationValue as number)
  }

  const updateStatus = (prevState: Partial<TransitionState>): void => {
    if (prevState.status !== nextState.status) {
      // Timeout should be cleared in any case as previous can lead set to unexpected `nextStatus`
      clearTimeout(timeoutIdRef.current)

      if (nextState.nextStatus) {
        handleStart(nextState.nextStatus)
      }
    }

    if (!prevState.animating && nextState.animating) {
      _invoke(props, 'onStart', null, { ...props, status: nextState.status })
    }

    if (prevState.animating && !nextState.animating) {
      _invoke(props, 'onComplete', null, { ...props, status: nextState.status })
    }
  }

  // ----------------------------------------
  // Lifecycle
  // ----------------------------------------

  // `componentDidMount` and `componentDidUpdate`: the previous state is empty while mounting and
  // holds the state of the previous commit afterwards.
  useEffect(() => {
    updateStatus(prevStateRef.current)

    prevStateRef.current = {
      status: nextState.status,
      animating: nextState.animating,
      nextStatus: nextState.nextStatus,
    }
  }, [nextState.status, nextState.animating])

  // `componentWillUnmount`
  useEffect(
    () => () => {
      clearTimeout(timeoutIdRef.current)
      // Reset the previous-state bookkeeping so a remount (e.g. StrictMode effect
      // replay) re-runs `updateStatus` from a fresh mount and re-schedules the
      // status-advance timer instead of skipping on a stale previous status.
      prevStateRef.current = {}
    },
    [],
  )

  const computeClasses = (): string => {
    const { animation, children } = props
    const { animating, status } = nextState

    const childClasses = _get(children, 'props.className')
    const isDirectional = _includes(SUI.DIRECTIONAL_TRANSITIONS, animation)

    if (isDirectional) {
      return cx(
        animation,
        childClasses,
        useKeyOnly(animating, 'animating'),
        useKeyOnly(status === TRANSITION_STATUS_ENTERING, 'in'),
        useKeyOnly(status === TRANSITION_STATUS_EXITING, 'out'),
        useKeyOnly(status === TRANSITION_STATUS_EXITED, 'hidden'),
        useKeyOnly(status !== TRANSITION_STATUS_EXITED, 'visible'),
        'transition',
      )
    }

    return cx(animation, childClasses, useKeyOnly(animating, 'animating transition'))
  }

  const computeStyle = (): React.CSSProperties => {
    const { children, duration } = props
    const { status } = nextState

    const childStyle = _get(children, 'props.style')
    const type = TRANSITION_STYLE_TYPE[status]
    const animationDuration = type && normalizeTransitionDuration(duration, type) + 'ms'

    return { ...childStyle, animationDuration }
  }

  // ----------------------------------------
  // Render
  // ----------------------------------------

  if (nextState.status === TRANSITION_STATUS_UNMOUNTED) {
    return null
  }

  // The runtime clones whatever single child it is given, so a non-element
  // child keeps failing here exactly like it did in the original implementation.
  return cloneElement(props.children as React.ReactElement, {
    className: computeClasses(),
    style: computeStyle(),
  })
}

Transition.handledProps = [
  'animation',
  'children',
  'duration',
  'mountOnShow',
  'onComplete',
  'onStart',
  'transitionOnMount',
  'unmountOnHide',
  'visible',
]



Transition.defaultProps = {
  animation: 'fade',
  duration: 500,
  visible: true,
  mountOnShow: true,
  transitionOnMount: false,
  unmountOnHide: false,
} satisfies Partial<TransitionProps>

export default Transition
