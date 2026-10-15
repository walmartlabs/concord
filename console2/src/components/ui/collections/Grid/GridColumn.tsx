import cx from 'clsx'

import React from 'react'
import { SemanticTEXTALIGNMENTS, SemanticWIDTHS } from '../../generic'
import {
  createShorthandFactory,
  getElementType,
  getUnhandledProps,
  useTextAlignProp,
  useWidthProp,
} from '../../lib'

export interface GridColumnProps
  extends StrictGridColumnProps,
    React.HTMLAttributes<HTMLElement> {}

export interface StrictGridColumnProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string

  /** A column can specify its text alignment. */
  textAlign?: SemanticTEXTALIGNMENTS

  /** Represents width of column. */
  width?: SemanticWIDTHS
}

/**
 * A column sub-component for Grid.
 */
function GridColumn(props: GridColumnProps): React.ReactElement {
  const { children, className, textAlign, width } = props

  const classes = cx(useTextAlignProp(textAlign), useWidthProp(width, 'wide'), 'column', className)

  const rest = getUnhandledProps(GridColumn, props)
  const ElementType = getElementType(GridColumn, props)

  return React.createElement(ElementType, { ...rest, className: classes }, children)
}

GridColumn.handledProps = ['as', 'children', 'className', 'textAlign', 'width']



GridColumn.create = createShorthandFactory(GridColumn, (children: React.ReactNode) => ({
  children,
}))

export default GridColumn
