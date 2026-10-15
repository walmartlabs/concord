import cx from 'clsx'

import React from 'react'
import { getElementType, getUnhandledProps, useKeyOnly, useTextAlignProp } from '../../lib'

export interface CardContentProps
  extends Omit<React.HTMLAttributes<HTMLDivElement>, keyof StrictCardContentProps>,
    StrictCardContentProps {}

export interface StrictCardContentProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string

  /** A card can contain extra content meant to be formatted separately from the main content. */
  extra?: boolean

  /** A card content can adjust its text alignment. */
  textAlign?: 'center' | 'left' | 'right'
}

/**
 * A card can contain blocks of content or extra content meant to be formatted separately from the main content.
 */
function CardContent(props: CardContentProps): React.ReactElement {
  const { children, className, extra, textAlign } = props

  const classes = cx(useKeyOnly(extra, 'extra'), useTextAlignProp(textAlign), 'content', className)
  const rest = getUnhandledProps(CardContent, props)
  const ElementType = getElementType(CardContent, props)

  return React.createElement(ElementType, { ...rest, className: classes }, children)
}

CardContent.handledProps = ['as', 'children', 'className', 'extra', 'textAlign']



export default CardContent
