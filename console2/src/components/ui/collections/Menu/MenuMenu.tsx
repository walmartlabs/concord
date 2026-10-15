import cx from 'clsx'

import React from 'react'
import { getElementType, getUnhandledProps } from '../../lib'

export interface MenuMenuProps
  extends Omit<React.HTMLAttributes<HTMLElement>, keyof StrictMenuMenuProps>,
    StrictMenuMenuProps {}

export interface StrictMenuMenuProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string

  /** A sub menu can take left or right position. */
  position?: 'left' | 'right'
}

/**
 * A menu can contain a sub menu.
 */
function MenuMenu(props: MenuMenuProps): React.ReactElement {
  const { children, className, position } = props

  const classes = cx(position, 'menu', className)
  const rest = getUnhandledProps(MenuMenu, props)
  const ElementType = getElementType(MenuMenu, props)

  return React.createElement(ElementType, { ...rest, className: classes }, children)
}

MenuMenu.handledProps = ['as', 'children', 'className', 'position']



export default MenuMenu
