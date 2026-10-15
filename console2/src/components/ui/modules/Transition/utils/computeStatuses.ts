export const TRANSITION_STATUS_INITIAL = 'INITIAL'
export const TRANSITION_STATUS_ENTERED = 'ENTERED'
export const TRANSITION_STATUS_ENTERING = 'ENTERING'
export const TRANSITION_STATUS_EXITED = 'EXITED'
export const TRANSITION_STATUS_EXITING = 'EXITING'
export const TRANSITION_STATUS_UNMOUNTED = 'UNMOUNTED'

/**
 * Every status a `Transition` can hold, including its implementation-only
 * initial status which is never exposed through its callbacks.
 */
export type TransitionStatus =
  | typeof TRANSITION_STATUS_INITIAL
  | typeof TRANSITION_STATUS_ENTERED
  | typeof TRANSITION_STATUS_ENTERING
  | typeof TRANSITION_STATUS_EXITED
  | typeof TRANSITION_STATUS_EXITING
  | typeof TRANSITION_STATUS_UNMOUNTED

/** State a `Transition` derives from its props and its current status. */
export interface TransitionDerivedState {
  /** Whether the transition is currently animating. */
  animating?: boolean

  /** Status to move to once the current animation completed. */
  nextStatus?: TransitionStatus

  /** Current transition status. */
  status?: TransitionStatus
}

export interface ComputeStatusesOptions {
  /** Current transition status. */
  status?: TransitionStatus

  /** Whether the component is mounted only once it is shown. */
  mountOnShow?: boolean

  /** Whether the enter animation runs on mount. */
  transitionOnMount?: boolean

  /** Whether the component is shown. */
  visible?: boolean

  /** Whether the component unmounts once it is hidden. */
  unmountOnHide?: boolean
}

/**
 * @param {Object} [options]
 * @param {String} [options.status]
 * @param {Boolean} [options.mountOnShow]
 * @param {Boolean} [options.transitionOnMount]
 * @param {Boolean} [options.visible]
 * @param {Boolean} [options.unmountOnHide]
 */
export function computeStatuses(options: ComputeStatusesOptions): TransitionDerivedState {
  const { mountOnShow, status, transitionOnMount, visible, unmountOnHide } = options

  if (visible) {
    if (status === TRANSITION_STATUS_INITIAL) {
      if (transitionOnMount) {
        return {
          animating: true,
          status: TRANSITION_STATUS_ENTERING,
          nextStatus: TRANSITION_STATUS_ENTERED,
        }
      }

      return {
        animating: false,
        status: TRANSITION_STATUS_ENTERED,
        nextStatus: undefined,
      }
    }

    if (status === TRANSITION_STATUS_UNMOUNTED) {
      return {
        animating: true,
        status: TRANSITION_STATUS_ENTERING,
        nextStatus: TRANSITION_STATUS_ENTERED,
      }
    }

    if (status === TRANSITION_STATUS_EXITED || status === TRANSITION_STATUS_EXITING) {
      return {
        animating: true,
        status: TRANSITION_STATUS_ENTERING,
        nextStatus: TRANSITION_STATUS_ENTERED,
      }
    }

    if (status === TRANSITION_STATUS_ENTERING) {
      return {}
    }
    /* istanbul ignore else */

    if (status === TRANSITION_STATUS_ENTERED) {
      return {
        animating: false,
        status: TRANSITION_STATUS_ENTERED,
        nextStatus: undefined,
      }
    }
  }

  if (status === TRANSITION_STATUS_INITIAL) {
    if (mountOnShow || unmountOnHide) {
      return {
        animating: false,
        status: TRANSITION_STATUS_UNMOUNTED,
        nextStatus: undefined,
      }
    }

    return {
      animating: false,
      status: TRANSITION_STATUS_EXITED,
      nextStatus: undefined,
    }
  }

  if (status === TRANSITION_STATUS_ENTERED || status === TRANSITION_STATUS_ENTERING) {
    return {
      animating: true,
      status: TRANSITION_STATUS_EXITING,
      nextStatus: unmountOnHide ? TRANSITION_STATUS_UNMOUNTED : TRANSITION_STATUS_EXITED,
    }
  }

  if (status === TRANSITION_STATUS_EXITING) {
    return {}
  }

  if (status === TRANSITION_STATUS_EXITED) {
    return {
      animating: false,
      status: TRANSITION_STATUS_EXITED,
      nextStatus: undefined,
    }
  }
  /* istanbul ignore else */

  if (status === TRANSITION_STATUS_UNMOUNTED) {
    return {
      animating: false,
      status: TRANSITION_STATUS_UNMOUNTED,
      nextStatus: undefined,
    }
  }
  /* istanbul ignore next */

  throw new Error(
    `Transition:computeStatuses(): an unexpected status transition: { visible: ${visible}, status: ${status} }`,
  )
}
