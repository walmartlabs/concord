import cx from 'clsx'

import React from 'react'
import { getElementType, getUnhandledProps } from '../../lib'

export interface MessageHeaderProps
  extends Omit<React.HTMLAttributes<HTMLElement>, keyof StrictMessageHeaderProps>,
    StrictMessageHeaderProps {}

export interface StrictMessageHeaderProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string
}

/**
 * A message can contain a header.
 */
function MessageHeader(props: MessageHeaderProps): React.ReactElement {
  const { children, className } = props

  const classes = cx('header', className)
  const rest = getUnhandledProps(MessageHeader, props)
  const ElementType = getElementType(MessageHeader, props)

  return React.createElement(ElementType, { ...rest, className: classes }, children)
}

MessageHeader.handledProps = ['as', 'children', 'className']



export default MessageHeader
