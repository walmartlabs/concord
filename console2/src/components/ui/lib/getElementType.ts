import React from 'react'

/**
 * Returns a createElement() type based on the props of the Component.
 * Useful for calculating what type a component should render as.
 */
function getElementType(
  Component: React.ElementType,
  props: { as?: React.ElementType; href?: string },
  getDefault?: () => React.ElementType | undefined,
): React.ElementType {
  const { defaultProps = {} } = Component as React.ElementType & {
    defaultProps?: { as?: React.ElementType }
  }

  // ----------------------------------------
  // user defined "as" element type
  if (props.as && props.as !== defaultProps.as) return props.as

  // ----------------------------------------
  // computed default element type
  if (getDefault) {
    const computedDefault = getDefault()
    if (computedDefault) return computedDefault
  }

  // ----------------------------------------
  // infer anchor links
  if (props.href) return 'a'

  // ----------------------------------------
  // use defaultProp or 'div'
  return defaultProps.as || 'div'
}

export default getElementType
