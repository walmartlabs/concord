import cx from 'clsx'

import React from 'react'
import { getElementType, getUnhandledProps, useKeyOnly } from '../../lib'

export interface ButtonGroupProps
  extends StrictButtonGroupProps,
    Omit<React.HTMLAttributes<HTMLDivElement>, keyof StrictButtonGroupProps> {}

export interface StrictButtonGroupProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string

  /** Groups can take the width of their container. */
  fluid?: boolean
}

/**
 * Buttons can be grouped.
 */
function ButtonGroup(props: ButtonGroupProps): React.ReactElement {
  const { children, className, fluid } = props

  const classes = cx('ui', useKeyOnly(fluid, 'fluid'), 'buttons', className)
  const rest = getUnhandledProps(ButtonGroup, props)
  const ElementType = getElementType(ButtonGroup, props)

  return React.createElement(ElementType, { ...rest, className: classes }, children)
}

ButtonGroup.handledProps = ['as', 'children', 'className', 'fluid']



export default ButtonGroup
