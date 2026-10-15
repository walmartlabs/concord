import cx from 'clsx'

import React from 'react'
import { getElementType, getUnhandledProps } from '../../lib'

export interface ButtonOrProps
  extends StrictButtonOrProps,
    Omit<React.HTMLAttributes<HTMLDivElement>, keyof StrictButtonOrProps> {}

export interface StrictButtonOrProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Additional classes. */
  className?: string
}

/**
 * Button groups can contain conditionals.
 */
function ButtonOr(props: ButtonOrProps): React.ReactElement {
  const { className } = props

  const classes = cx('or', className)
  const rest = getUnhandledProps(ButtonOr, props)
  const ElementType = getElementType(ButtonOr, props)

  return React.createElement(ElementType, { ...rest, className: classes })
}

ButtonOr.handledProps = ['as', 'className']



export default ButtonOr
