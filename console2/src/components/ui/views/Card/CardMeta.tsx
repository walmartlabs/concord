import cx from 'clsx'

import React from 'react'
import { getElementType, getUnhandledProps } from '../../lib'

export interface CardMetaProps
  extends Omit<React.HTMLAttributes<HTMLDivElement>, keyof StrictCardMetaProps>,
    StrictCardMetaProps {}

export interface StrictCardMetaProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string
}

/**
 * A card can contain content metadata.
 */
function CardMeta(props: CardMetaProps): React.ReactElement {
  const { children, className } = props

  const classes = cx('meta', className)
  const rest = getUnhandledProps(CardMeta, props)
  const ElementType = getElementType(CardMeta, props)

  return React.createElement(ElementType, { ...rest, className: classes }, children)
}

CardMeta.handledProps = ['as', 'children', 'className']



export default CardMeta
