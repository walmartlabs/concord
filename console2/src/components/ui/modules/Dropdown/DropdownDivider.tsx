import cx from 'clsx'

import React from 'react'
import { getElementType, getUnhandledProps } from '../../lib'

export interface DropdownDividerProps extends React.HTMLAttributes<HTMLDivElement> {
  as?: React.ElementType
}

/**
 * A dropdown menu can contain dividers to separate related content.
 */
function DropdownDivider(props: DropdownDividerProps): React.ReactElement {
  const { className } = props

  const classes = cx('divider', className)
  const rest = getUnhandledProps(DropdownDivider, props)
  const ElementType = getElementType(DropdownDivider, props)

  return React.createElement(ElementType, { ...rest, className: classes })
}

DropdownDivider.handledProps = ['as', 'className']



export default DropdownDivider
