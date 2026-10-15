import cx from 'clsx'

import React from 'react'
import { SemanticCOLORS } from '../../generic'
import { getElementType, getUnhandledProps, useKeyOnly } from '../../lib'

export interface SegmentProps
  extends StrictSegmentProps,
    Omit<React.HTMLAttributes<HTMLElement>, 'color'> {}

export interface StrictSegmentProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** A basic segment has no special formatting. */
  basic?: boolean

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string

  /** Segment can be colored. */
  color?: SemanticCOLORS

  /** A segment may show its content is disabled. */
  disabled?: boolean

  /** A segment can be formatted to appear less noticeable. */
  secondary?: boolean
}

/**
 * A segment is used to create a grouping of related content.
 */
function Segment(props: SegmentProps): React.ReactElement {
  const { basic, children, className, color, disabled, secondary } = props

  const classes = cx(
    'ui',
    color,
    useKeyOnly(basic, 'basic'),
    useKeyOnly(disabled, 'disabled'),
    useKeyOnly(secondary, 'secondary'),
    'segment',
    className,
  )

  const rest = getUnhandledProps(Segment, props)
  const ElementType = getElementType(Segment, props)

  return React.createElement(ElementType, { ...rest, className: classes }, children)
}

Segment.handledProps = [
  'as',
  'basic',
  'children',
  'className',
  'color',
  'disabled',
  'secondary',
]



export default Segment
