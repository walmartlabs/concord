
import React from 'react'
import { getElementType, getUnhandledProps } from '../../lib'

export interface TableHeaderProps
  extends Omit<React.HTMLAttributes<HTMLTableSectionElement>, keyof StrictTableHeaderProps>,
    StrictTableHeaderProps {}

export interface StrictTableHeaderProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string
}

/**
 * A table can have a header.
 */
function TableHeader(props: TableHeaderProps): React.ReactElement {
  const { children, className } = props

  const rest = getUnhandledProps(TableHeader, props)
  const ElementType = getElementType(TableHeader, props)

  return React.createElement(ElementType, { ...rest, className }, children)
}

TableHeader.handledProps = ['as', 'children', 'className']



TableHeader.defaultProps = { as: 'thead' } satisfies Partial<TableHeaderProps>

export default TableHeader
