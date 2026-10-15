import _invoke from 'lodash-es/invoke'
import cx from 'clsx'

import React from 'react'
import { getElementType, getUnhandledProps, useKeyOnly, useKeyOrValueAndKey } from '../../lib'

export interface MenuItemProps
  extends Omit<React.AnchorHTMLAttributes<HTMLAnchorElement>, keyof StrictMenuItemProps>,
    StrictMenuItemProps {}

export interface StrictMenuItemProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** A menu item can be active. */
  active?: boolean

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string

  /** A menu item can be disabled. */
  disabled?: boolean

  /** A menu item or menu can remove element padding, vertically or horizontally. */
  fitted?: boolean | 'horizontally' | 'vertically'

  /**
   * Called on click. When passed, the component will render as an `a`
   * tag by default instead of a `div`.
   *
   * @param {SyntheticEvent} event - React's original SyntheticEvent.
   * @param {object} data - All props.
   */
  onClick?: (event: React.MouseEvent<HTMLAnchorElement>, data: MenuItemProps) => void

  /** Dropdown text forwarded when rendered as a Dropdown. */
  text?: string

  /** A menu item can take left or right position. */
  position?: 'left' | 'right'
}

/**
 * A menu can contain an item.
 */
function MenuItem(props: MenuItemProps): React.ReactElement {
  const { active, children, className, disabled, fitted, onClick, position } = props

  const handleClick = (e: React.MouseEvent<HTMLAnchorElement>): void => {
    if (!disabled) _invoke(props, 'onClick', e, props)
  }

  const classes = cx(
    position,
    useKeyOnly(active, 'active'),
    useKeyOnly(disabled, 'disabled'),
    useKeyOrValueAndKey(fitted, 'fitted'),
    'item',
    className,
  )
  const ElementType = getElementType(MenuItem, props, () => {
    if (onClick) return 'a'
  })
  // Leave callee props such as Dropdown's text unhandled for as={Dropdown}.
  const rest = getUnhandledProps(MenuItem, props)

  return React.createElement(
    ElementType,
    { ...rest, className: classes, onClick: handleClick },
    children,
  )
}

MenuItem.handledProps = [
  'active',
  'as',
  'children',
  'className',
  'disabled',
  'fitted',
  'onClick',
  'position',
]



export default MenuItem
