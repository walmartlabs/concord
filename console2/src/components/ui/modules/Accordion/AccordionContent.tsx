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

export interface AccordionContentProps
  extends StrictAccordionContentProps,
    Omit<React.HTMLAttributes<HTMLDivElement>, keyof StrictAccordionContentProps> {}

export interface StrictAccordionContentProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Whether or not the content is visible. */
  active?: boolean

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string

  /** Shorthand for primary content. */
  content?: SemanticShorthandContent
}

/**
 * A content sub-component for Accordion component.
 */
function AccordionContent(props: AccordionContentProps): React.ReactElement {
  const { active, children, className, content } = props

  const classes = cx('content', useKeyOnly(active, 'active'), className)
  const rest = getUnhandledProps(AccordionContent, props)
  const ElementType = getElementType(AccordionContent, props)

  return React.createElement(
    ElementType,
    { ...rest, className: classes },
    childrenUtils.isNil(children) ? content : children,
  )
}

AccordionContent.handledProps = ['active', 'as', 'children', 'className', 'content']



AccordionContent.create = createShorthandFactory(AccordionContent, (content: React.ReactNode) => ({
  content,
}))

export default AccordionContent
