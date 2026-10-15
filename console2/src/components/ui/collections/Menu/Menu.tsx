import cx from 'clsx'

import React from 'react'
import { SemanticWIDTHS } from '../../generic'
import {
  getElementType,
  getUnhandledProps,
  useKeyOnly,
  useKeyOrValueAndKey,
  useWidthProp,
} from '../../lib'
import MenuItem from './MenuItem'
import MenuMenu from './MenuMenu'

export interface MenuProps
  extends Omit<React.HTMLAttributes<HTMLElement>, keyof StrictMenuProps>,
    StrictMenuProps {}

export interface StrictMenuProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** A menu item or menu can have no borders. */
  borderless?: boolean

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string

  /** A vertical menu may take the size of its container. */
  fluid?: boolean

  /** A menu may have its colors inverted to show greater contrast. */
  inverted?: boolean

  /** A menu can point to show its relationship to nearby content. */
  pointing?: boolean

  /** A menu can adjust its appearance to de-emphasize its contents. */
  secondary?: boolean

  /** A menu can vary in size. */
  size?: 'mini' | 'tiny' | 'small' | 'large' | 'huge' | 'massive'

  /** A menu can be formatted to show tabs of information. */
  tabular?: boolean | 'right'

  /** A vertical menu displays elements vertically. */
  vertical?: boolean

  /** A menu can have its items divided evenly. */
  widths?: SemanticWIDTHS
}

/**
 * A menu displays grouped navigation actions.
 * @see Dropdown
 */
function Menu(props: MenuProps): React.ReactElement {
  const {
    borderless,
    children,
    className,
    fluid,
    inverted,
    pointing,
    secondary,
    size,
    tabular,
    vertical,
    widths,
  } = props

  const classes = cx(
    'ui',
    size,
    useKeyOnly(borderless, 'borderless'),
    useKeyOnly(fluid, 'fluid'),
    useKeyOnly(inverted, 'inverted'),
    useKeyOnly(pointing, 'pointing'),
    useKeyOnly(secondary, 'secondary'),
    useKeyOnly(vertical, 'vertical'),
    useKeyOrValueAndKey(tabular, 'tabular'),
    useWidthProp(widths, 'item'),
    className,
    'menu',
  )

  const rest = getUnhandledProps(Menu, props)
  const ElementType = getElementType(Menu, props)

  return React.createElement(ElementType, { ...rest, className: classes }, children)
}

Menu.handledProps = [
  'as',
  'borderless',
  'children',
  'className',
  'fluid',
  'inverted',
  'pointing',
  'secondary',
  'size',
  'tabular',
  'vertical',
  'widths',
]



Menu.Item = MenuItem
Menu.Menu = MenuMenu

export default Menu
