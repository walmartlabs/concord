import cx from 'clsx'

import React from 'react'
import { getElementType, getUnhandledProps } from '../../lib'

export interface HeaderSubheaderProps
  extends Omit<React.HTMLAttributes<HTMLElement>, keyof StrictHeaderSubheaderProps>,
    StrictHeaderSubheaderProps {}

export interface StrictHeaderSubheaderProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string
}

/**
 * Headers may contain subheaders.
 */
function HeaderSubheader(props: HeaderSubheaderProps): React.ReactElement {
  const { children, className } = props

  const classes = cx('sub header', className)
  const rest = getUnhandledProps(HeaderSubheader, props)
  const ElementType = getElementType(HeaderSubheader, props)

  return React.createElement(ElementType, { ...rest, className: classes }, children)
}

HeaderSubheader.handledProps = ['as', 'children', 'className']



export default HeaderSubheader
