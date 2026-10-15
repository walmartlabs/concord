import cx from 'clsx'

import React from 'react'
import { getElementType, getUnhandledProps } from '../../lib'

export interface TableBodyProps
  extends Omit<React.HTMLAttributes<HTMLTableSectionElement>, keyof StrictTableBodyProps>,
    StrictTableBodyProps {}

export interface StrictTableBodyProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string
}

function TableBody(props: TableBodyProps): React.ReactElement {
  const { children, className } = props

  const classes = cx(className)
  const rest = getUnhandledProps(TableBody, props)
  const ElementType = getElementType(TableBody, props)

  return React.createElement(ElementType, { ...rest, className: classes }, children)
}

TableBody.handledProps = ['as', 'children', 'className']



TableBody.defaultProps = { as: 'tbody' } satisfies Partial<TableBodyProps>

export default TableBody
