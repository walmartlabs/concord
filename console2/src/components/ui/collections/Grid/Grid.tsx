import cx from 'clsx'

import React from 'react'
import {
  SemanticTEXTALIGNMENTS,
  SemanticVERTICALALIGNMENTS,
  SemanticWIDTHS,
} from '../../generic'
import {
  getElementType,
  getUnhandledProps,
  useKeyOnly,
  useKeyOrValueAndKey,
  useTextAlignProp,
  useVerticalAlignProp,
  useWidthProp,
} from '../../lib'
import GridColumn from './GridColumn'
import GridRow from './GridRow'

export interface GridProps extends StrictGridProps, React.HTMLAttributes<HTMLElement> {}

export interface StrictGridProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** A grid can have its columns centered. */
  centered?: boolean

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string

  /** Represents column count per row in Grid. */
  columns?: SemanticWIDTHS | 'equal'

  /** A grid can have dividers between its columns. */
  divided?: boolean | 'vertically'

  /** A grid can specify its text alignment. */
  textAlign?: SemanticTEXTALIGNMENTS

  /** A grid can specify its vertical alignment to have all its columns vertically centered. */
  verticalAlign?: SemanticVERTICALALIGNMENTS
}

/**
 * A grid is used to harmonize negative space in a layout.
 */
function Grid(props: GridProps): React.ReactElement {
  const { centered, children, className, columns, divided, textAlign, verticalAlign } = props

  const classes = cx(
    'ui',
    useKeyOnly(centered, 'centered'),
    useKeyOrValueAndKey(divided, 'divided'),
    useTextAlignProp(textAlign),
    useVerticalAlignProp(verticalAlign),
    useWidthProp(columns, 'column', true),
    'grid',
    className,
  )

  const rest = getUnhandledProps(Grid, props)
  const ElementType = getElementType(Grid, props)

  return React.createElement(ElementType, { ...rest, className: classes }, children)
}

Grid.handledProps = [
  'as',
  'centered',
  'children',
  'className',
  'columns',
  'divided',
  'textAlign',
  'verticalAlign',
]



Grid.Column = GridColumn
Grid.Row = GridRow

export default Grid
