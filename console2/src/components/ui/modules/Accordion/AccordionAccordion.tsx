import _map from 'lodash-es/map'
import cx from 'clsx'

import React from 'react'
import { SemanticShorthandCollection } from '../../generic'
import {
  childrenUtils,
  getElementType,
  getUnhandledProps,
  useAutoControlledState,
} from '../../lib'
import AccordionPanel, { AccordionPanelProps } from './AccordionPanel'
import { AccordionTitleProps } from './AccordionTitle'

export interface AccordionAccordionProps
  extends StrictAccordionAccordionProps,
    Omit<React.HTMLAttributes<HTMLDivElement>, keyof StrictAccordionAccordionProps> {}

export interface StrictAccordionAccordionProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string

  /** Shorthand array of props for Accordion. */
  panels?: SemanticShorthandCollection<AccordionPanelProps>
}

/**
 * `activeIndex` is auto controlled by `useAutoControlledState()`, it is
 * deliberately kept out of the public props contract.
 */
interface AccordionAccordionInternalProps extends AccordionAccordionProps {
  activeIndex?: number | string
}

interface AccordionAccordionState {
  /** An index coming from `AccordionTitleProps` can be a string. */
  activeIndex: number | string
}

/**
 * An Accordion can contain sub-accordions.
 */
function AccordionAccordion(props: AccordionAccordionInternalProps): React.ReactElement {
  const [state, setState] = useAutoControlledState<
    AccordionAccordionInternalProps,
    AccordionAccordionState
  >(props, {
    autoControlledProps: ['activeIndex'],
    initialState: { activeIndex: -1 },
  })

  const computeNewIndex = (index: number | string): number | string => {
    const { activeIndex } = state

    return index === activeIndex ? -1 : index
  }

  const handleTitleClick = (
    e: React.MouseEvent<HTMLDivElement>,
    titleProps: AccordionTitleProps,
  ): void => {
    const { index } = titleProps

    setState({ activeIndex: computeNewIndex(index) })
  }

  const isIndexActive = (index: number): boolean => state.activeIndex === index

  const { className, children, panels } = props

  const classes = cx('accordion', className)
  const rest = getUnhandledProps(AccordionAccordion, props)
  const ElementType = getElementType(AccordionAccordion, props)

  return React.createElement(
    ElementType,
    { ...rest, className: classes },
    childrenUtils.isNil(children)
      ? _map(panels, (panel, index) =>
          AccordionPanel.create(panel, {
            defaultProps: {
              active: isIndexActive(index),
              index,
              onTitleClick: handleTitleClick,
            },
          }),
        )
      : children,
  )
}

AccordionAccordion.handledProps = ['as', 'children', 'className', 'panels']



export default AccordionAccordion
