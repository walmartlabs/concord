import cx from 'clsx'

import React from 'react'
import { getElementType, getUnhandledProps } from '../../lib'

export interface MessageListProps
  extends Omit<React.HTMLAttributes<HTMLUListElement>, keyof StrictMessageListProps>,
    StrictMessageListProps {}

export interface StrictMessageListProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string
}

/**
 * A message can contain a list of items.
 */
function MessageList(props: MessageListProps): React.ReactElement {
  const { children, className } = props

  const classes = cx('list', className)
  const rest = getUnhandledProps(MessageList, props)
  const ElementType = getElementType(MessageList, props)

  return React.createElement(ElementType, { ...rest, className: classes }, children)
}

MessageList.handledProps = ['as', 'children', 'className']



MessageList.defaultProps = { as: 'ul' } satisfies Partial<MessageListProps>

export default MessageList
