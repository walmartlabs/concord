import cx from 'clsx'

import React from 'react'
import { SemanticCOLORS, SemanticWIDTHS } from '../../generic'
import {
  getElementType,
  getUnhandledProps,
  useKeyOnly,
  useKeyOrValueAndKey,
  useTextAlignProp,
  useWidthProp,
} from '../../lib'
import TableBody from './TableBody'
import TableCell from './TableCell'
import TableHeader from './TableHeader'
import TableHeaderCell from './TableHeaderCell'
import TableRow from './TableRow'

export interface TableProps
  extends Omit<React.TableHTMLAttributes<HTMLTableElement>, keyof StrictTableProps>,
    StrictTableProps {}

export interface StrictTableProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Attach table to other content */
  attached?: boolean | 'top' | 'bottom'

  /** A table can reduce its complexity to increase readability. */
  basic?: boolean | 'very'

  /** A table may be divided into individual cells. */
  celled?: boolean

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string

  /** A table can be collapsing, taking up only as much space as its rows. */
  collapsing?: boolean

  /** A table can be given a color to distinguish it from other tables. */
  color?: SemanticCOLORS

  /** A table can specify its column count to divide its content evenly. */
  columns?: SemanticWIDTHS

  /** A table may sometimes need to be more compact to make more rows visible at a time. */
  compact?: boolean | 'very'

  /** A table may be formatted to emphasize a first column that defines a rows content. */
  definition?: boolean

  /** A table can have its rows appear selectable. */
  selectable?: boolean

  /** A table can specify that its cell contents should remain on a single line and not wrap. */
  singleLine?: boolean

  /** A table can also be small or large. */
  size?: 'small' | 'large'

  /** A table can stripe alternate rows of content with a darker color to increase contrast. */
  striped?: boolean

  /** A table can adjust its text alignment. */
  textAlign?: 'center' | 'left' | 'right'

  /** Calendar alignment variant. */
  unstackable?: boolean
}

/**
 * A table displays a collections of data grouped into rows.
 */
function Table(props: TableProps): React.ReactElement {
  const {
    attached,
    basic,
    celled,
    children,
    className,
    collapsing,
    color,
    columns,
    compact,
    definition,
    selectable,
    singleLine,
    size,
    striped,
    textAlign,
    unstackable,
  } = props

  const classes = cx(
    'ui',
    color,
    size,
    useKeyOnly(celled, 'celled'),
    useKeyOnly(collapsing, 'collapsing'),
    useKeyOnly(definition, 'definition'),
    useKeyOnly(selectable, 'selectable'),
    useKeyOnly(singleLine, 'single line'),
    useKeyOnly(striped, 'striped'),
    useKeyOnly(unstackable, 'unstackable'),
    useKeyOrValueAndKey(attached, 'attached'),
    useKeyOrValueAndKey(basic, 'basic'),
    useKeyOrValueAndKey(compact, 'compact'),
    useTextAlignProp(textAlign),
    useWidthProp(columns, 'column'),
    'table',
    className,
  )

  const rest = getUnhandledProps(Table, props)
  const ElementType = getElementType(Table, props)

  return React.createElement(ElementType, { ...rest, className: classes }, children)
}

Table.handledProps = [
  'as',
  'attached',
  'basic',
  'celled',
  'children',
  'className',
  'collapsing',
  'color',
  'columns',
  'compact',
  'definition',
  'selectable',
  'singleLine',
  'size',
  'striped',
  'textAlign',
  'unstackable',
]



Table.defaultProps = { as: 'table' } satisfies Partial<TableProps>

Table.Body = TableBody
Table.Cell = TableCell
Table.Header = TableHeader
Table.HeaderCell = TableHeaderCell
Table.Row = TableRow

export default Table
