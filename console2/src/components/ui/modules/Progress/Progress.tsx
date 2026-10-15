import _clamp from 'lodash-es/clamp'
import cx from 'clsx'

import React from 'react'
import { SemanticCOLORS } from '../../generic'
import { getElementType, getUnhandledProps, useKeyOnly } from '../../lib'

export interface ProgressProps
  extends StrictProgressProps,
    Omit<React.HTMLAttributes<HTMLDivElement>, keyof StrictProgressProps> {}

export interface StrictProgressProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** A progress bar can show activity. */
  active?: boolean

  /** Additional classes. */
  className?: string

  /** A progress bar can have different colors. */
  color?: SemanticCOLORS

  /** Current percent complete. */
  percent?: number | string

  /** A progress bar can contain a text value indicating current progress. */
  progress?: boolean | 'percent'

  /** A progress bar can vary in size. */
  size?: 'tiny' | 'small' | 'medium' | 'large' | 'big'
}

/**
 * A progress bar shows the progression of a task.
 */
function Progress(props: ProgressProps): React.ReactElement {
  const { active, className, color, progress, size } = props

  // `clamp` coerces its argument through `toNumber`, so the string form of
  // `percent` is accepted here exactly like it was by the original implementation.
  const percent = _clamp(props.percent as number, 0, 100) || 0

  const classes = cx('ui', color, size, useKeyOnly(active, 'active'), 'progress', className)
  const rest = getUnhandledProps(Progress, props)
  const ElementType = getElementType(Progress, props)

  return React.createElement(
    ElementType,
    { ...rest, className: classes, 'data-percent': Math.floor(percent) },
    React.createElement(
      'div',
      { className: 'bar', style: { width: percent + '%' } },
      progress ? React.createElement('div', { className: 'progress' }, percent + '%') : null,
    ),
  )
}

Progress.handledProps = ['active', 'as', 'className', 'color', 'percent', 'progress', 'size']



export default Progress
