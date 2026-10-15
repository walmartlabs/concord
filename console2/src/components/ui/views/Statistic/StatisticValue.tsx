import cx from 'clsx'

import React from 'react'
import { getElementType, getUnhandledProps } from '../../lib'

export interface StatisticValueProps
  extends Omit<React.HTMLAttributes<HTMLElement>, keyof StrictStatisticValueProps>,
    StrictStatisticValueProps {}

export interface StrictStatisticValueProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string
}

/**
 * A statistic can contain a numeric, icon, image, or text value.
 */
function StatisticValue(props: StatisticValueProps): React.ReactElement {
  const { children, className } = props

  const classes = cx('value', className)
  const rest = getUnhandledProps(StatisticValue, props)
  const ElementType = getElementType(StatisticValue, props)

  return React.createElement(ElementType, { ...rest, className: classes }, children)
}

StatisticValue.handledProps = ['as', 'children', 'className']



export default StatisticValue
