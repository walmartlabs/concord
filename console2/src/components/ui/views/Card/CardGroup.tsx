import cx from 'clsx'

import React from 'react'
import { SemanticWIDTHS } from '../../generic'
import { getElementType, getUnhandledProps, useWidthProp } from '../../lib'

export interface CardGroupProps
  extends Omit<React.HTMLAttributes<HTMLDivElement>, keyof StrictCardGroupProps>,
    StrictCardGroupProps {}

export interface StrictCardGroupProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** A group of cards can center itself inside its container. */
  centered?: boolean

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string

  /** A group of cards can set how many cards should exist in a row. */
  itemsPerRow?: SemanticWIDTHS

  /** A group of cards can automatically stack rows to a single columns on mobile devices. */
  stackable?: boolean

  /** A card group can adjust its text alignment. */
  textAlign?: 'center' | 'left' | 'right'
}

/**
 * A group of cards.
 */
function CardGroup(props: CardGroupProps): React.ReactElement {
  const { children, className, itemsPerRow } = props

  const classes = cx('ui', useWidthProp(itemsPerRow), 'cards', className)
  const rest = getUnhandledProps(CardGroup, props)
  const ElementType = getElementType(CardGroup, props)

  return React.createElement(ElementType, { ...rest, className: classes }, children)
}

CardGroup.handledProps = ['as', 'children', 'className', 'itemsPerRow']



export default CardGroup
