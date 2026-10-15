import cx from 'clsx'

import React from 'react'
import { SemanticVERTICALALIGNMENTS, SemanticWIDTHS } from '../../generic'
import {
  getElementType,
  getUnhandledProps,
  useKeyOnly,
  useTextAlignProp,
  useVerticalAlignProp,
  useWidthProp,
} from '../../lib'

export interface TableCellProps
  extends Omit<React.TdHTMLAttributes<HTMLTableCellElement>, keyof StrictTableCellProps>,
    StrictTableCellProps {}

export interface StrictTableCellProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** A cell can be active or selected. */
  active?: boolean

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string

  /** Number of columns spanned by the cell. */
  colSpan?: number

  /** Number of rows spanned by the cell. */
  rowSpan?: number

  /** A table can be collapsing, taking up only as much space as its rows. */
  collapsing?: boolean

  /** A disabled cell cannot be interacted with. */
  disabled?: boolean

  /** A cell can specify that its contents should remain on a single line and not wrap. */
  singleLine?: boolean

  /** A table cell can adjust its text alignment. */
  textAlign?: 'center' | 'left' | 'right'

  /** A table cell can adjust its vertical alignment. */
  verticalAlign?: SemanticVERTICALALIGNMENTS

  /** A table can specify the width of individual columns independently. */
  width?: SemanticWIDTHS
}

/**
 * A table row can have cells.
 */
function TableCell(props: TableCellProps): React.ReactElement {
  const {
    active,
    children,
    className,
    collapsing,
    disabled,
    singleLine,
    textAlign,
    verticalAlign,
    width,
  } = props

  const classes = cx(
    useKeyOnly(active, 'active'),
    useKeyOnly(collapsing, 'collapsing'),
    useKeyOnly(disabled, 'disabled'),
    useKeyOnly(singleLine, 'single line'),
    useTextAlignProp(textAlign),
    useVerticalAlignProp(verticalAlign),
    useWidthProp(width, 'wide'),
    className,
  )

  const rest = getUnhandledProps(TableCell, props)
  const ElementType = getElementType(TableCell, props)

  return React.createElement(ElementType, { ...rest, className: classes }, children)
}

TableCell.handledProps = [
  'active',
  'as',
  'children',
  'className',
  'collapsing',
  'disabled',
  'singleLine',
  'textAlign',
  'verticalAlign',
  'width',
]



TableCell.defaultProps = { as: 'td' } satisfies Partial<TableCellProps>

export default TableCell
