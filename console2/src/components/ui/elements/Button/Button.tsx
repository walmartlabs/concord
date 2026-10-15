import _invoke from 'lodash-es/invoke'
import _isNil from 'lodash-es/isNil'
import cx from 'clsx'

import React from 'react'
import {
  SemanticCOLORS,
  SemanticFLOATS,
  SemanticShorthandContent,
  SemanticShorthandItem,
  SemanticSIZES,
} from '../../generic'
import {
  childrenUtils,
  createShorthandFactory,
  getElementType,
  getUnhandledProps,
  useKeyOnly,
  useKeyOrValueAndKey,
  useValueAndKey,
} from '../../lib'
import type { ShorthandFactoryOptions } from '../../lib'
import Icon, { IconProps } from '../Icon/Icon'
import ButtonGroup from './ButtonGroup'
import ButtonOr from './ButtonOr'

export interface ButtonProps
  extends Omit<React.ButtonHTMLAttributes<HTMLButtonElement>, 'content' | 'onClick'> {
  as?: React.ElementType
  attached?: boolean | 'left' | 'right' | 'top' | 'bottom'
  basic?: boolean
  color?: SemanticCOLORS | 'facebook' | 'google plus' | 'instagram' | 'linkedin' | 'twitter' | 'vk' | 'youtube'
  compact?: boolean
  content?: SemanticShorthandContent
  floated?: SemanticFLOATS
  fluid?: boolean
  icon?: boolean | string | SemanticShorthandItem<IconProps>
  labelPosition?: 'left' | 'right'
  href?: string
  rel?: string
  target?: React.HTMLAttributeAnchorTarget
  loading?: boolean
  negative?: boolean
  onClick?: (event: React.MouseEvent<HTMLButtonElement>, data: ButtonProps) => void
  positive?: boolean
  primary?: boolean
  size?: SemanticSIZES
}

export type StrictButtonProps = ButtonProps



class Button extends React.Component<ButtonProps> {
  static Group: typeof ButtonGroup
  static Or: typeof ButtonOr
  static create: (
    value: SemanticShorthandItem<ButtonProps>,
    options?: ShorthandFactoryOptions<ButtonProps>,
  ) => React.ReactNode
  static handledProps: string[]
  static defaultProps?: Partial<ButtonProps>

  private buttonRef: HTMLElement | null = null

  focus(): void {
    this.buttonRef?.focus()
  }

  private setButtonRef = (node: HTMLElement | null): void => {
    this.buttonRef = node
  }

  render(): React.ReactElement {
    return renderButton(this.props, this.setButtonRef)
  }
}
export type ButtonHandle = Button

function renderButton(props: ButtonProps, ref: React.Ref<HTMLElement>): React.ReactElement {
  const computeElementType = (): 'div' | undefined => {
    const { attached } = props

    if (!_isNil(attached)) return 'div'
    return undefined
  }

  const computeTabIndex = (ElementType: React.ElementType): number | undefined => {
    const { disabled, tabIndex } = props

    if (!_isNil(tabIndex)) return tabIndex
    if (disabled) return -1
    if (ElementType === 'div') return 0

    return undefined
  }

  const handleClick = (e: React.MouseEvent<HTMLButtonElement>): void => {
    const { disabled } = props

    if (disabled) {
      e.preventDefault()
      return
    }

    _invoke(props, 'onClick', e, props)
  }

  const hasIconClass = (): boolean => {
    const { children, content, icon, labelPosition } = props

    if (icon === true) return true

    return !!icon && (!!labelPosition || (childrenUtils.isNil(children) && _isNil(content)))
  }

  const computeButtonAriaRole = (ElementType: React.ElementType): string | undefined => {
    const { role } = props

    if (!_isNil(role)) return role
    if (ElementType !== 'button') return 'button'

    return undefined
  }

  const {
    attached,
    basic,
    children,
    className,
    color,
    compact,
    content,
    disabled,
    floated,
    fluid,
    icon,
    labelPosition,
    loading,
    negative,
    positive,
    primary,
    size,
  } = props

  const baseClasses = cx(
    color,
    size,
    useKeyOnly(basic, 'basic'),
    useKeyOnly(compact, 'compact'),
    useKeyOnly(fluid, 'fluid'),
    useKeyOnly(hasIconClass(), 'icon'),
    useKeyOnly(loading, 'loading'),
    useKeyOnly(negative, 'negative'),
    useKeyOnly(positive, 'positive'),
    useKeyOnly(primary, 'primary'),
    useKeyOrValueAndKey(attached, 'attached'),
  )

  const wrapperClasses = cx(
    useKeyOnly(disabled, 'disabled'),
    useValueAndKey(floated, 'floated'),
  )

  const rest = getUnhandledProps(Button, props)
  const ElementType = getElementType(Button, props, computeElementType)
  const tabIndex = computeTabIndex(ElementType)

  const classes = cx(
    'ui',
    baseClasses,
    wrapperClasses,
    useKeyOrValueAndKey(labelPosition, 'labeled'),
    'button',
    className,
  )
  const hasChildren = !childrenUtils.isNil(children)
  const role = computeButtonAriaRole(ElementType)

  return React.createElement(
    ElementType,
    {
      ref,
      className: classes,
      disabled: (disabled && ElementType === 'button') || undefined,
      onClick: handleClick,
      role,
      tabIndex,
    },
    hasChildren && children,
    !hasChildren && Icon.create(icon, { autoGenerateKey: false }),
    !hasChildren && content,
  )
}

Button.Group = ButtonGroup
Button.Or = ButtonOr
Button.create = createShorthandFactory(Button, (value: React.ReactNode) => ({
  content: value,
}))

Button.handledProps = [
  'as',
  'attached',
  'basic',
  'children',
  'className',
  'color',
  'compact',
  'content',
  'disabled',
  'floated',
  'fluid',
  'icon',
  'labelPosition',
  'loading',
  'negative',
  'onClick',
  'positive',
  'primary',
  'role',
  'size',
  'tabIndex',
]



Button.defaultProps = { as: 'button' } satisfies Partial<ButtonProps>

export default Button
