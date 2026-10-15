import cx from 'clsx'

import React from 'react'
import { SemanticShorthandContent } from '../../generic'
import {
  childrenUtils,
  createShorthandFactory,
  getElementType,
  getUnhandledProps,
} from '../../lib'

export interface ModalHeaderProps
  extends StrictModalHeaderProps,
    Omit<React.HTMLAttributes<HTMLDivElement>, keyof StrictModalHeaderProps> {}

export interface StrictModalHeaderProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string

  /** Shorthand for primary content. */
  content?: SemanticShorthandContent
}

/**
 * A modal can have a header.
 */
function ModalHeader(props: ModalHeaderProps): React.ReactElement {
  const { children, className, content } = props

  const classes = cx('header', className)

  const rest = getUnhandledProps(ModalHeader, props)
  const ElementType = getElementType(ModalHeader, props)

  return React.createElement(
    ElementType,
    { ...rest, className: classes },
    childrenUtils.isNil(children) ? content : children,
  )
}

ModalHeader.handledProps = ['as', 'children', 'className', 'content']



ModalHeader.create = createShorthandFactory(
  ModalHeader,
  (content: SemanticShorthandContent) => ({ content }),
)

export default ModalHeader
