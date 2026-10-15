import cx from 'clsx'

import React from 'react'
import { SemanticShorthandContent } from '../../generic'
import {
  childrenUtils,
  createShorthandFactory,
  getElementType,
  getUnhandledProps,
  useKeyOnly,
} from '../../lib'

export interface ModalContentProps
  extends StrictModalContentProps,
    Omit<React.HTMLAttributes<HTMLDivElement>, keyof StrictModalContentProps> {}

export interface StrictModalContentProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string

  /** Shorthand for primary content. */
  content?: SemanticShorthandContent

  /** A modal can use the entire size of the screen. */
  scrolling?: boolean
}

/**
 * A modal can contain content.
 */
function ModalContent(props: ModalContentProps): React.ReactElement {
  const { children, className, content, scrolling } = props

  const classes = cx(className, useKeyOnly(scrolling, 'scrolling'), 'content')

  const rest = getUnhandledProps(ModalContent, props)
  const ElementType = getElementType(ModalContent, props)

  return React.createElement(
    ElementType,
    { ...rest, className: classes },
    childrenUtils.isNil(children) ? content : children,
  )
}

ModalContent.handledProps = ['as', 'children', 'className', 'content', 'scrolling']



ModalContent.create = createShorthandFactory(
  ModalContent,
  (content: SemanticShorthandContent) => ({ content }),
)

export default ModalContent
