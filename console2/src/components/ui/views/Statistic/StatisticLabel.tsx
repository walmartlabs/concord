import cx from 'clsx'

import React from 'react'
import { getElementType, getUnhandledProps } from '../../lib'

export interface StatisticLabelProps
  extends Omit<React.HTMLAttributes<HTMLElement>, keyof StrictStatisticLabelProps>,
    StrictStatisticLabelProps {}

export interface StrictStatisticLabelProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string
}

/**
 * A statistic can contain a label to help provide context for the presented value.
 */
function StatisticLabel(props: StatisticLabelProps): React.ReactElement {
  const { children, className } = props

  const classes = cx('label', className)
  const rest = getUnhandledProps(StatisticLabel, props)
  const ElementType = getElementType(StatisticLabel, props)

  return React.createElement(ElementType, { ...rest, className: classes }, children)
}

StatisticLabel.handledProps = ['as', 'children', 'className']



export default StatisticLabel
