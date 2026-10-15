import cx from 'clsx'

import React from 'react'
import { SemanticTEXTALIGNMENTS } from '../../generic'
import { getElementType, getUnhandledProps, useKeyOnly, useTextAlignProp } from '../../lib'

export interface ContainerProps extends React.HTMLAttributes<HTMLDivElement> {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** A fluid container width is bounded by the page's max width. */
  fluid?: boolean

  /** A container can reduce its maximum width to more naturally accommodate a single column of text. */
  text?: boolean

  /** Align container content. */
  textAlign?: SemanticTEXTALIGNMENTS
}

/**
 * A container limits content to a maximum width.
 */
function Container(props: ContainerProps): React.ReactElement {
  const { children, className, fluid, text, textAlign } = props

  const classes = cx(
    'ui',
    useKeyOnly(text, 'text'),
    useKeyOnly(fluid, 'fluid'),
    useTextAlignProp(textAlign),
    'container',
    className,
  )

  const rest = getUnhandledProps(Container, props)
  const ElementType = getElementType(Container, props)

  return React.createElement(ElementType, { ...rest, className: classes }, children)
}

Container.handledProps = ['as', 'children', 'className', 'fluid', 'text', 'textAlign']



export default Container
