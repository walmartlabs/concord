import cx from 'clsx'

import React from 'react'
import { SemanticCOLORS, SemanticShorthandItem, SemanticTEXTALIGNMENTS } from '../../generic'
import {
  childrenUtils,
  getElementType,
  getUnhandledProps,
  useKeyOnly,
  useKeyOrValueAndKey,
  useTextAlignProp,
} from '../../lib'
import Icon, { IconProps } from '../Icon/Icon'
import HeaderContent from './HeaderContent'
import HeaderSubheader from './HeaderSubheader'

export interface HeaderProps
  extends Omit<React.HTMLAttributes<HTMLElement>, keyof StrictHeaderProps>,
    StrictHeaderProps {}

export interface StrictHeaderProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Attach header  to other content, like a segment. */
  attached?: boolean | 'top' | 'bottom'

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string

  /** Color of the header. */
  color?: SemanticCOLORS

  /** Shorthand for primary content. */
  content?: React.ReactNode

  /** Show that the header is inactive. */
  disabled?: boolean

  /** Divide header from the content below it. */
  dividing?: boolean

  /** Add an icon by name, props object, or pass an element. */
  icon?: boolean | SemanticShorthandItem<IconProps>

  /** Content headings are sized with em and are based on the font-size of their container. */
  size?: 'tiny' | 'small' | 'medium' | 'large' | 'huge'

  /** Align header content. */
  textAlign?: SemanticTEXTALIGNMENTS
}

/**
 * A header provides a short summary of content.
 */
function Header(props: HeaderProps): React.ReactElement {
  const { attached, children, className, color, content, disabled, dividing, icon, size, textAlign } = props

  const classes = cx(
    'ui',
    color,
    size,
    useKeyOnly(disabled, 'disabled'),
    useKeyOnly(dividing, 'dividing'),
    useKeyOnly(icon === true, 'icon'),
    useKeyOrValueAndKey(attached, 'attached'),
    useTextAlignProp(textAlign),
    'header',
    className,
  )

  const rest = getUnhandledProps(Header, props)
  const ElementType = getElementType(Header, props)

  if (!childrenUtils.isNil(children)) {
    return React.createElement(ElementType, { ...rest, className: classes }, children)
  }

  const iconElement = Icon.create(icon, { autoGenerateKey: false })

  if (iconElement) {
    return React.createElement(
      ElementType,
      { ...rest, className: classes },
      iconElement,
      content && React.createElement(HeaderContent, null, content),
    )
  }

  return React.createElement(ElementType, { ...rest, className: classes }, content)
}

Header.handledProps = [
  'as',
  'attached',
  'children',
  'className',
  'color',
  'content',
  'disabled',
  'dividing',
  'icon',
  'size',
  'textAlign',
]



Header.Content = HeaderContent
Header.Subheader = HeaderSubheader

export default Header
