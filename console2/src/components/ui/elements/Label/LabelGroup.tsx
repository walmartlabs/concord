import cx from 'clsx'

import React from 'react'
import { getElementType, getUnhandledProps } from '../../lib'

export interface LabelGroupProps
  extends StrictLabelGroupProps,
    React.HTMLAttributes<HTMLDivElement> {}

export interface StrictLabelGroupProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string
}

/**
 * A label can be grouped.
 */
function LabelGroup(props: LabelGroupProps): React.ReactElement {
  const { children, className } = props

  const classes = cx('ui', 'labels', className)
  const rest = getUnhandledProps(LabelGroup, props)
  const ElementType = getElementType(LabelGroup, props)

  return React.createElement(ElementType, { ...rest, className: classes }, children)
}

LabelGroup.handledProps = ['as', 'children', 'className']



export default LabelGroup
