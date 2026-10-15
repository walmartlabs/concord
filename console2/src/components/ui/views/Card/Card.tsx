import cx from 'clsx'

import React from 'react'
import { SemanticCOLORS } from '../../generic'
import { getElementType, getUnhandledProps, useKeyOnly } from '../../lib'
import CardContent from './CardContent'
import CardDescription from './CardDescription'
import CardGroup from './CardGroup'
import CardHeader from './CardHeader'
import CardMeta from './CardMeta'

export interface CardProps
  extends Omit<React.HTMLAttributes<HTMLDivElement>, keyof StrictCardProps>,
    StrictCardProps {}

export interface StrictCardProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** A Card can center itself inside its container. */
  centered?: boolean

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string

  /** A Card can be formatted to display different colors. */
  color?: SemanticCOLORS

  /**
   * Called on click. When passed, the component renders as an `a`
   * tag by default instead of a `div`.
   *
   * @param {SyntheticEvent} event - React's original SyntheticEvent.
   * @param {object} data - All props.
   */
  onClick?: (event: React.MouseEvent<HTMLAnchorElement>, data: CardProps) => void
}

/**
 * A card displays site content in a manner similar to a playing card.
 */
function Card(props: CardProps): React.ReactElement {
  const { centered, children, className, color, onClick } = props

  const handleClick = (e: React.MouseEvent<HTMLAnchorElement>): void => {
    if (onClick) onClick(e, props)
  }

  const classes = cx('ui', color, useKeyOnly(centered, 'centered'), 'card', className)
  const rest = getUnhandledProps(Card, props)
  const ElementType = getElementType(Card, props, () => {
    if (onClick) return 'a'
  })

  return React.createElement(
    ElementType,
    { ...rest, className: classes, onClick: handleClick },
    children,
  )
}

Card.Content = CardContent
Card.Description = CardDescription
Card.Group = CardGroup
Card.Header = CardHeader
Card.Meta = CardMeta

Card.handledProps = ['as', 'centered', 'children', 'className', 'color', 'onClick']



export default Card
