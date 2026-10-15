import cx from 'clsx'

import React from 'react'
import { SemanticShorthandContent } from '../../generic'
import { childrenUtils, getElementType, getUnhandledProps, useKeyOnly } from '../../lib'

export interface DividerProps extends Omit<React.HTMLAttributes<HTMLDivElement>, 'content'> {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string

  /** Shorthand for primary content. */
  content?: SemanticShorthandContent

  disabled?: boolean

  /** Divider can segment content horizontally. */
  horizontal?: boolean
}

/**
 * A divider visually segments content into groups.
 */
function Divider(props: DividerProps): React.ReactElement {
  const { children, className, content, horizontal } = props

  const classes = cx('ui', useKeyOnly(horizontal, 'horizontal'), 'divider', className)
  const rest = getUnhandledProps(Divider, props)
  const ElementType = getElementType(Divider, props)

  return React.createElement(
    ElementType,
    { ...rest, className: classes },
    childrenUtils.isNil(children) ? content : children,
  )
}

Divider.handledProps = ['as', 'children', 'className', 'content', 'horizontal']



export default Divider
