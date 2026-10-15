import cx from 'clsx'

import React from 'react'
import { SemanticFLOATS, SemanticVERTICALALIGNMENTS } from '../../generic'
import {
  getElementType,
  getUnhandledProps,
  useValueAndKey,
  useVerticalAlignProp,
} from '../../lib'

export interface ListContentProps
  extends Omit<React.HTMLAttributes<HTMLElement>, keyof StrictListContentProps>,
    StrictListContentProps {}

export interface StrictListContentProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string

  /** An list content can be floated left or right. */
  floated?: SemanticFLOATS

  /** An element inside a list can be vertically aligned. */
  verticalAlign?: SemanticVERTICALALIGNMENTS
}

/**
 * A list item can contain a content.
 */
function ListContent(props: ListContentProps): React.ReactElement {
  const { children, className, floated, verticalAlign } = props

  const classes = cx(
    useValueAndKey(floated, 'floated'),
    useVerticalAlignProp(verticalAlign),
    'content',
    className,
  )

  const rest = getUnhandledProps(ListContent, props)
  const ElementType = getElementType(ListContent, props)

  return React.createElement(ElementType, { ...rest, className: classes }, children)
}

ListContent.handledProps = ['as', 'children', 'className', 'floated', 'verticalAlign']



export default ListContent
