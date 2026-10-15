import cx from 'clsx'

import React from 'react'
import { getElementType, getUnhandledProps } from '../../lib'

export interface HeaderContentProps
  extends Omit<React.HTMLAttributes<HTMLElement>, keyof StrictHeaderContentProps>,
    StrictHeaderContentProps {}

export interface StrictHeaderContentProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string
}

/**
 * Header content wraps the main content when there is an adjacent Icon.
 */
function HeaderContent(props: HeaderContentProps): React.ReactElement {
  const { children, className } = props

  const classes = cx('content', className)
  const rest = getUnhandledProps(HeaderContent, props)
  const ElementType = getElementType(HeaderContent, props)

  return React.createElement(ElementType, { ...rest, className: classes }, children)
}

HeaderContent.handledProps = ['as', 'children', 'className']



export default HeaderContent
