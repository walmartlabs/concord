import cx from 'clsx'

import React from 'react'
import { getElementType, getUnhandledProps, useKeyOnly } from '../../lib'

export interface ListItemProps
  extends Omit<React.LiHTMLAttributes<HTMLLIElement>, keyof StrictListItemProps>,
    StrictListItemProps {}

export interface StrictListItemProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string
  /** Link target URL. */
  href?: string

  /** Link target browsing context. */
  target?: string
}

function ListItem(props: ListItemProps): React.ReactElement {
  const { children, className } = props

  const ElementType = getElementType(ListItem, props)
  const classes = cx(useKeyOnly(ElementType !== 'li', 'item'), className)
  const rest = getUnhandledProps(ListItem, props)

  return React.createElement(
    ElementType,
    { role: 'listitem', className: classes, ...rest },
    children,
  )
}

ListItem.handledProps = ['as', 'children', 'className']



export default ListItem
