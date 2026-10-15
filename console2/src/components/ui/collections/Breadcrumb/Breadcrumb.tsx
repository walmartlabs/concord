import cx from 'clsx'

import React from 'react'
import { getElementType, getUnhandledProps, SUI } from '../../lib'
import BreadcrumbDivider from './BreadcrumbDivider'
import BreadcrumbSection from './BreadcrumbSection'

export interface BreadcrumbProps
  extends Omit<React.HTMLAttributes<HTMLElement>, keyof StrictBreadcrumbProps>,
    StrictBreadcrumbProps {}

export interface StrictBreadcrumbProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string

  /** Size of Breadcrumb */
  size?: 'mini' | 'tiny' | 'small' | 'large' | 'big' | 'huge' | 'massive'
}

/**
 * A breadcrumb is used to show hierarchy between content.
 */
function Breadcrumb(props: BreadcrumbProps): React.ReactElement {
  const { children, className, size } = props

  const classes = cx('ui', size, 'breadcrumb', className)
  const rest = getUnhandledProps(Breadcrumb, props)
  const ElementType = getElementType(Breadcrumb, props)

  return React.createElement(ElementType, { ...rest, className: classes }, children)
}

Breadcrumb.handledProps = ['as', 'children', 'className', 'size']



Breadcrumb.Divider = BreadcrumbDivider
Breadcrumb.Section = BreadcrumbSection

export default Breadcrumb
