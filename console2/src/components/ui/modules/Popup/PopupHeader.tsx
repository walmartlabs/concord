import cx from 'clsx'

import React from 'react'
import { createShorthandFactory, getElementType, getUnhandledProps } from '../../lib'

export interface PopupHeaderProps
  extends StrictPopupHeaderProps,
    React.HTMLAttributes<HTMLDivElement> {}

export interface StrictPopupHeaderProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string
}

/**
 * A PopupHeader displays a header in a Popover.
 */
function PopupHeader(props: PopupHeaderProps): React.ReactElement {
  const { children, className } = props

  const classes = cx('header', className)
  const rest = getUnhandledProps(PopupHeader, props)
  const ElementType = getElementType(PopupHeader, props)

  return React.createElement(ElementType, { ...rest, className: classes }, children)
}

PopupHeader.handledProps = ['as', 'children', 'className']



PopupHeader.create = createShorthandFactory(PopupHeader, (children: React.ReactNode) => ({
  children,
}))

export default PopupHeader
