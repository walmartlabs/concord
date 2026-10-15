import _invoke from 'lodash-es/invoke'

import React from 'react'
import { SemanticShorthandItem } from '../../generic'
import { createShorthandFactory } from '../../lib'
import AccordionContent, { AccordionContentProps } from './AccordionContent'
import AccordionTitle, { AccordionTitleProps } from './AccordionTitle'

export interface AccordionPanelProps
  extends StrictAccordionPanelProps,
    Omit<React.HTMLAttributes<HTMLDivElement>, keyof StrictAccordionPanelProps> {}

export interface StrictAccordionPanelProps {
  /** Whether or not the title is in the open state. */
  active?: boolean

  /** A shorthand for Accordion.Content. */
  content?: SemanticShorthandItem<AccordionContentProps>

  /** A panel index. */
  index?: number | string

  /**
   * Called when a panel title is clicked.
   *
   * @param {SyntheticEvent} event - React's original SyntheticEvent.
   * @param {AccordionTitleProps} data - All item props.
   */
  onTitleClick?: (event: React.MouseEvent<HTMLDivElement>, data: AccordionTitleProps) => void

  /** A shorthand for Accordion.Title. */
  title?: SemanticShorthandItem<AccordionTitleProps>
}

/**
 * A panel sub-component for Accordion component.
 */
function AccordionPanel(props: AccordionPanelProps): React.ReactElement {
  const { active, content, index, title } = props

  const handleTitleOverrides = (predefinedProps: Partial<AccordionTitleProps>) => ({
    onClick: (e: React.MouseEvent<HTMLDivElement>, titleProps: AccordionTitleProps): void => {
      _invoke(predefinedProps, 'onClick', e, titleProps)

      _invoke(props, 'onTitleClick', e, titleProps)
    },
  })

  return React.createElement(
    React.Fragment,
    null,
    AccordionTitle.create(title, {
      autoGenerateKey: false,
      defaultProps: {
        active,
        index,
      },
      overrideProps: handleTitleOverrides,
    }),
    AccordionContent.create(content, {
      autoGenerateKey: false,
      defaultProps: {
        active,
      },
    }),
  )
}

AccordionPanel.handledProps = ['active', 'content', 'index', 'onTitleClick', 'title']



AccordionPanel.create = createShorthandFactory(AccordionPanel, null)

export default AccordionPanel
