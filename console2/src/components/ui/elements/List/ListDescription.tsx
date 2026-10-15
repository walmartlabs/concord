import cx from 'clsx'

import React from 'react'
import { getElementType, getUnhandledProps } from '../../lib'

export interface ListDescriptionProps
  extends Omit<React.HTMLAttributes<HTMLDivElement>, keyof StrictListDescriptionProps>,
    StrictListDescriptionProps {}

export interface StrictListDescriptionProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string
}

/**
 * A list item can contain a description.
 */
function ListDescription(props: ListDescriptionProps): React.ReactElement {
  const { children, className } = props

  const classes = cx(className, 'description')
  const rest = getUnhandledProps(ListDescription, props)
  const ElementType = getElementType(ListDescription, props)

  return React.createElement(ElementType, { ...rest, className: classes }, children)
}

ListDescription.handledProps = ['as', 'children', 'className']



export default ListDescription
