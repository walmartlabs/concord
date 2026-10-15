import _isUndefined from 'lodash-es/isUndefined'
import React from 'react'
import shallowEqual from 'shallowequal'

export interface UseAutoControlledStateConfig<P extends object, S extends object> {
  /** Props that are auto controlled: parent may pass either `foo` or `defaultFoo`. */
  autoControlledProps?: readonly (keyof P & keyof S & string)[]

  /** Initial (non auto controlled) state, matching `getInitialAutoControlledState()`. */
  initialState?: Partial<S> | (() => Partial<S>)

  /**
   * Recomputed state on every render, matching the static
   * `getAutoControlledStateFromProps()` contract. Receives the current
   * committed state as `previousState`.
   */
  getAutoControlledStateFromProps?: (
    props: P,
    computedState: S,
    previousState: S,
  ) => Partial<S> | null
}

type SetState<S> = (partial: Partial<S> | ((prevState: S) => Partial<S>)) => void

/**
 * Returns the auto controlled state value for a prop. The initial value is chosen in this order:
 *  - regular props
 *  - then, default props
 *  - then, initial state
 *  - then, `checked` defaults to false
 *  - then, `value` defaults to '' or [] if props.multiple
 *  - else, undefined
 */
const getAutoControlledStateValue = (
  propName: string,
  props: Record<string, unknown>,
  state: Record<string, unknown> | null | undefined,
  includeDefaults: boolean = false,
): unknown => {
  const propValue = props[propName]
  if (propValue !== undefined) return propValue

  if (includeDefaults) {
    const defaultProp = props[`default${propName[0].toUpperCase()}${propName.slice(1)}`]
    if (defaultProp !== undefined) return defaultProp

    if (state) {
      const initialState = state[propName]
      if (initialState !== undefined) return initialState
    }
  }

  if (propName === 'checked') return false
  if (propName === 'value') return props.multiple ? [] : ''

  return undefined
}

/**
 * Hook replacement for `ModernAutoControlledComponent`, preserving the exact
 * auto-controlled / `getAutoControlledStateFromProps` semantics of the legacy
 * class (including the persistence of bookkeeping fields such as Dropdown's
 * `__options` / `__value` and Search's `prevValue`).
 *
 * Returns the effective state and a `setState` that merges partial updates,
 * mirroring `this.setState(partial)`.
 */
function useAutoControlledState<P extends object, S extends object>(
  props: P,
  config: UseAutoControlledStateConfig<P, S>,
): [S, SetState<S>] {
  const {
    autoControlledProps = [],
    initialState,
    getAutoControlledStateFromProps,
  } = config

  const [state, setState] = React.useState<S>(() => {
    const initial = (
      typeof initialState === 'function'
        ? (initialState as () => Partial<S>)()
        : (initialState ?? ({} as Partial<S>))
    ) as S

    const initialAutoControlledState = autoControlledProps.reduce<
      Record<string, unknown>
    >((acc, prop) => {
      acc[prop] = getAutoControlledStateValue(
        prop,
        props as unknown as Record<string, unknown>,
        initial as unknown as Record<string, unknown> | null | undefined,
        true,
      )
      return acc
    }, {})

    return { ...initial, ...(initialAutoControlledState as unknown as S) } as S
  })

  // Replicate `ModernAutoControlledComponent.getDerivedStateFromProps()`.
  const newStateFromProps = autoControlledProps.reduce<Record<string, unknown>>(
    (acc, prop) => {
      if (!_isUndefined((props as unknown as Record<string, unknown>)[prop])) {
        acc[prop] = (props as unknown as Record<string, unknown>)[prop]
      }
      return acc
    },
    {},
  )

  const computedState = { ...state, ...(newStateFromProps as unknown as S) } as S

  let nextState = computedState
  if (getAutoControlledStateFromProps) {
    const derived =
      getAutoControlledStateFromProps(props, computedState, state) ?? ({} as Partial<S>)
    nextState = { ...computedState, ...derived } as S
  }

  // Persist derived state during render (React re-renders immediately before
  // committing children). Guarded to avoid an infinite loop; identical derived
  // values settle in a single extra render.
  if (!shallowEqual(nextState, state)) {
    setState(nextState)
  }

  const setPartialState: SetState<S> = React.useCallback(
    (partial) => {
      setState((prev) => ({
        ...prev,
        ...(typeof partial === 'function' ? (partial as (p: S) => Partial<S>)(prev) : partial),
      }))
    },
    [setState],
  )

  return [nextState, setPartialState]
}

export default useAutoControlledState
