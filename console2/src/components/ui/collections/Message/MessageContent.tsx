import cx from 'clsx'

import React from 'react'
import { getElementType, getUnhandledProps } from '../../lib'

export interface MessageContentProps
  extends Omit<React.HTMLAttributes<HTMLElement>, keyof StrictMessageContentProps>,
    StrictMessageContentProps {}

export interface StrictMessageContentProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string
}

/**
 * A message can contain a content.
 */
function MessageContent(props: MessageContentProps): React.ReactElement {
  const { children, className } = props

  const classes = cx('content', className)
  const rest = getUnhandledProps(MessageContent, props)
  const ElementType = getElementType(MessageContent, props)

  return React.createElement(ElementType, { ...rest, className: classes }, children)
}

MessageContent.handledProps = ['as', 'children', 'className']



export default MessageContent
