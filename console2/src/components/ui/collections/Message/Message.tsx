import _isNil from 'lodash-es/isNil'
import cx from 'clsx'

import React from 'react'
import { SemanticShorthandContent } from '../../generic'
import {
  childrenUtils,
  createHTMLParagraph,
  getElementType,
  getUnhandledProps,
  useKeyOnly,
} from '../../lib'
import Icon from '../../elements/Icon'
import MessageContent from './MessageContent'
import MessageHeader from './MessageHeader'
import MessageItem from './MessageItem'
import MessageList from './MessageList'

export type MessageSizeProp = 'mini' | 'tiny' | 'small' | 'large' | 'big' | 'huge' | 'massive'

export interface MessageProps
  extends Omit<React.HTMLAttributes<HTMLElement>, keyof StrictMessageProps>,
    StrictMessageProps {}

export interface StrictMessageProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string

  /** Shorthand for primary content. */
  content?: SemanticShorthandContent

  /** A message may be formatted to display a negative message. Same as `negative`. */
  error?: boolean

  /** A message may be formatted to display a negative message. Same as `error`. */
  negative?: boolean

  /**
   * A message that the user can choose to hide.
   * Called when the user clicks the "x" icon. This also adds the "x" icon.
   *
   * @param {SyntheticEvent} event - React's original SyntheticEvent.
   * @param {object} data - All props.
   */
  onDismiss?: (event: React.MouseEvent<HTMLElement>, data: MessageProps) => void

  /** A message can have different sizes. */
  size?: MessageSizeProp

  /** A message may be formatted to display a positive message.  Same as `positive`. */
  success?: boolean

  /** A message may be formatted to display warning messages. */
  warning?: boolean
}

/**
 * A message displays information that explains nearby content.
 * @see Form
 */
function Message(props: MessageProps): React.ReactElement {
  const {
    children,
    className,
    content,
    error,
    negative,
    onDismiss,
    size,
    success,
    warning,
  } = props

  const handleDismiss = (e: React.MouseEvent<HTMLElement>): void => {
    if (onDismiss) onDismiss(e, props)
  }

  const classes = cx(
    'ui',
    size,
    useKeyOnly(error, 'error'),
    useKeyOnly(negative, 'negative'),
    useKeyOnly(success, 'success'),
    useKeyOnly(warning, 'warning'),
    'message',
    className,
  )
  const dismissIcon = onDismiss && <Icon name="close" onClick={handleDismiss} />
  const rest = getUnhandledProps(Message, props)
  const ElementType = getElementType(Message, props)

  if (!childrenUtils.isNil(children)) {
    return React.createElement(ElementType, { ...rest, className: classes }, dismissIcon, children)
  }

  return React.createElement(
    ElementType,
    { ...rest, className: classes },
    dismissIcon,
    !_isNil(content) &&
      React.createElement(
        MessageContent,
        null,
        createHTMLParagraph(content, { autoGenerateKey: false }),
      ),
  )
}

Message.Content = MessageContent
Message.Header = MessageHeader
Message.List = MessageList
Message.Item = MessageItem

Message.handledProps = [
  'as',
  'children',
  'className',
  'content',
  'error',
  'negative',
  'onDismiss',
  'size',
  'success',
  'warning',
]



export default Message
