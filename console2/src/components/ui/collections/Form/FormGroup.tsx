import cx from 'clsx'

import React from 'react'
import { SemanticWIDTHS } from '../../generic'
import { getElementType, getUnhandledProps, useKeyOnly, useWidthProp } from '../../lib'

export interface FormGroupProps extends StrictFormGroupProps {}

export interface StrictFormGroupProps extends React.HTMLAttributes<HTMLDivElement> {
  /** An element type to render as (string or function). */
  as?: React.ElementType
  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string

  /** Multiple fields may be inline in a row. */
  inline?: boolean

  /** Fields Groups can specify their width in grid columns or automatically divide fields to be equal width. */
  widths?: SemanticWIDTHS | 'equal'
}

/**
 * A set of fields can appear grouped together.
 * @see Form
 */
function FormGroup(props: FormGroupProps): React.ReactElement {
  const { children, className, inline, widths } = props

  const classes = cx(
    useKeyOnly(inline, 'inline'),
    useWidthProp(widths, null, true),
    'fields',
    className,
  )

  const rest = getUnhandledProps(FormGroup, props)
  const ElementType = getElementType(FormGroup, props)

  return React.createElement(ElementType, { ...rest, className: classes }, children)
}

FormGroup.handledProps = ['as', 'children', 'className', 'inline', 'widths']



export default FormGroup
