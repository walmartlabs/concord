import cx from 'clsx'

import React from 'react'
import { SemanticCOLORS } from '../../generic'
import { getElementType, getUnhandledProps, SUI } from '../../lib'
import StatisticLabel from './StatisticLabel'
import StatisticValue from './StatisticValue'

export type StatisticSizeProp = 'mini' | 'tiny' | 'small' | 'large' | 'huge'

export interface StatisticProps
  extends Omit<React.HTMLAttributes<HTMLDivElement>, keyof StrictStatisticProps>,
    StrictStatisticProps {}

export interface StrictStatisticProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string

  /** A statistic can be formatted to be different colors. */
  color?: SemanticCOLORS

  /** A statistic can vary in size. */
  size?: StatisticSizeProp
}

/**
 * A statistic emphasizes the current value of an attribute.
 */
function Statistic(props: StatisticProps): React.ReactElement {
  const { children, className, color, size } = props

  const classes = cx('ui', color, size, 'statistic', className)
  const rest = getUnhandledProps(Statistic, props)
  const ElementType = getElementType(Statistic, props)

  return React.createElement(ElementType, { ...rest, className: classes }, children)
}

Statistic.handledProps = ['as', 'children', 'className', 'color', 'size']



Statistic.Label = StatisticLabel
Statistic.Value = StatisticValue

export default Statistic
