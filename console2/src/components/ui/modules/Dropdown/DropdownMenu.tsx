import cx from 'clsx'

import React from 'react'
import { getElementType, getUnhandledProps, useKeyOnly } from '../../lib'

export interface DropdownMenuProps extends React.HTMLAttributes<HTMLDivElement> {
  open?: boolean
}

/**
 * A dropdown menu can contain a menu.
 */
function DropdownMenu(props: DropdownMenuProps): React.ReactElement {
  const { children, className, open } = props

  const classes = cx(useKeyOnly(open, 'visible'), 'menu transition', className)
  const rest = getUnhandledProps(DropdownMenu, props)
  // `as` is supported at runtime, but is not part of the published props interface
  const ElementType = getElementType(
    DropdownMenu,
    props as DropdownMenuProps & { as?: React.ElementType },
  )

  return React.createElement(ElementType, { ...rest, className: classes }, children)
}

DropdownMenu.handledProps = ['as', 'children', 'className', 'open']



export default DropdownMenu
