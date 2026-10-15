import _isNil from 'lodash-es/isNil'
import _invoke from 'lodash-es/invoke'
import cx from 'clsx'

import React from 'react'
import { SemanticShorthandContent, SemanticShorthandItem } from '../../generic'
import {
  childrenUtils,
  createShorthand,
  createShorthandFactory,
  getElementType,
  getUnhandledProps,
  useKeyOnly,
} from '../../lib'
import Icon, { IconProps } from '../../elements/Icon/Icon'

export interface DropdownItemProps
  extends Omit<React.AnchorHTMLAttributes<HTMLAnchorElement>, 'content' | 'onClick'> {
  as?: React.ElementType
  key?: React.Key
  active?: boolean
  content?: SemanticShorthandContent
  disabled?: boolean
  icon?: SemanticShorthandItem<IconProps>
  onClick?: (event: React.MouseEvent<HTMLDivElement>, data: DropdownItemProps) => void
  selected?: boolean
  text?: SemanticShorthandContent
  value?: boolean | number | string
}

/**
 * An item sub-component for Dropdown component.
 */
function DropdownItem(props: DropdownItemProps): React.ReactElement {
  const { active, children, className, content, disabled, icon, selected, text } = props

  const classes = cx(
    useKeyOnly(active, 'active'),
    useKeyOnly(disabled, 'disabled'),
    useKeyOnly(selected, 'selected'),
    'item',
    className,
  )

  const handleClick = (e: React.MouseEvent<HTMLDivElement>): void => {
    _invoke(props, 'onClick', e, props)
  }

  // add default dropdown icon if item contains another menu
  const iconName = _isNil(icon)
    ? childrenUtils.someByType(children, 'DropdownMenu') && 'dropdown'
    : icon
  const rest = getUnhandledProps(DropdownItem, props)
  const ElementType = getElementType(DropdownItem, props)
  const ariaOptions = {
    role: 'option',
    'aria-disabled': disabled,
    'aria-checked': active,
    'aria-selected': selected,
  }

  if (!childrenUtils.isNil(children)) {
    return React.createElement(
      ElementType,
      { ...rest, ...ariaOptions, className: classes, onClick: handleClick },
      children,
    )
  }

  const iconElement = Icon.create(iconName, { autoGenerateKey: false })
  const textElement = createShorthand<React.HTMLAttributes<HTMLSpanElement>, React.ReactNode>(
    'span',
    (val: React.ReactNode) => ({ children: val }),
    childrenUtils.isNil(content) ? text : content,
    { defaultProps: { className: 'text' }, autoGenerateKey: false },
  )

  return React.createElement(
    ElementType,
    { ...rest, ...ariaOptions, className: classes, onClick: handleClick },
    iconElement,
    textElement,
  )
}

DropdownItem.handledProps = [
  'active',
  'as',
  'children',
  'className',
  'content',
  'disabled',
  'icon',
  'onClick',
  'selected',
  'text',
  'value',
]



DropdownItem.create = createShorthandFactory(DropdownItem, (opts: DropdownItemProps) => opts)

export default DropdownItem
