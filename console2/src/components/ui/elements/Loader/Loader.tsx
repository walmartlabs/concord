import cx from 'clsx'

import React from 'react'
import { SemanticShorthandContent, SemanticSIZES } from '../../generic'
import { childrenUtils, getElementType, getUnhandledProps, useKeyOnly } from '../../lib'

export interface LoaderProps
  extends Omit<React.HTMLAttributes<HTMLElement>, keyof StrictLoaderProps>,
    StrictLoaderProps {}

export interface StrictLoaderProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** A loader can be active or visible. */
  active?: boolean

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string

  /** Shorthand for primary content. */
  content?: SemanticShorthandContent

  /** Loaders can have different sizes. */
  size?: SemanticSIZES
}

/**
 * A loader alerts a user to wait for an activity to complete.
 * @see Dimmer
 */
function Loader(props: LoaderProps): React.ReactElement {
  const { active, children, className, content, size } = props

  const classes = cx(
    'ui',
    size,
    useKeyOnly(active, 'active'),
    useKeyOnly(!!(children || content), 'text'),
    'loader',
    className,
  )

  const rest = getUnhandledProps(Loader, props)
  const ElementType = getElementType(Loader, props)

  return React.createElement(
    ElementType,
    { ...rest, className: classes },
    childrenUtils.isNil(children) ? content : children,
  )
}

Loader.handledProps = ['active', 'as', 'children', 'className', 'content', 'size']



export default Loader
