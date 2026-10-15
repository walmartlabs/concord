import cx from 'clsx'

import React from 'react'
import { SemanticSIZES, SemanticVERTICALALIGNMENTS } from '../../generic'
import {
  getElementType,
  getUnhandledProps,
  useKeyOnly,
  useKeyOrValueAndKey,
  useVerticalAlignProp,
} from '../../lib'
import ListContent from './ListContent'
import ListDescription from './ListDescription'
import ListHeader from './ListHeader'
import ListIcon from './ListIcon'
import ListItem from './ListItem'

export interface ListProps
  extends Omit<React.HTMLAttributes<HTMLElement>, keyof StrictListProps>,
    StrictListProps {}

export interface StrictListProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string

  /** A list can show divisions between content. */
  divided?: boolean

  /** A list can relax its padding to provide more negative space. */
  relaxed?: boolean | 'very'

  /** A selection list formats list items as possible choices. */
  selection?: boolean

  /** A list can vary in size. */
  size?: SemanticSIZES

  /** An element inside a list can be vertically aligned. */
  verticalAlign?: SemanticVERTICALALIGNMENTS
}

/**
 * A list groups related content.
 */
function List(props: ListProps): React.ReactElement {
  const { children, className, divided, relaxed, selection, size, verticalAlign } = props

  const classes = cx(
    'ui',
    size,
    useKeyOnly(divided, 'divided'),
    useKeyOnly(selection, 'selection'),
    useKeyOrValueAndKey(relaxed, 'relaxed'),
    useVerticalAlignProp(verticalAlign),
    'list',
    className,
  )

  const rest = getUnhandledProps(List, props)
  const ElementType = getElementType(List, props)

  return React.createElement(ElementType, { role: 'list', className: classes, ...rest }, children)
}

List.handledProps = [
  'as',
  'children',
  'className',
  'divided',
  'relaxed',
  'selection',
  'size',
  'verticalAlign',
]



List.Content = ListContent
List.Description = ListDescription
List.Header = ListHeader
List.Icon = ListIcon
List.Item = ListItem

export default List
