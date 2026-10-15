import cx from 'clsx'

import React from 'react'
import { getElementType, getUnhandledProps } from '../../lib'

export interface ListHeaderProps
  extends Omit<React.AnchorHTMLAttributes<HTMLAnchorElement>, keyof StrictListHeaderProps>,
    StrictListHeaderProps {}

export interface StrictListHeaderProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string
  to?: string
}

/**
 * A list item can contain a header.
 */
function ListHeader(props: ListHeaderProps): React.ReactElement {
  const { children, className } = props

  const classes = cx('header', className)
  const rest = getUnhandledProps(ListHeader, props)
  const ElementType = getElementType(ListHeader, props)

  return React.createElement(ElementType, { ...rest, className: classes }, children)
}

ListHeader.handledProps = ['as', 'children', 'className']



export default ListHeader
