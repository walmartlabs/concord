import cx from 'clsx'

import React from 'react'
import { SemanticShorthandContent } from '../../generic'
import {
  childrenUtils,
  createShorthandFactory,
  getElementType,
  getUnhandledProps,
} from '../../lib'

export interface DropdownTextProps
  extends Omit<React.HTMLAttributes<HTMLSpanElement>, 'content'> {
  as?: React.ElementType
  content?: SemanticShorthandContent
}

/**
 * A dropdown contains a selected value.
 */
function DropdownText(props: DropdownTextProps): React.ReactElement {
  const { children, className, content } = props

  const classes = cx('divider', className)
  const rest = getUnhandledProps(DropdownText, props)
  const ElementType = getElementType(DropdownText, props)

  return React.createElement(
    ElementType,
    { 'aria-atomic': true, 'aria-live': 'polite', role: 'alert', ...rest, className: classes },
    childrenUtils.isNil(children) ? content : children,
  )
}

DropdownText.handledProps = ['as', 'children', 'className', 'content']



DropdownText.create = createShorthandFactory(DropdownText, (val: SemanticShorthandContent) => ({
  content: val,
}))

export default DropdownText
