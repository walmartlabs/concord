import _invoke from 'lodash-es/invoke'
import _isNil from 'lodash-es/isNil'
import cx from 'clsx'

import React from 'react'
import { SemanticCOLORS, SemanticICONS } from '../../generic'
import {
  createShorthandFactory,
  getElementType,
  getUnhandledProps,
  useKeyOnly,
} from '../../lib'

export type IconSizeProp = 'mini' | 'tiny' | 'small' | 'large' | 'big' | 'huge' | 'massive'

export interface IconProps
  extends StrictIconProps,
    Omit<React.HTMLAttributes<HTMLElement>, keyof StrictIconProps> {}

export interface StrictIconProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Additional classes. */
  className?: string

  /** Color of the icon. */
  color?: SemanticCOLORS

  /** Show that the icon is inactive. */
  disabled?: boolean

  /** Fitted, without space to left or right of Icon. */
  fitted?: boolean

  /** Icon can be formatted as a link. */
  link?: boolean

  /** Icon can be used as a simple loader. */
  loading?: boolean

  /** Name of the icon. */
  name?: SemanticICONS

  /** Size of the icon. */
  size?: IconSizeProp

  /** Icon can have an aria hidden. */
  'aria-hidden'?: string

  /** Icon can have an aria label. */
  'aria-label'?: string
}

/**
 * An icon is a glyph used to represent something else.
 * @see Image
 */
function Icon(props: IconProps): React.ReactElement {
  const { className, color, disabled, fitted, link, loading, name, size } = props

  const handleClick = (e: React.MouseEvent<HTMLElement>) => {
    const { disabled } = props

    if (disabled) {
      e.preventDefault()
      return
    }

    _invoke(props, 'onClick', e, props)
  }

  const getIconAriaOptions = (): { 'aria-hidden'?: string; 'aria-label'?: string } => {
    const ariaOptions: { 'aria-hidden'?: string; 'aria-label'?: string } = {}
    const { 'aria-label': ariaLabel, 'aria-hidden': ariaHidden } = props

    if (_isNil(ariaLabel)) {
      ariaOptions['aria-hidden'] = 'true'
    } else {
      ariaOptions['aria-label'] = ariaLabel
    }

    if (!_isNil(ariaHidden)) {
      ariaOptions['aria-hidden'] = ariaHidden
    }

    return ariaOptions
  }

  const classes = cx(
    color,
    name,
    size,
    useKeyOnly(disabled, 'disabled'),
    useKeyOnly(fitted, 'fitted'),
    useKeyOnly(link, 'link'),
    useKeyOnly(loading, 'loading'),
    'icon',
    className,
  )

  const rest = getUnhandledProps(Icon, props)
  const ElementType = getElementType(Icon, props)
  const ariaOptions = getIconAriaOptions()

  return React.createElement(ElementType, {
    ...rest,
    ...ariaOptions,
    className: classes,
    onClick: handleClick,
  })
}

Icon.create = createShorthandFactory(Icon, (value: SemanticICONS) => ({ name: value }))

Icon.handledProps = [
  'aria-hidden',
  'aria-label',
  'as',
  'className',
  'color',
  'disabled',
  'fitted',
  'link',
  'loading',
  'name',
  'size',
]



Icon.defaultProps = { as: 'i' } satisfies Partial<IconProps>

export default Icon
