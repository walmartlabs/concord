import cx from 'clsx'

import React from 'react'
import { childrenUtils, getElementType, getUnhandledProps } from '../../lib'

export interface BreadcrumbDividerProps
  extends Omit<React.HTMLAttributes<HTMLElement>, keyof StrictBreadcrumbDividerProps>,
    StrictBreadcrumbDividerProps {}

export interface StrictBreadcrumbDividerProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string
}

/**
 * A divider sub-component for Breadcrumb component.
 */
function BreadcrumbDivider(props: BreadcrumbDividerProps): React.ReactElement {
  const { children, className } = props

  const classes = cx('divider', className)
  const rest = getUnhandledProps(BreadcrumbDivider, props)
  const ElementType = getElementType(BreadcrumbDivider, props)

  return React.createElement(
    ElementType,
    { ...rest, className: classes },
    childrenUtils.isNil(children) ? '/' : children,
  )
}

BreadcrumbDivider.handledProps = ['as', 'children', 'className']



export default BreadcrumbDivider
