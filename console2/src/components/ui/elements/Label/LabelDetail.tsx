import cx from 'clsx'

import React from 'react'
import { getElementType, getUnhandledProps } from '../../lib'

export interface LabelDetailProps
  extends StrictLabelDetailProps,
    React.HTMLAttributes<HTMLElement> {}

export interface StrictLabelDetailProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string
}

function LabelDetail(props: LabelDetailProps): React.ReactElement {
  const { children, className } = props

  const classes = cx('detail', className)
  const rest = getUnhandledProps(LabelDetail, props)
  const ElementType = getElementType(LabelDetail, props)

  return React.createElement(ElementType, { ...rest, className: classes }, children)
}

LabelDetail.handledProps = ['as', 'children', 'className']



export default LabelDetail
