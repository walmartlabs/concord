import cx from 'clsx'

import React from 'react'
import { getElementType, getUnhandledProps } from '../../lib'

export interface CardDescriptionProps
  extends Omit<React.HTMLAttributes<HTMLDivElement>, keyof StrictCardDescriptionProps>,
    StrictCardDescriptionProps {}

export interface StrictCardDescriptionProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string
}

/**
 * A card can contain a description with one or more paragraphs.
 */
function CardDescription(props: CardDescriptionProps): React.ReactElement {
  const { children, className } = props

  const classes = cx('description', className)
  const rest = getUnhandledProps(CardDescription, props)
  const ElementType = getElementType(CardDescription, props)

  return React.createElement(ElementType, { ...rest, className: classes }, children)
}

CardDescription.handledProps = ['as', 'children', 'className']



export default CardDescription
