
import React from 'react'
import { getUnhandledProps } from '../../lib'
import TableCell, { StrictTableCellProps } from './TableCell'

export interface TableHeaderCellProps
  extends Omit<React.ThHTMLAttributes<HTMLTableCellElement>, keyof StrictTableHeaderCellProps>,
    StrictTableHeaderCellProps {}

export interface StrictTableHeaderCellProps extends StrictTableCellProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Additional classes. */
  className?: string
}

/**
 * A table can have a header cell.
 */
function TableHeaderCell(props: TableHeaderCellProps): React.ReactElement {
  const { as, className } = props

  const rest = getUnhandledProps(TableHeaderCell, props)

  return React.createElement(TableCell, { ...rest, as, className })
}

TableHeaderCell.handledProps = ['as', 'className']



TableHeaderCell.defaultProps = { as: 'th' } satisfies Partial<TableHeaderCellProps>

export default TableHeaderCell
