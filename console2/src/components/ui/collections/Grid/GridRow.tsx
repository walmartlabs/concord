import cx from 'clsx'

import React from 'react'
import { SemanticVERTICALALIGNMENTS, SemanticWIDTHS } from '../../generic'
import {
  getElementType,
  getUnhandledProps,
  useKeyOnly,
  useVerticalAlignProp,
  useWidthProp,
} from '../../lib'

export interface GridRowProps extends StrictGridRowProps, React.HTMLAttributes<HTMLElement> {}

export interface StrictGridRowProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** A row can have its columns centered. */
  centered?: boolean

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string

  /** Represents column count per line in Row. */
  columns?: SemanticWIDTHS | 'equal'

  /** A row can specify its vertical alignment to have all its columns vertically centered. */
  verticalAlign?: SemanticVERTICALALIGNMENTS
}

/**
 * A row sub-component for Grid.
 */
function GridRow(props: GridRowProps): React.ReactElement {
  const { centered, children, className, columns, verticalAlign } = props

  const classes = cx(
    useKeyOnly(centered, 'centered'),
    useVerticalAlignProp(verticalAlign),
    useWidthProp(columns, 'column', true),
    'row',
    className,
  )

  const rest = getUnhandledProps(GridRow, props)
  const ElementType = getElementType(GridRow, props)

  return React.createElement(ElementType, { ...rest, className: classes }, children)
}

GridRow.handledProps = ['as', 'centered', 'children', 'className', 'columns', 'verticalAlign']



export default GridRow
