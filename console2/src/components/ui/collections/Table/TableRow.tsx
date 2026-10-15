import cx from 'clsx'

import React from 'react'
import { SemanticVERTICALALIGNMENTS } from '../../generic'
import { getElementType, getUnhandledProps, useKeyOnly, useVerticalAlignProp } from '../../lib'

export interface TableRowProps
  extends Omit<React.HTMLAttributes<HTMLTableRowElement>, keyof StrictTableRowProps>,
    StrictTableRowProps {}

export interface StrictTableRowProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string

  /** A row may call attention to an error or a negative value. */
  error?: boolean

  /** A row may let a user know whether a value is bad. */
  negative?: boolean

  /** A row may let a user know whether a value is good. */
  positive?: boolean

  /** Callback on click. */
  onClick?: React.MouseEventHandler<HTMLTableRowElement>

  /** A table row can adjust its vertical alignment. */
  verticalAlign?: SemanticVERTICALALIGNMENTS

  /** A row may warn a user. */
  warning?: boolean
}

/**
 * A table can have rows.
 */
function TableRow(props: TableRowProps): React.ReactElement {
  const { children, className, error, negative, positive, verticalAlign, warning } = props

  const classes = cx(
    useKeyOnly(error, 'error'),
    useKeyOnly(negative, 'negative'),
    useKeyOnly(positive, 'positive'),
    useKeyOnly(warning, 'warning'),
    useVerticalAlignProp(verticalAlign),
    className,
  )

  const rest = getUnhandledProps(TableRow, props)
  const ElementType = getElementType(TableRow, props)

  return React.createElement(ElementType, { ...rest, className: classes }, children)
}

TableRow.handledProps = [
  'as',
  'children',
  'className',
  'error',
  'negative',
  'positive',
  'verticalAlign',
  'warning',
]



TableRow.defaultProps = { as: 'tr' } satisfies Partial<TableRowProps>

export default TableRow
