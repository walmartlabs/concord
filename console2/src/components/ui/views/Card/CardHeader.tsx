import cx from 'clsx'

import React from 'react'
import { getElementType, getUnhandledProps } from '../../lib'

export interface CardHeaderProps
  extends Omit<React.HTMLAttributes<HTMLDivElement>, keyof StrictCardHeaderProps>,
    StrictCardHeaderProps {}

export interface StrictCardHeaderProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string
}

/**
 * A card can contain a header.
 */
function CardHeader(props: CardHeaderProps): React.ReactElement {
  const { children, className } = props

  const classes = cx('header', className)
  const rest = getUnhandledProps(CardHeader, props)
  const ElementType = getElementType(CardHeader, props)

  return React.createElement(ElementType, { ...rest, className: classes }, children)
}

CardHeader.handledProps = ['as', 'children', 'className']



export default CardHeader
