type TransitionDuration =
  | number
  | string
  | { hide?: number | string; show?: number | string }

/**
 * Normalizes the duration of a transition.
 * @param duration The value to normalize.
 * @param type The type of transition.
 */
export default (duration: TransitionDuration, type: 'hide' | 'show'): number | string =>
  typeof duration === 'number' || typeof duration === 'string' ? duration : duration[type]
