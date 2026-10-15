import React from 'react'

/**
 * Returns an object consisting of props beyond the scope of the Component.
 * Useful for getting and spreading unknown props from the user.
 */
const getUnhandledProps = <P extends object>(
  Component: React.ElementType,
  props: P,
): Partial<P> => {
  // Components explicitly list their handled props; this is independent of runtime validation.
  const { handledProps = [] } = Component as React.ElementType & {
    handledProps?: readonly string[]
  }

  return Object.keys(props).reduce<Record<string, unknown>>((acc, prop) => {
    if (prop === 'childKey') return acc
    if (handledProps.indexOf(prop) === -1) acc[prop] = props[prop as keyof P]
    return acc
  }, {}) as unknown as Partial<P>
}

export default getUnhandledProps
