import cx from 'clsx'

import React from 'react'
import { getElementType, getUnhandledProps } from '../../lib'

export interface MessageItemProps
  extends Omit<React.HTMLAttributes<HTMLElement>, keyof StrictMessageItemProps>,
    StrictMessageItemProps {}

export interface StrictMessageItemProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string
}

/**
 * A message list can contain an item.
 */
function MessageItem(props: MessageItemProps): React.ReactElement {
  const { children, className } = props

  const classes = cx('content', className)
  const rest = getUnhandledProps(MessageItem, props)
  const ElementType = getElementType(MessageItem, props)

  return React.createElement(ElementType, { ...rest, className: classes }, children)
}

MessageItem.handledProps = ['as', 'children', 'className']



MessageItem.defaultProps = { as: 'li' } satisfies Partial<MessageItemProps>

export default MessageItem
