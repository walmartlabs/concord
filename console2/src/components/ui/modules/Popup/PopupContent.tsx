import cx from 'clsx'

import React from 'react'
import { createShorthandFactory, getElementType, getUnhandledProps } from '../../lib'

export interface PopupContentProps
  extends StrictPopupContentProps,
    React.HTMLAttributes<HTMLDivElement> {}

export interface StrictPopupContentProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** The content of the Popup */
  children?: React.ReactNode

  /** Classes to add to the Popup content className. */
  className?: string
}

/**
 * A PopupContent displays the content body of a Popover.
 */
function PopupContent(props: PopupContentProps): React.ReactElement {
  const { children, className } = props

  const classes = cx('content', className)
  const rest = getUnhandledProps(PopupContent, props)
  const ElementType = getElementType(PopupContent, props)

  return React.createElement(ElementType, { ...rest, className: classes }, children)
}

PopupContent.handledProps = ['as', 'children', 'className']



PopupContent.create = createShorthandFactory(PopupContent, (children: React.ReactNode) => ({
  children,
}))

export default PopupContent
